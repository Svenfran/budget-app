import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { environment } from 'src/environments/environment';
import { SpendingsOverviewDto } from '../model/spendings-overview-dto';
import { SpendingsOverviewYearlyDto } from '../model/spendings-overview-yearly-dto';
import { Group } from 'src/app/model/group';
import { INIT_VALUES } from 'src/app/constants/default-values';

@Injectable({
  providedIn: 'root'
})
export class OverviewService {

  private apiBaseUrl = environment.apiBaseUrl;
  private spendingsOverviewUrl = `${this.apiBaseUrl}/api/spendings`;

  public spendingsOverview = signal<SpendingsOverviewDto | null>(null);
  public spendingsOverviewYearly = signal<SpendingsOverviewYearlyDto | null>(null);
  public availableYears = signal<number[]>([]);
  public spendingsPerMonth = computed(() =>
    this.spendingsOverview()?.spendingsPerMonth ?? []
  );
  public spendingsTotalYearMonthly = computed(() =>
    this.spendingsOverview()?.spendingsTotalYear ?? { spendingsTotalUser: [], sumTotalYear: 0 }
  );
  public year = computed(() =>
    this.spendingsOverview()?.year ?? new Date().getFullYear()
  );
  public spendingsPerYear = computed(() =>
    this.spendingsOverviewYearly()?.spendingsPerYear ?? []
  );
  public spendingsTotalYearYearly = computed(() =>
    this.spendingsOverviewYearly()?.spendingsTotalYear ?? { spendingsTotalUser: [], sumTotalYear: 0 }
  );

  public overviewRefresh = signal(0);

  constructor(
    private http: HttpClient
  ) { }

  triggerUpdate() {
    this.overviewRefresh.set(this.overviewRefresh() + 1);
  }


  getSpendingsOverview(year: number, group: Group, refresh?: boolean): void {
    if (group.flag?.includes(INIT_VALUES.DEFAULT)) {
      this.spendingsOverview.set(null);
      return;
    };

    this.http
      .get<SpendingsOverviewDto>(`${this.spendingsOverviewUrl}/${group.id}/${year}`)
      .subscribe({
        next: (result) => {
          this.spendingsOverview.set(result || null);
          if (refresh) { this.triggerUpdate() };
        },
        error: (err) => {
          console.log('Error fetching spendings:', err);
          this.spendingsOverview.set(null);
        }
      })
  };

  getSpendingsOverviewYearly(group: Group, refresh?: boolean, onComplete?: () => void): void {
    if (group.flag?.includes(INIT_VALUES.DEFAULT)) {
      this.spendingsOverview.set(null);
      onComplete?.();
      return;
    };

    this.http
      .get<SpendingsOverviewYearlyDto>(`${this.spendingsOverviewUrl}/${group.id}`)
      .subscribe({
        next: (result) => {
          this.spendingsOverviewYearly.set(result || null);
          this.availableYears.set(result.availableYears || []);
          onComplete?.();
          if (refresh) { this.triggerUpdate() };
        },
        error: (err) => {
          console.log('Error fetching yearly spendings:', err);
          this.spendingsOverviewYearly.set(null);
          this.availableYears.set([]);
          onComplete?.();
        }
      })
  }

}
