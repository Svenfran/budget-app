import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { AddEditShoppingItemDto } from '../models/add-edit-shopping-item-dto';

@Injectable({
  providedIn: 'root'
})
export class ShoppingitemService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private addItemToShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/add-item`;
  private updateItemToShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/update-item`;
  private deleteItemToShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/delete-item`;
 
  constructor(private http: HttpClient) { }

  
  addItemToShoppingList(newShoppingItem: AddEditShoppingItemDto): Observable<AddEditShoppingItemDto> {
    return this.http.post<AddEditShoppingItemDto>(this.addItemToShoppingListUrl, newShoppingItem);
  }
 
  updateItemOfShoppingList(updateShoppingItem: AddEditShoppingItemDto): Observable<AddEditShoppingItemDto> {
    return this.http.put<AddEditShoppingItemDto>(this.updateItemToShoppingListUrl, updateShoppingItem);
  }
 
  deleteItemFromShoppingList(deleteShoppingItem: AddEditShoppingItemDto): Observable<void> {
    return this.http.post<void>(this.deleteItemToShoppingListUrl, deleteShoppingItem);
  }
}
