import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { SpendingsOverviewDto } from '../models/spendings-overview-dto';
import { SpendingsOverviewYearlyDto } from '../models/spendings-overview-yearly-dto';

@Injectable({
  providedIn: 'root'
})
export class SpendingsOverviewService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private spendingsOverviewUrl = `${this.apiBaseUrl}/api/spendings`;
  private availableYearsUrl = `${this.apiBaseUrl}/api/spendings/available-years`;
  
  private _spendingsOverviewModified = new BehaviorSubject<boolean>(false);

  constructor(private http: HttpClient) { }

  setSpendingsModified(spendingsMod: boolean) {
    this._spendingsOverviewModified.next(spendingsMod);
  }

  get spendingsOverviewModified() {
    return this._spendingsOverviewModified.asObservable();
  }

  getSpendingsOverview(year: number, groupId: number): Observable<SpendingsOverviewDto> {
    return this.http.get<SpendingsOverviewDto>(`${this.spendingsOverviewUrl}/${groupId}/${year}`);
  }

  getSpendingsOverviewYearly(groupId: number): Observable<SpendingsOverviewYearlyDto> {
    return this.http.get<SpendingsOverviewYearlyDto>(`${this.spendingsOverviewUrl}/${groupId}`);
  }

  getAvailableYears(groupId: number): Observable<number[]> {
    return this.http.get<number[]>(`${this.availableYearsUrl}/${groupId}`);
  }
  

}
