import { Component, OnDestroy, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController, ModalController } from '@ionic/angular';
import { format } from 'date-fns';
import { Subscription } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { FilterModalPage } from 'src/app/filter-modal/filter-modal.page';
import { Cart } from 'src/app/models/cart';
import { CartFilter } from 'src/app/models/cartFilter';
import { Group } from 'src/app/models/group';
import { GroupSideNav } from 'src/app/models/group-side-nav';
import { CartService } from 'src/app/services/cart.service';
import { GroupService } from 'src/app/services/group.service';
import { StorageService } from 'src/app/services/storage.service';
import { SettlementPaymentPage } from 'src/app/settlement-payment/settlement-payment.page';

@Component({
  selector: 'app-cartlist',
  templateUrl: './cartlist.page.html',
  styleUrls: ['./cartlist.page.scss'],
})
export class CartlistPage implements OnInit, OnDestroy {

  cartlist: Cart[] = [];
  cartlistInit: Cart[] = [];
  isLoading = false;
  userName: string;
  activeGroup: GroupSideNav;
  groupSub: Subscription;
  cartSub: Subscription;
  filterTerm: string;
  filterMode = false;
  sum: number;
  count: number;
  loadedActiveGroup: Promise<boolean>;
  cartFilter: CartFilter = {};
  hidden: boolean = true;
  visibleItems: Set<number> = new Set<number>();

  constructor(
    private cartService: CartService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private modalCtrl: ModalController,
    private groupService: GroupService,
    private storageService: StorageService,
    private authService: AuthService) { }

  ngOnInit() {
    this.getCurrentUser();
    this.groupSub = this.groupService.activeGroup.subscribe(group => {
      if (group) {
        // console.log(group);
        this.loadedActiveGroup = Promise.resolve(true);
        this.activeGroup = group;
        this.getAllCartsByGroupId(group.id);
      } else {
        this.groupService.setActiveGroup(null);
      }
    })
    this.filterTerm = "";
  }
  
  ionViewWillEnter() {
    this.groupSub = this.groupService.activeGroup.subscribe(group => {
      if (group) {
        this.loadedActiveGroup = Promise.resolve(true);
        this.activeGroup = group;
        this.getAllCartsByGroupId(group.id);
        this.filterMode = false;
        this.filterTerm = "";
        this.visibleItems = new Set<number>();
      } else {
        this.groupService.setActiveGroup(null);
      }
    })
  }


  getAllCartsByGroupId(groupId: number) {
    this.isLoading = true;
    if (groupId === null) {
      this.isLoading = false;
      this.cartlist = [];
      this.sum = 0;
      this.count = 0;
    } else {
      this.cartService.cartModified.subscribe(() => {
        this.cartSub = this.cartService.getCartListByGroupId(groupId).subscribe(carts => {
          this.isLoading = false;
          this.cartlist = carts;
          this.cartlistInit = carts;
          this.sum = this.cartlist.reduce((s, c) => s + (+c.amount), 0);
          this.count = this.cartlist.length;
        });
      })
    }
  }

