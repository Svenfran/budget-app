import { Component, OnInit } from '@angular/core';
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

  constructor(private cartService: CartService) { }

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

}
