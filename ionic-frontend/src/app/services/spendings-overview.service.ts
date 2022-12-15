import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { SpendingsOverviewDto } from '../models/spendings-overview-dto';

@Injectable({
  providedIn: 'root'
})
export class SpendingsOverviewService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private spendingsOverviewUrl = `${this.apiBaseUrl}/api/spendings`;
  
  constructor(private http: HttpClient) { }


  getSpendingsOverview(year: number, groupId: number): Observable<SpendingsOverviewDto> {
    return this.http.get<SpendingsOverviewDto>(`${this.spendingsOverviewUrl}/${groupId}/${year}`);
  }

}
