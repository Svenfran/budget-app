import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Init } from 'src/app/constants/default-values';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { AlertService } from 'src/app/service/alert.service';
import { SettlementPaymentDto } from 'src/app/settlement-payment/model/settlement-payment-dto';
import { environment } from 'src/environments/environment';
import { aCart, aGroup, aUser } from 'src/testing/test-data';
import { alertServiceSpy, provideServiceTesting } from 'src/testing/test-providers';
import { Cart } from '../model/cart';
import { CartService } from './cart.service';

describe('CartService', () => {
  let service: CartService;
  let http: HttpTestingController;
  let alerts: jasmine.SpyObj<AlertService>;
  const api = `${environment.apiBaseUrl}/api/carts`;
  const notWithinMembership = 'Date purchased is not within membership period';
  const ids = (carts: Cart[]) => carts.map(c => c.id);

  beforeEach(() => {
    alerts = alertServiceSpy();
    TestBed.configureTestingModule({
      providers: [...provideServiceTesting(), { provide: AlertService, useValue: alerts }],
    });
    service = TestBed.inject(CartService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('Laden', () => {
    it('lädt die Einkäufe der Gruppe in beide Listen und berechnet Summe und Anzahl', () => {
      const loaded = [aCart({ amount: 10 }), aCart({ amount: 32.5 })];
      const onComplete = jasmine.createSpy('onComplete');
      const updatesBefore = service.cartUpdated();

      service.getCartListByGroupId(aGroup({ id: 7 }), false, onComplete);
      http.expectOne(`${api}/carts-by-groupid/7`).flush(loaded);

      expect(service.cartList()).toEqual(loaded);
      expect(service.initCartList()).toEqual(loaded);
      expect(service.count()).toBe(2);
      expect(service.sum()).toBe(42.5);
      expect(onComplete).toHaveBeenCalledTimes(1);
      expect(service.cartUpdated()).toBe(updatesBefore);
    });

    it('meldet beim Neuladen mit refresh eine Änderung', () => {
      const updatesBefore = service.cartUpdated();

      service.getCartListByGroupId(aGroup({ id: 7 }), true);
      http.expectOne(`${api}/carts-by-groupid/7`).flush([aCart()]);

      expect(service.cartUpdated()).toBe(updatesBefore + 1);
    });

    it('leert bei einem Ladefehler beide Listen', () => {
      service.cartList.set([aCart()]);
      service.initCartList.set([aCart(), aCart()]);
      const onComplete = jasmine.createSpy('onComplete');

      service.getCartListByGroupId(aGroup({ id: 7 }), false, onComplete);
      http.expectOne(`${api}/carts-by-groupid/7`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.cartList()).toEqual([]);
      expect(service.initCartList()).toEqual([]);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('leert für die Platzhalter-Gruppe beide Listen ohne Request', () => {
      service.cartList.set([aCart()]);
      service.initCartList.set([aCart(), aCart()]);
      const onComplete = jasmine.createSpy('onComplete');

      service.getCartListByGroupId(Init.DEFAULT_GROUP, false, onComplete);

      http.expectNone(() => true);
      expect(service.cartList()).toEqual([]);
      expect(service.initCartList()).toEqual([]);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });
  });

  describe('addCart', () => {
    it('fügt einen neuen Einkauf nach dem Speichern sortiert in beide Listen ein und meldet die Änderung', () => {
      const older = aCart({ id: 1, datePurchased: new Date('2025-01-01') });
      const filteredOut = aCart({ id: 3, datePurchased: new Date('2025-03-01') });
      service.cartList.set([older]);
      service.initCartList.set([older, filteredOut]);
      const updatesBefore = service.cartUpdated();
      const newCart = aCart({ id: null, title: 'Pizza', datePurchased: new Date('2025-02-01') });

      service.addCart(newCart);
      const req = http.expectOne(`${api}/add`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newCart);
      req.flush({ ...newCart, id: 2 });

      expect(ids(service.cartList())).toEqual([2, 1]);
      expect(ids(service.initCartList())).toEqual([3, 2, 1]);
      expect(service.cartUpdated()).toBe(updatesBefore + 1);
    });

    it('lässt beide Listen bei einem Fehler unverändert und zeigt den Mitgliedschafts-Hinweis', () => {
      const cartList = [aCart({ id: 1 })];
      const initCartList = [aCart({ id: 1 }), aCart({ id: 3 })];
      service.cartList.set(cartList);
      service.initCartList.set(initCartList);
      const updatesBefore = service.cartUpdated();

      service.addCart(aCart({ id: null }));
      http.expectOne(`${api}/add`).flush(notWithinMembership, { status: 400, statusText: 'Bad Request' });

      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(initCartList);
      expect(service.cartUpdated()).toBe(updatesBefore);
      expect(alerts.showErrorAlert).toHaveBeenCalledWith(
        'alerts.cart.invalid_membership.header', 'alerts.cart.invalid_membership.message_new_edit');
    });
  });

  describe('updateCart', () => {
    it('ersetzt den Einkauf nach dem Speichern in beiden Listen, sortiert neu und meldet die Änderung', () => {
      const newest = aCart({ id: 1, datePurchased: new Date('2025-03-01') });
      const edited = aCart({ id: 2, title: 'Alt', datePurchased: new Date('2025-02-01') });
      const filteredOut = aCart({ id: 3, datePurchased: new Date('2025-01-01') });
      service.cartList.set([newest, edited]);
      service.initCartList.set([newest, edited, filteredOut]);
      const updatesBefore = service.cartUpdated();
      const changed = { ...edited, title: 'Neu', datePurchased: new Date('2025-04-01') };

      service.updateCart(changed);
      const req = http.expectOne(`${api}/update`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(changed);
      req.flush(changed);

      expect(ids(service.cartList())).toEqual([2, 1]);
      expect(ids(service.initCartList())).toEqual([2, 1, 3]);
      expect(service.cartList()[0].title).toBe('Neu');
      expect(service.initCartList()[0].title).toBe('Neu');
      expect(service.cartUpdated()).toBe(updatesBefore + 1);
    });

    // Bearbeiteter Einkauf und ein weiterer aus derselben Vorlage (bzw. einer anderen) – je nach
    // Antwort muss der andere Einkauf in beiden Listen von der Vorlage gelöst werden oder unverändert bleiben.
    [
      {
        name: 'löst bei geändertem Wiederholungstyp und geänderter Vorlage die anderen Einkäufe der Vorlage',
        newRecurrenceType: RECURRENCE_TYPE.WEEKLY, templateUpdateSelected: false, hasTemplateChanged: true,
        siblingTemplateId: 50, siblingDetached: true,
      },
      {
        name: 'löst bei ausgewählter Vorlagen-Aktualisierung und geänderter Vorlage die anderen Einkäufe der Vorlage',
        newRecurrenceType: RECURRENCE_TYPE.MONTHLY, templateUpdateSelected: true, hasTemplateChanged: true,
        siblingTemplateId: 50, siblingDetached: true,
      },
      {
        name: 'lässt die anderen Einkäufe der Vorlage unverändert, wenn sich die Vorlage nicht geändert hat',
        newRecurrenceType: RECURRENCE_TYPE.WEEKLY, templateUpdateSelected: false, hasTemplateChanged: false,
        siblingTemplateId: 50, siblingDetached: false,
      },
      {
        name: 'lässt Einkäufe einer anderen Vorlage unverändert',
        newRecurrenceType: RECURRENCE_TYPE.WEEKLY, templateUpdateSelected: false, hasTemplateChanged: true,
        siblingTemplateId: 99, siblingDetached: false,
      },
    ].forEach(c => {
      it(c.name, () => {
        const edited = aCart({
          id: 1, recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true, templateId: 50,
          datePurchased: new Date('2025-02-01'),
        });
        const sibling = aCart({
          id: 2, recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true, templateId: c.siblingTemplateId,
          datePurchased: new Date('2025-01-01'),
        });
        service.cartList.set([edited, sibling]);
        service.initCartList.set([edited, sibling]);
        const changed = { ...edited, recurrenceType: c.newRecurrenceType, templateUpdateSelected: c.templateUpdateSelected };

        service.updateCart(changed);
        http.expectOne(`${api}/update`).flush({ ...changed, hasTemplateChanged: c.hasTemplateChanged });

        const expectedSibling = c.siblingDetached
          ? { recurrenceType: RECURRENCE_TYPE.NONE, hasActiveTemplate: false }
          : { recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true };
        for (const list of [service.cartList(), service.initCartList()]) {
          expect(list.find(x => x.id === 1)).toEqual(jasmine.objectContaining({ recurrenceType: c.newRecurrenceType }));
          expect(list.find(x => x.id === 2)).toEqual(jasmine.objectContaining(expectedSibling));
        }
      });
    });

    it('lässt beide Listen bei einem Fehler unverändert und zeigt den Mitgliedschafts-Hinweis', () => {
      const cartList = [aCart({ id: 1 })];
      const initCartList = [aCart({ id: 1 }), aCart({ id: 3 })];
      service.cartList.set(cartList);
      service.initCartList.set(initCartList);
      const updatesBefore = service.cartUpdated();

      service.updateCart({ ...cartList[0], title: 'Neu' });
      http.expectOne(`${api}/update`).flush(notWithinMembership, { status: 400, statusText: 'Bad Request' });

      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(initCartList);
      expect(service.cartUpdated()).toBe(updatesBefore);
      expect(alerts.showErrorAlert).toHaveBeenCalledWith(
        'alerts.cart.invalid_membership.header', 'alerts.cart.invalid_membership.message_new_edit');
    });
  });

  describe('deleteCart', () => {
    it('entfernt den Einkauf nach dem Löschen aus beiden Listen und meldet die Änderung', () => {
      service.cartList.set([aCart({ id: 1 }), aCart({ id: 2 })]);
      service.initCartList.set([aCart({ id: 1 }), aCart({ id: 2 }), aCart({ id: 3 })]);
      const updatesBefore = service.cartUpdated();

      service.deleteCart(2);
      const req = http.expectOne(`${api}/delete/2`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      expect(ids(service.cartList())).toEqual([1]);
      expect(ids(service.initCartList())).toEqual([1, 3]);
      expect(service.cartUpdated()).toBe(updatesBefore + 1);
    });

    it('lässt beide Listen unverändert, wenn der Nutzer nicht Eigentümer des Einkaufs ist', () => {
      const cartList = [aCart({ id: 1 }), aCart({ id: 2 })];
      const initCartList = [aCart({ id: 1 }), aCart({ id: 2 }), aCart({ id: 3 })];
      service.cartList.set(cartList);
      service.initCartList.set(initCartList);
      const updatesBefore = service.cartUpdated();

      service.deleteCart(2);
      http.expectOne(`${api}/delete/2`).flush('User with ID 5 is not the owner of the cart',
        { status: 403, statusText: 'Forbidden' });

      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(initCartList);
      expect(service.cartUpdated()).toBe(updatesBefore);
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });

  describe('addSettlementPayment', () => {
    const payment = (): SettlementPaymentDto => ({
      amount: 25, groupId: 1, member: aUser({ userName: 'Anna' }), datePurchased: new Date('2025-02-01'),
    });

    it('fügt die erzeugten Ausgleichsbuchungen sortiert in beide Listen ein und meldet die Änderung', () => {
      const older = aCart({ id: 1, datePurchased: new Date('2025-01-01') });
      const newest = aCart({ id: 4, datePurchased: new Date('2025-03-01') });
      service.cartList.set([older]);
      service.initCartList.set([newest, older]);
      const updatesBefore = service.cartUpdated();
      const dto = payment();

      service.addSettlementPayment(dto);
      const req = http.expectOne(`${api}/settlement-payment/add`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dto);
      req.flush([
        aCart({ id: 2, datePurchased: new Date('2025-02-01') }),
        aCart({ id: 3, datePurchased: new Date('2025-02-02') }),
      ]);

      expect(ids(service.cartList())).toEqual([3, 2, 1]);
      expect(ids(service.initCartList())).toEqual([4, 3, 2, 1]);
      expect(service.cartUpdated()).toBe(updatesBefore + 1);
    });

    it('lässt beide Listen bei einem Fehler unverändert und zeigt den Mitgliedschafts-Hinweis mit dem Mitglied', () => {
      const cartList = [aCart({ id: 1 })];
      const initCartList = [aCart({ id: 1 }), aCart({ id: 3 })];
      service.cartList.set(cartList);
      service.initCartList.set(initCartList);
      const updatesBefore = service.cartUpdated();

      service.addSettlementPayment(payment());
      http.expectOne(`${api}/settlement-payment/add`).flush(notWithinMembership,
        { status: 400, statusText: 'Bad Request' });

      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(initCartList);
      expect(service.cartUpdated()).toBe(updatesBefore);
      // TranslateService ohne Loader liefert den Key, die Parameter werden dabei nicht eingesetzt.
      expect(alerts.showErrorAlert).toHaveBeenCalledWith(
        'alerts.cart.invalid_membership.header', 'alerts.cart.invalid_membership.message_settlementpayment');
    });
  });

  // Nur der Mitgliedschafts-Fehler hat einen eigenen Hinweis; andere Fehler laufen ohne Alert durch.
  [
    { name: 'addCart', call: (s: CartService) => s.addCart(aCart({ id: null })), url: `${api}/add` },
    { name: 'updateCart', call: (s: CartService) => s.updateCart(aCart({ id: 1 })), url: `${api}/update` },
    {
      name: 'addSettlementPayment',
      call: (s: CartService) => s.addSettlementPayment(
        { amount: 25, groupId: 1, member: aUser(), datePurchased: new Date('2025-02-01') }),
      url: `${api}/settlement-payment/add`,
    },
  ].forEach(c => {
    it(`${c.name} zeigt bei einem anderen Fehler keinen Hinweis`, () => {
      c.call(service);
      http.expectOne(c.url).flush('User with ID 5 is either a member nor the owner of the group',
        { status: 403, statusText: 'Forbidden' });

      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });
});
