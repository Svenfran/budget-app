import { Component, OnDestroy, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController } from '@ionic/angular';
import { Subscription } from 'rxjs';
import { Cart } from 'src/app/models/cart';
import { GroupSideNav } from 'src/app/models/group-side-nav';
import { CartService } from 'src/app/services/cart.service';
import { GroupService } from 'src/app/services/group.service';

@Component({
  selector: 'app-cartlist',
  templateUrl: './cartlist.page.html',
  styleUrls: ['./cartlist.page.scss'],
})
export class CartlistPage implements OnInit, OnDestroy {

  cartlist: Cart[] = [];
  isLoading = false;
  userName: string;
  activeGroup: GroupSideNav;
  activeGroupName: string;
  groupSub: Subscription;
  cartSub: Subscription;
  filterTerm: string;
  filterMode = false;
  sum: number;
  count: number;

  constructor(
    private cartService: CartService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private groupService: GroupService) { }

  ngOnInit() {
    this.getCurrentUser();
    this.groupSub = this.groupService.activeGroup.subscribe(group => {
      this.activeGroupName = group.name;
      this.activeGroup = group;
      this.getAllCartsByGroupId(group.id);
    })
    this.filterTerm = "";
  }
  
  ionViewWillEnter() {
    this.groupSub = this.groupService.activeGroup.subscribe(group => {
      this.activeGroupName = group.name;
      this.activeGroup = group;
      this.getAllCartsByGroupId(group.id);
      this.filterMode = false;
      this.filterTerm = "";
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
      this.cartSub = this.cartService.getCartListByGroupId(groupId).subscribe(carts => {
        this.isLoading = false;
        this.cartlist = carts;
        this.sum = this.cartlist.reduce((s, c) => s + (+c.amount), 0);
        this.count = this.cartlist.length;
      });
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
    } else {
      this.filterTerm = ""
      this.filterMode = !this.filterMode;
      this.getAllCartsByGroupId(groupId);
    }
  }

  ngOnDestroy(): void {
    if (this.groupSub) {
      this.groupSub.unsubscribe();
    }
    if (this.cartSub) {
      this.cartSub.unsubscribe();
    }
  }

  getCurrentUser() {
    this.groupService.currentUser.subscribe(user => {
      this.userName = user.userName;
    })
  }
}