  onDelete(cartId: number, cartTitle:string, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: 'Löschen',
      message: `Möchtest du den Eintrag "${cartTitle}" wirklich löschen?`,
      buttons: [{
        text: 'Nein'
      }, {
        text: 'Ja',
        handler: () => {
          this.loadingCtrl.create({
            message: 'Lösche Einkauf...'
          }).then(loadingEl => {
            loadingEl.present(),
            this.cartService.deleteCart(cartId).subscribe(() => {
              loadingEl.dismiss();
              // this.cartlist = this.cartlist.filter(cart => cart.id !== cartId);
              this.ionViewWillEnter();
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }
  
  onFilterCategory(filterTerm: string, groupId: number) {
    this.sum = 0;
    this.filterTerm = filterTerm;
    if (!this.filterMode) {
      this.filterMode = !this.filterMode;
      this.cartlist = this.cartlist.filter(c => c.categoryDto.name == filterTerm);
      this.sum = this.cartlist.reduce((s, c) => s + (+c.amount), 0);
      this.count = this.cartlist.length;
      this.cartFilter.category = [filterTerm];
    } else {
      this.filterTerm = ""
      this.filterMode = !this.filterMode;
      this.getAllCartsByGroupId(groupId);
    }
  }

  onFilterUserName(filterTerm: string, groupId: number) {
    this.sum = 0;
    this.filterTerm = filterTerm;
    if (!this.filterMode) {
      this.filterMode = !this.filterMode;
      this.cartlist = this.cartlist.filter(c => c.userDto.userName == filterTerm);
      this.sum = this.cartlist.reduce((s, c) => s + (+c.amount), 0);
      this.count = this.cartlist.length;
      this.cartFilter.userName = [filterTerm];
    } else {
      this.filterTerm = ""
      this.filterMode = !this.filterMode;
      this.getAllCartsByGroupId(groupId);
    }
  }

  deleteFilter(groupId: number) {
    this.filterTerm = "";
    this.filterMode = false;
    this.getAllCartsByGroupId(groupId);
    this.cartFilter = {};
  }

  onFilter(cartfilter: CartFilter) {
    if (Object.values(cartfilter).every(field => field == null)) {
      this.cartlist = this.cartlistInit;
      this.filterMode = false;
      this.filterTerm = '';
      this.sum = this.cartlist.reduce((s, c) => s + (+c.amount), 0);
      this.count = this.cartlist.length;
      return;
    }
    this.filterTerm = '';
    this.cartlist = this.cartlistInit;
    this.cartlist = this.cartlist.filter(item => 
      (cartfilter.title ? item.title.toLowerCase().includes(cartfilter.title.toLowerCase()) : true) &&
      (cartfilter.description ? item.description.toLowerCase().includes(cartfilter.description.toLowerCase()) : true) &&
      (cartfilter.category?.length > 0 ? cartfilter.category.includes(item.categoryDto.name) : true) &&
      (cartfilter.userName?.length > 0 ? cartfilter.userName.includes(item.userDto.userName) : true) &&
      (cartfilter.startDate ? new Date(item.datePurchased) >= cartfilter.startDate : true) &&
      (cartfilter.endDate ? new Date(item.datePurchased) <= cartfilter.endDate : true) 
    );

    this.filterMode = true;
    this.sum = this.cartlist.reduce((s, c) => s + (+c.amount), 0);
    this.count = this.cartlist.length;
    // console.log(this.cartlist);
  }

  download() {
    let filename = "Ausgaben_" + this.activeGroup.name.replace(/ /g, "-") + "_" + format(new Date(), 'yyyyMMddHHmmss') + ".xlsx";
    this.cartService.getExcelFile(this.activeGroup.id, filename);
  }

  async filterModal() {
    const modal = this.modalCtrl.create({
      component: FilterModalPage,
      componentProps: { 
        activeGroupId: this.activeGroup.id,
        cartFilter: this.cartFilter
      }
    });

    (await modal).onDidDismiss().then((response) => {
      if (response.data) {
        this.onFilter(response.data);
        this.cartFilter = response.data;
      }
    });

    return (await modal).present();
  }

  async settlementPayment() {
    const modal = this.modalCtrl.create({
      component: SettlementPaymentPage
    });

    (await modal).onDidDismiss().then(() => {
      this.getAllCartsByGroupId(this.activeGroup.id);
    });
    return (await modal).present();
  }

  ngOnDestroy(): void {
    if (this.groupSub) {
      this.groupSub.unsubscribe();
    }
    if (this.cartSub) {
      this.cartSub.unsubscribe();
    }
  }

  // Methode zum Umschalten der Sichtbarkeit
  toggleDescriptionVisibility(index: number) {
    if (this.visibleItems.has(index)) {
      this.visibleItems.delete(index);  // Wenn es sichtbar ist, verstecken
    } else {
      this.visibleItems.add(index);  // Wenn es nicht sichtbar ist, anzeigen
    }
  }

  // Methode zur Überprüfung, ob die Beschreibung sichtbar ist
  isDescriptionVisible(index: number): boolean {
    return this.visibleItems.has(index);
  }

  getCurrentUser() {
    this.authService.userName.subscribe(name => {
      this.userName = name;
    })
    return this.userName;
  }

  onCreateGroup() {
    this.alertCtrl.create({
      header: "Neue Gruppe:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Erstelle Gruppe..."
          }).then(loadingEl => {
            let newGroup = new Group(null, data.groupName, null);
            this.groupService.addGroup(newGroup).subscribe((group) => {
              loadingEl.dismiss();
              this.groupService.setGroupModified(true);
              this.groupService.setActiveGroup(group);
              this.storageService.setActiveGroup(this.activeGroup);
            })
          })
        }
      }],
      inputs: [
        {
          name: "groupName",
          placeholder: "Gruppenname"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }
}