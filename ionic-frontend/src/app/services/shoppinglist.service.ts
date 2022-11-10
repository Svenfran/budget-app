import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { AddEditShoppingListDto } from '../models/add-edit-shopping-list-dto';
import { ShoppingListDto } from '../models/shopping-list-dto';

@Injectable({
  providedIn: 'root'
})
export class ShoppinglistService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private shoppingListsWithItemsUrl = `${this.apiBaseUrl}/api/groups/shopping-lists-with-items`;
  private addShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/add`;
  private deleteShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/delete`;
  private updateShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/update`;

  constructor(private http: HttpClient) { }

  getShoppingListsWithItems(groupId: number): Observable<ShoppingListDto[]> {
    return this.http.get<ShoppingListDto[]>(`${this.shoppingListsWithItemsUrl}/${groupId}`);
  }

  addShoppingList(newShoppingList: AddEditShoppingListDto): Observable<AddEditShoppingListDto> {
    return this.http.post<AddEditShoppingListDto>(this.addShoppingListUrl, newShoppingList);
  }

  deleteShoppingList(deleteShoppingList: AddEditShoppingListDto): Observable<void> {
    return this.http.post<void>(this.deleteShoppingListUrl, deleteShoppingList);
  }

  updateShoppingList(updateShoppingList: AddEditShoppingListDto): Observable<AddEditShoppingListDto> {
    return this.http.put<AddEditShoppingListDto>(this.updateShoppingListUrl, updateShoppingList);
  }

}
