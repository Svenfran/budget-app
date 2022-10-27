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

  constructor(
    private cartService: CartService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private groupService: GroupService) { }

  ngOnInit() {
    this.getCurrentUser();
    this.groupSub = this.groupService.activeGroup.subscribe(group => {
      this.activeGroupName = group.name;
      this.getAllCartsByGroupId(group.id)
    })
  }
  
  ionViewWillEnter() {
    this.groupService.activeGroup.subscribe(group => {
      this.activeGroupName = group.name;
      this.getAllCartsByGroupId(group.id)
    })
  }

  getAllCartsByGroupId(groupId: number) {
    this.isLoading = true;
    this.cartSub = this.cartService.getCartListByGroupId(groupId).subscribe(carts => {
      this.cartlist = carts;
      this.isLoading = false;
    });
  }

  onDelete(cartId: number, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: 'Löschen',
      message: 'Möchtest du den Eintrag wirklich löschen?',
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
              this.cartlist = this.cartlist.filter(cart => cart.id !== cartId);
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  getCurrentUser() {
    this.userName = "sven";
  }

  ngOnDestroy(): void {
    if (this.groupSub) {
      this.groupSub.unsubscribe();
    }
    if (this.cartSub) {
      this.cartSub.unsubscribe();
    }
  }
}
