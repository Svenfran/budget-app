import { Component, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController } from '@ionic/angular';
import { Cart } from 'src/app/models/cart';
import { CartService } from 'src/app/services/cart.service';

@Component({
  selector: 'app-cartlist',
  templateUrl: './cartlist.page.html',
  styleUrls: ['./cartlist.page.scss'],
})
export class CartlistPage implements OnInit {

  cartlist: Cart[] = [];
  isLoading = false;

  constructor(
    private cartService: CartService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController) { }

  ngOnInit() {
    this.getAllCarts();
  }

  ionViewWillEnter() {
    this.getAllCarts();
  }

  getAllCarts() {
    this.isLoading = true;
    this.cartService.getCartList().subscribe(carts => {
      this.cartlist = carts;
      this.isLoading = false;
    })
  }

  onDelete(cartId: number, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: 'Löschen',
      message: 'Möchtest du den Eintrag löschen?',
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
              this.ionViewWillEnter();
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

}
