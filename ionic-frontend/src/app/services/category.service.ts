import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { CategoryDto } from '../models/category';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {

  private apiBaseUrl = environment.apiBaseUrl;
  private getCategoriesUrl = `${this.apiBaseUrl}/api/categories`;

  constructor(private http: HttpClient) { }


  getCategories(): Observable<CategoryDto[]> {
    return this.http.get<CategoryDto[]>(this.getCategoriesUrl);
  }


}
