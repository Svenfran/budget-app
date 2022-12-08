import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { Cart } from '../models/cart';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private cartlistUrl = `${this.apiBaseUrl}/api/carts/carts-by-groupid`;
  private getCartByIdUrl = `${this.apiBaseUrl}/api/carts`;
  private addCartUrl = `${this.apiBaseUrl}/api/carts/add`;
  private updateCartUrl = `${this.apiBaseUrl}/api/carts/update`;
  private deleteCartUrl = `${this.apiBaseUrl}/api/carts/delete`;

  private _cartModified = new BehaviorSubject<boolean>(false);

  constructor(private http: HttpClient) { }

  setCartModified(cartMod: boolean) {
    this._cartModified.next(cartMod);
  }

  get cartModified() {
    return this._cartModified.asObservable();
  }

  getCartListByGroupId(groupId: number): Observable<Cart[]> {
    return this.http.get<Cart[]>(`${this.cartlistUrl}/${groupId}`);
  }

  getCartById(cartId: number): Observable<Cart> {
    return this.http.get<Cart>(`${this.getCartByIdUrl}/${cartId}`);
  }

  addCart(cart: Cart): Observable<Cart> {
    return this.http.post<Cart>(this.addCartUrl, cart);
  }

  updateCart(cart: Cart): Observable<Cart> {
    return this.http.put<Cart>(this.updateCartUrl, cart);
  }

  deleteCart(cartId: number): Observable<void> {
    const deleteCartUrl = `${this.deleteCartUrl}/${cartId}`;
    return this.http.delete<void>(deleteCartUrl);
  }

}
