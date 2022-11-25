import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { CategoryDto } from '../models/category';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private getCategoriesUrl = `${this.apiBaseUrl}/api/groups/categories`;
  private addCategoryUrl = `${this.apiBaseUrl}/api/groups/category/add`;
  private updateCategoryUrl = `${this.apiBaseUrl}/api/groups/category/update`;
  private deleteCategoryUrl = `${this.apiBaseUrl}/api/groups/category/delete`;


  constructor(private http: HttpClient) { }


  getCategoriesByGroup(groupId: number): Observable<CategoryDto[]> {
    return this.http.get<CategoryDto[]>(`${this.getCategoriesUrl}/${groupId}`);
  }

  addCategory(category: CategoryDto): Observable<CategoryDto> {
    return this.http.post<CategoryDto>(this.addCategoryUrl, category);
  }

  updateCategory(category: CategoryDto): Observable<CategoryDto> {
    return this.http.put<CategoryDto>(this.updateCategoryUrl, category);
  }
  
  deleteCategory(category: CategoryDto): Observable<CategoryDto> {
    return this.http.post<CategoryDto>(this.deleteCategoryUrl, category);
  }

}
