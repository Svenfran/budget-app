import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { StorageService } from 'src/app/service/storage.service';
import { environment } from 'src/environments/environment';
import { aLoggedInUser, REFERENCE_DATE } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { StatisticsDto } from '../model/statistics-dto';
import { StatisticsFilter } from '../model/statistics-filter';
import { StatisticsService } from './statistics.service';

describe('StatisticsService', () => {
  let service: StatisticsService;
  let http: HttpTestingController;
  /** steuerbarer Anmeldestatus (Logout/Login), den GroupService beobachtet */
  let user$: BehaviorSubject<User | null>;
  const api = `${environment.apiBaseUrl}/api/statistics`;
  const serverError = { status: 500, statusText: 'Server Error' };
  /** Standard laut AC2 bei REFERENCE_DATE (15.06.2025) */
  const defaultFilter: StatisticsFilter = { groupId: null, period: 'YEAR', year: 2025, month: 6 };

  const statisticsDto = (overrides: Partial<StatisticsDto> = {}): StatisticsDto => ({
    groupId: null, period: 'YEAR', year: 2025, month: null, totalSpent: 100, paidByUser: 60, paidCartCount: 3, currentBalance: 10,
    availableYears: [2025], timeline: [], categories: [], ...overrides,
  });
  const isStatisticsRequest = (request: HttpRequest<unknown>) => request.url === api;
  const params = (request: TestRequest) => request.request.params.keys()
    .reduce((result, key) => ({ ...result, [key]: request.request.params.get(key) }), {} as Record<string, string | null>);

  beforeEach(() => {
    jasmine.clock().mockDate(REFERENCE_DATE);
    user$ = new BehaviorSubject<User | null>(aLoggedInUser());
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(user$);
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AlertService, useValue: alertServiceSpy() },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(StatisticsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    jasmine.clock().uninstall();
  });

  describe('Filter und Request', () => {
    it('startet mit „Alle Gruppen“ und dem aktuellen Jahr, ohne von selbst zu laden (AC2)', () => {
      expect(service.filter()).toEqual(defaultFilter);
      expect(service.statistics()).toBeNull();
      expect(service.isLoading()).toBeFalse();
      expect(service.hasError()).toBeFalse();
      http.expectNone(isStatisticsRequest);
    });

    it('sendet je Zeitraum nur die benötigten Parameter, ohne groupId bei „Alle Gruppen“', () => {
      service.load();
      const year = http.expectOne(isStatisticsRequest);
      service.setFilter({ groupId: 7, period: 'MONTH', year: 2024, month: 3 });
      const month = http.expectOne(isStatisticsRequest);
      service.setFilter({ groupId: null, period: 'ALL', year: 2024, month: 3 });
      const all = http.expectOne(isStatisticsRequest);

      expect(year.request.method).toBe('GET');
      expect(params(year)).toEqual({ period: 'YEAR', year: '2025' });
      expect(params(month)).toEqual({ period: 'MONTH', groupId: '7', year: '2024', month: '3' });
      expect(params(all)).toEqual({ period: 'ALL' });
      [year, month, all].forEach(request => request.flush(statisticsDto()));
    });
  });

  describe('Laden', () => {
    it('zeigt während des Ladens keine Daten und übernimmt danach die Antwort', () => {
      const loaded = statisticsDto({ paidByUser: 42.5 });
      const onComplete = jasmine.createSpy('onComplete');

      service.load(onComplete);

      expect(service.isLoading()).toBeTrue();
      expect(service.statistics()).toBeNull();
      http.expectOne(isStatisticsRequest).flush(loaded);

      expect(service.statistics()).toEqual(loaded);
      expect(service.isLoading()).toBeFalse();
      expect(service.hasError()).toBeFalse();
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('verwirft beim Filterwechsel die alten Daten sofort (FA8)', () => {
      service.load();
      http.expectOne(isStatisticsRequest).flush(statisticsDto());

      service.setFilter({ ...defaultFilter, groupId: 3 });

      expect(service.statistics()).toBeNull();
      expect(service.isLoading()).toBeTrue();
      http.expectOne(isStatisticsRequest).flush(statisticsDto({ groupId: 3 }));
    });

    it('zeigt nach einem Fehler keine Daten, sondern den Fehlerzustand; erneutes Laden setzt ihn zurück', () => {
      const onComplete = jasmine.createSpy('onComplete');
      service.load();
      http.expectOne(isStatisticsRequest).flush(statisticsDto());

      service.load(onComplete);
      http.expectOne(isStatisticsRequest).flush('boom', serverError);

      expect(service.statistics()).toBeNull();
      expect(service.hasError()).toBeTrue();
      expect(service.isLoading()).toBeFalse();
      expect(onComplete).toHaveBeenCalledTimes(1);

      service.load();
      expect(service.hasError()).toBeFalse();
      http.expectOne(isStatisticsRequest).flush(statisticsDto());
      expect(service.statistics()).not.toBeNull();
    });
  });

  describe('Veraltete Antworten (AC14)', () => {
    it('verwirft die Antwort eines älteren Requests, die nach der neueren eintrifft', () => {
      const onComplete = jasmine.createSpy('onComplete');
      service.load(onComplete);
      const older = http.expectOne(isStatisticsRequest);
      service.setFilter({ ...defaultFilter, groupId: 2 });
      const newer = http.expectOne(isStatisticsRequest);

      newer.flush(statisticsDto({ groupId: 2 }));
      older.flush(statisticsDto({ groupId: null }));

      expect(service.statistics()?.groupId).toBe(2);
      expect(service.isLoading()).toBeFalse();
      expect(onComplete).withContext('auch eine verworfene Antwort beendet z. B. den Refresher').toHaveBeenCalledTimes(1);
    });

    it('eine verspätete Fehlerantwort eines älteren Requests ändert weder Daten noch Lade- und Fehlerzustand', () => {
      service.setFilter({ ...defaultFilter, groupId: 1 });
      const older = http.expectOne(isStatisticsRequest);
      service.setFilter({ ...defaultFilter, groupId: 2 });
      const newer = http.expectOne(isStatisticsRequest);

      older.flush('boom', serverError);
      expect(service.isLoading()).toBeTrue();
      expect(service.hasError()).toBeFalse();

      newer.flush(statisticsDto({ groupId: 2 }));
      expect(service.statistics()?.groupId).toBe(2);
      expect(service.hasError()).toBeFalse();
      expect(service.isLoading()).toBeFalse();
    });

    it('verwirft nach einem Sitzungswechsel die laufende Antwort und setzt den Filter auf den Standard zurück (K7)', () => {
      service.setFilter({ groupId: 4, period: 'ALL', year: 2023, month: 2 });
      const request = http.expectOne(isStatisticsRequest);

      user$.next(null);
      user$.next(aLoggedInUser());
      TestBed.flushEffects();
      request.flush(statisticsDto({ groupId: 4 }));

      expect(service.statistics()).toBeNull();
      expect(service.isLoading()).toBeFalse();
      expect(service.filter()).toEqual(defaultFilter);
    });

    it('behält den Filter innerhalb derselben Sitzung (K7)', () => {
      const filter: StatisticsFilter = { groupId: 4, period: 'MONTH', year: 2024, month: 2 };
      service.setFilter(filter);
      http.expectOne(isStatisticsRequest).flush(statisticsDto());

      user$.next(user$.value);
      TestBed.flushEffects();

      expect(service.filter()).toEqual(filter);
    });
  });

  describe('Gruppenänderungen (Codex F-003)', () => {
    it('merkt sich beim ersten Aufruf nur den Stand, ohne zu laden', () => {
      service.groupsChanged([1, 2]);

      http.expectNone(isStatisticsRequest);
    });

    it('lädt bei unveränderter ID-Menge nicht neu (andere Reihenfolge, Umbenennung)', () => {
      service.groupsChanged([1, 2]);
      service.groupsChanged([2, 1]);

      http.expectNone(isStatisticsRequest);
    });

    it('fällt auf „Alle Gruppen“ zurück und lädt neu, wenn die gewählte Gruppe wegfällt', () => {
      service.groupsChanged([1, 2]);
      service.setFilter({ ...defaultFilter, groupId: 2 });
      http.expectOne(isStatisticsRequest).flush(statisticsDto({ groupId: 2 }));

      service.groupsChanged([1]);

      expect(service.filter().groupId).toBeNull();
      expect(service.statistics()).toBeNull();
      const request = http.expectOne(isStatisticsRequest);
      expect(params(request)).toEqual({ period: 'YEAR', year: '2025' });
      request.flush(statisticsDto());
    });

    it('lädt auch bei „Alle Gruppen“ neu und verwirft eine vorher gestartete Antwort', () => {
      service.groupsChanged([1, 2]);
      service.load();
      const before = http.expectOne(isStatisticsRequest);

      service.groupsChanged([1]);
      const after = http.expectOne(isStatisticsRequest);
      before.flush(statisticsDto({ totalSpent: 999 }));

      expect(service.statistics()).toBeNull();
      after.flush(statisticsDto({ totalSpent: 100 }));
      expect(service.statistics()?.totalSpent).toBe(100);
    });
  });
});
