import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { Cart } from '../models/cart';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private apiBaseUrl = environment.apiBaseUrl;
  private cartlistUrl = `${this.apiBaseUrl}/api/carts`;


  constructor(private http: HttpClient) { }

  getCartList(): Observable<Cart[]> {
    return this.http.get<Cart[]>(this.cartlistUrl);
  }
}
