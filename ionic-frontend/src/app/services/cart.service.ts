import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { Cart } from '../models/cart';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private cartlistUrl = `${this.apiBaseUrl}/api/cartsbygroupid`;
  private getCartByIdUrl = `${this.apiBaseUrl}/api/carts`;
  private addCartUrl = `${this.apiBaseUrl}/api/carts/add`;
  private updateCartUrl = `${this.apiBaseUrl}/api/carts/update`;
  private deleteCartUrl = `${this.apiBaseUrl}/api/carts/delete`;


  constructor(private http: HttpClient) { }

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
