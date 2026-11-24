import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { StorageService } from 'src/app/service/storage.service';
import { environment } from 'src/environments/environment';
import { GroupService } from 'src/app/service/group.service';
import { aGroup, aLoggedInUser, REFERENCE_DATE } from 'src/testing/test-data';
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
  const defaultFilter: StatisticsFilter = { groupId: null, period: 'YEAR', year: 2025, month: 6, currency: null };

  const statisticsDto = (overrides: Partial<StatisticsDto> = {}): StatisticsDto => ({
    groupId: null, currencyCode: 'EUR', availableCurrencies: ['EUR'],
    period: 'YEAR', year: 2025, month: null, totalSpent: 100, paidByUser: 60, paidCartCount: 3, currentBalance: 10,
    availableYears: [2025], timeline: [], categories: [],
    groupView: { cartCount: 0, averagePerCart: 0, memberCount: 0, timeline: [], categories: [] }, ...overrides,
  });
  const isStatisticsRequest = (request: HttpRequest<unknown>) => request.url === api;
  /** Gruppen der Seitenleiste wie vom StatisticsPage-Effect gemeldet (ID und Währung, DIV-77) */
  const groups = (...ids: number[]) => ids.map(id => ({ id, currencyCode: 'EUR' }));
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

    it('sendet je Zeitraum nur die benötigten Parameter, ohne groupId, aber mit Währung bei „Alle Gruppen“', () => {
      service.load();
      const year = http.expectOne(isStatisticsRequest);
      service.setFilter({ groupId: 7, period: 'MONTH', year: 2024, month: 3, currency: null });
      const month = http.expectOne(isStatisticsRequest);
      service.setFilter({ groupId: null, period: 'ALL', year: 2024, month: 3, currency: null });
      const all = http.expectOne(isStatisticsRequest);

      expect(year.request.method).toBe('GET');
      expect(params(year)).toEqual({ period: 'YEAR', currency: 'EUR', year: '2025' });
      expect(params(month)).toEqual({ period: 'MONTH', groupId: '7', year: '2024', month: '3' });
      expect(params(all)).toEqual({ period: 'ALL', currency: 'EUR' });
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
      service.setFilter({ groupId: 4, period: 'ALL', year: 2023, month: 2, currency: null });
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
      const filter: StatisticsFilter = { groupId: 4, period: 'MONTH', year: 2024, month: 2, currency: null };
      service.setFilter(filter);
      http.expectOne(isStatisticsRequest).flush(statisticsDto());

      user$.next(user$.value);
      TestBed.flushEffects();

      expect(service.filter()).toEqual(filter);
    });

    it('belegt den Filter von der Übersicht vor, ohne zu laden; die Seite lädt danach mit diesem Filter (DIV-67)', () => {
      const filter: StatisticsFilter = { groupId: 4, period: 'YEAR', year: 2024, month: 6, currency: null };
      service.presetFilter(filter);

      http.expectNone(isStatisticsRequest);
      expect(service.filter()).toEqual(filter);

      service.load();
      expect(params(http.expectOne(isStatisticsRequest))).toEqual({ period: 'YEAR', groupId: '4', year: '2024' });
    });

    it('behält einen vorbelegten Filter, auch wenn die Sitzung vorher gewechselt hat (DIV-67)', () => {
      const filter: StatisticsFilter = { groupId: 4, period: 'ALL', year: 2025, month: 6, currency: null };
      user$.next(null);
      user$.next(aLoggedInUser());

      service.presetFilter(filter);
      TestBed.flushEffects();

      expect(service.filter()).toEqual(filter);
    });
  });

  // DIV-77: Beträge verschiedener Währungen nie addieren – Währung nur bei „Alle Gruppen“
  describe('Währungsfilter', () => {
    let groupService: GroupService;
    beforeEach(() => groupService = TestBed.inject(GroupService));

    it('ohne gewählte Währung gilt die der aktiven Gruppe; mit Gruppe wird keine Währung gesendet', () => {
      groupService.activeGroup.set(aGroup({ id: 2, currencyCode: 'CHF' }));

      service.load();
      const alle = http.expectOne(isStatisticsRequest);
      service.setFilter({ ...defaultFilter, groupId: 2 });
      const gruppe = http.expectOne(isStatisticsRequest);

      expect(params(alle)).toEqual({ period: 'YEAR', currency: 'CHF', year: '2025' });
      expect(params(gruppe)).toEqual({ period: 'YEAR', groupId: '2', year: '2025' });
      [alle, gruppe].forEach(request => request.flush(statisticsDto()));
    });

    it('sendet die gewählte Währung und behält sie beim Wechsel von Zeitraum und Perspektive', () => {
      groupService.activeGroup.set(aGroup({ id: 2, currencyCode: 'EUR' }));

      service.setFilter({ ...defaultFilter, currency: 'USD' });
      const usd = http.expectOne(isStatisticsRequest);
      service.setFilter({ ...service.filter(), period: 'ALL' });
      const alle = http.expectOne(isStatisticsRequest);

      expect(params(usd)).toEqual({ period: 'YEAR', currency: 'USD', year: '2025' });
      expect(params(alle)).toEqual({ period: 'ALL', currency: 'USD' });
      [usd, alle].forEach(request => request.flush(statisticsDto({ currencyCode: 'USD' })));
    });

    it('lädt neu, wenn eine Gruppe die Währung wechselt (Menge aus ID und Währung)', () => {
      service.groupsChanged([{ id: 1, currencyCode: 'EUR' }, { id: 2, currencyCode: 'EUR' }]);
      service.groupsChanged([{ id: 2, currencyCode: 'EUR' }, { id: 1, currencyCode: 'EUR' }]);
      http.expectNone(isStatisticsRequest);

      service.groupsChanged([{ id: 1, currencyCode: 'EUR' }, { id: 2, currencyCode: 'USD' }]);

      http.expectOne(isStatisticsRequest).flush(statisticsDto());
    });

    it('fällt auf die Währung der aktiven Gruppe zurück, wenn keine Gruppe die gewählte Währung mehr hat', () => {
      groupService.activeGroup.set(aGroup({ id: 1, currencyCode: 'EUR' }));
      service.groupsChanged([{ id: 1, currencyCode: 'EUR' }, { id: 2, currencyCode: 'USD' }]);
      service.setFilter({ ...defaultFilter, currency: 'USD' });
      http.expectOne(isStatisticsRequest).flush(statisticsDto({ currencyCode: 'USD' }));

      service.groupsChanged([{ id: 1, currencyCode: 'EUR' }, { id: 2, currencyCode: 'GBP' }]);

      expect(service.filter().currency).toBeNull();
      expect(params(http.expectOne(isStatisticsRequest))).toEqual({ period: 'YEAR', currency: 'EUR', year: '2025' });
    });

    it('behält die gewählte Währung, solange noch eine Gruppe sie hat', () => {
      service.groupsChanged([{ id: 1, currencyCode: 'USD' }, { id: 2, currencyCode: 'USD' }]);
      service.setFilter({ ...defaultFilter, currency: 'USD' });
      http.expectOne(isStatisticsRequest).flush(statisticsDto());

      service.groupsChanged([{ id: 1, currencyCode: 'USD' }, { id: 2, currencyCode: 'EUR' }]);

      expect(service.filter().currency).toBe('USD');
      expect(params(http.expectOne(isStatisticsRequest))).toEqual({ period: 'YEAR', currency: 'USD', year: '2025' });
    });

    it('sendet auch eine dieser App-Version unbekannte Währung (später eingeführt) unverändert', () => {
      service.groupsChanged([{ id: 1, currencyCode: 'EUR' }, { id: 2, currencyCode: 'JPY' }]);
      service.setFilter({ ...defaultFilter, currency: 'JPY' });

      expect(params(http.expectOne(isStatisticsRequest))).toEqual({ period: 'YEAR', currency: 'JPY', year: '2025' });
      expect(service.filter().currency).toBe('JPY');
    });

    it('Sitzungswechsel setzt die gewählte Währung zurück', () => {
      service.setFilter({ ...defaultFilter, currency: 'USD' });
      http.expectOne(isStatisticsRequest).flush(statisticsDto());

      user$.next(null);
      user$.next(aLoggedInUser());
      TestBed.flushEffects();

      expect(service.filter().currency).toBeNull();
    });
  });

  describe('Gruppenänderungen (Codex F-003)', () => {
    it('merkt sich beim ersten Aufruf nur den Stand, ohne zu laden', () => {
      service.groupsChanged(groups(1, 2));

      http.expectNone(isStatisticsRequest);
    });

    it('lädt bei unveränderter ID-Menge nicht neu (andere Reihenfolge, Umbenennung)', () => {
      service.groupsChanged(groups(1, 2));
      service.groupsChanged(groups(2, 1));

      http.expectNone(isStatisticsRequest);
    });

    it('fällt auf „Alle Gruppen“ zurück und lädt neu, wenn die gewählte Gruppe wegfällt', () => {
      service.groupsChanged(groups(1, 2));
      service.setFilter({ ...defaultFilter, groupId: 2 });
      http.expectOne(isStatisticsRequest).flush(statisticsDto({ groupId: 2 }));

      service.groupsChanged(groups(1));

      expect(service.filter().groupId).toBeNull();
      expect(service.statistics()).toBeNull();
      const request = http.expectOne(isStatisticsRequest);
      expect(params(request)).toEqual({ period: 'YEAR', currency: 'EUR', year: '2025' });
      request.flush(statisticsDto());
    });

    it('lädt auch bei „Alle Gruppen“ neu und verwirft eine vorher gestartete Antwort', () => {
      service.groupsChanged(groups(1, 2));
      service.load();
      const before = http.expectOne(isStatisticsRequest);

      service.groupsChanged(groups(1));
      const after = http.expectOne(isStatisticsRequest);
      before.flush(statisticsDto({ totalSpent: 999 }));

      expect(service.statistics()).toBeNull();
      after.flush(statisticsDto({ totalSpent: 100 }));
      expect(service.statistics()?.totalSpent).toBe(100);
    });

    it('lädt nicht, wenn der Logout die Seitenleiste leert, und verwirft die alten Daten (DIV-59)', () => {
      const groupService = TestBed.inject(GroupService);
      const sideNavIds = () => groupService.groupsSideNav().map(group => group.id);
      const sideNavGroups = () => groupService.groupsSideNav().map(group => ({ id: group.id, currencyCode: group.currencyCode }));
      groupService.groupsSideNav.set([aGroup({ id: 1 }), aGroup({ id: 2 })]);
      service.groupsChanged(sideNavGroups());
      service.load();
      http.expectOne(isStatisticsRequest).flush(statisticsDto());

      user$.next(null);
      // so ruft der StatisticsPage-Effect groupsChanged nach dem Logout auf
      service.groupsChanged(sideNavGroups());

      expect(sideNavIds()).withContext('Seitenleiste nach Logout').toEqual([]);
      http.expectNone(isStatisticsRequest);
      expect(service.statistics()).toBeNull();
      expect(service.isLoading()).toBeFalse();
      expect(service.hasError()).toBeFalse();
    });
  });
});
