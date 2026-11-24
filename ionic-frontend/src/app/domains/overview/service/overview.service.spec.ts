import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { environment } from 'src/environments/environment';
import { provideServiceTesting } from 'src/testing/test-providers';
import { SpendingsOverviewYearlyDto } from '../model/spendings-overview-yearly-dto';
import { OverviewService } from './overview.service';

describe('OverviewService.loadUserBalances (DIV-109)', () => {
  let service: OverviewService;
  let http: HttpTestingController;

  const url = `${environment.apiBaseUrl}/api/spendings/7`;
  const gesamt = (users: { userId: number; userName: string; diff: number }[]): SpendingsOverviewYearlyDto => ({
    year: 0,
    availableYears: [2025],
    spendingsPerYear: [],
    spendingsTotalYear: { sumTotalYear: 0, spendingsTotalUser: users.map(user => ({ ...user, sum: 0 })) },
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideServiceTesting() });
    service = TestBed.inject(OverviewService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lädt die Gesamtübersicht der Gruppe und liefert die Nutzerzeilen, ohne die Signals der Übersicht zu ändern', async () => {
    const result = service.loadUserBalances(7);
    const request = http.expectOne(url);
    expect(request.request.method).toBe('GET');
    request.flush(gesamt([{ userId: 1, userName: 'Anna', diff: 100 }, { userId: 2, userName: 'Ben REMOVED', diff: -100 }]));

    expect(await result).toEqual([
      { userId: 1, userName: 'Anna', diff: 100, sum: 0 },
      { userId: 2, userName: 'Ben REMOVED', diff: -100, sum: 0 },
    ]);
    expect(service.spendingsOverviewYearly()).toBeNull();
    expect(service.availableYears()).toEqual([]);
    expect(service.overviewRefresh()).toBe(0);
  });

  it('Gruppe ohne Carts (leere Antwort): leere Liste', async () => {
    const result = service.loadUserBalances(7);
    http.expectOne(url).flush(null);

    expect(await result).toEqual([]);
  });

  it('Fehler: null, das Promise wird nicht zurückgewiesen, Signals unverändert', async () => {
    spyOn(console, 'error');
    const result = service.loadUserBalances(7);
    http.expectOne(url).flush('kaputt', { status: 500, statusText: 'Server Error' });

    expect(await result).toBeNull();
    expect(service.spendingsOverviewYearly()).toBeNull();
  });
});
