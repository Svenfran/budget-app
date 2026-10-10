import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AlertService } from 'src/app/service/alert.service';
import { environment } from 'src/environments/environment';
import { aCart, aGroup } from 'src/testing/test-data';
import { alertServiceSpy, provideServiceTesting } from 'src/testing/test-providers';
import { CartService } from './cart.service';

describe('CartService', () => {
  let service: CartService;
  let http: HttpTestingController;
  let alerts: jasmine.SpyObj<AlertService>;
  const api = `${environment.apiBaseUrl}/api/carts`;

  beforeEach(() => {
    alerts = alertServiceSpy();
    TestBed.configureTestingModule({
      providers: [...provideServiceTesting(), { provide: AlertService, useValue: alerts }],
    });
    service = TestBed.inject(CartService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lädt die Einkäufe der Gruppe und berechnet Summe und Anzahl', () => {
    service.getCartListByGroupId(aGroup({ id: 7 }));
    http.expectOne(`${api}/carts-by-groupid/7`).flush([aCart({ amount: 10 }), aCart({ amount: 32.5 })]);

    expect(service.count()).toBe(2);
    expect(service.sum()).toBe(42.5);
  });

  it('fügt einen neuen Einkauf nach dem Speichern sortiert ein und meldet die Änderung', () => {
    const older = aCart({ id: 1, datePurchased: new Date('2025-01-01') });
    service.cartList.set([older]);
    const updatesBefore = service.cartUpdated();

    service.addCart(aCart({ id: null, title: 'Pizza', datePurchased: new Date('2025-02-01') }));
    const req = http.expectOne(`${api}/add`);
    expect(req.request.method).toBe('POST');
    req.flush(aCart({ id: 2, title: 'Pizza', datePurchased: new Date('2025-02-01') }));

    expect(service.cartList().map(c => c.title)).toEqual(['Pizza', older.title]);
    expect(service.cartUpdated()).toBe(updatesBefore + 1);
  });

  it('lässt die Liste bei einem Fehler unverändert und zeigt den Mitgliedschafts-Hinweis', () => {
    const existing = [aCart({ id: 1 })];
    service.cartList.set(existing);

    service.addCart(aCart({ id: null }));
    http.expectOne(`${api}/add`).flush('Date purchased is not within membership period',
      { status: 400, statusText: 'Bad Request' });

    expect(service.cartList()).toEqual(existing);
    expect(alerts.showErrorAlert).toHaveBeenCalledWith(
      'alerts.cart.invalid_membership.header', 'alerts.cart.invalid_membership.message_new_edit');
  });
});
