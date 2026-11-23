import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { fakeAsync, flush, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Init } from 'src/app/constants/default-values';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { AlertService } from 'src/app/service/alert.service';
import { SettlementPaymentDto } from '../model/settlement-payment-dto';
import { SettlementEditData } from '../settlement-sheet/settlement-form';
import { environment } from 'src/environments/environment';
import { aCart, aGroup, aUser } from 'src/testing/test-data';
import { alertServiceSpy, provideServiceTesting } from 'src/testing/test-providers';
import { FileExportService } from 'src/app/service/file-export.service';
import { Cart } from '../model/cart';
import { CartService } from './cart.service';

describe('CartService', () => {
  let service: CartService;
  let http: HttpTestingController;
  let alerts: jasmine.SpyObj<AlertService>;
  let fileExport: jasmine.SpyObj<FileExportService>;
  const api = `${environment.apiBaseUrl}/api/carts`;
  const notWithinMembership = 'Date purchased is not within membership period';
  const ids = (carts: Cart[]) => carts.map(c => c.id);

  beforeEach(() => {
    alerts = alertServiceSpy();
    fileExport = jasmine.createSpyObj<FileExportService>('FileExportService', ['saveAndOpen']);
    fileExport.saveAndOpen.and.resolveTo('opened');
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AlertService, useValue: alerts },
        { provide: FileExportService, useValue: fileExport },
      ],
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

    // DIV-77: Summe in Cent wie im Backend (toCents), ohne Gleitkommafehler
    [
      { amounts: [0.1, 0.2], sum: 0.3 },
      { amounts: [1.005, -1.005], sum: 0 },
      { amounts: [65.349875, 10], sum: 75.35 },
    ].forEach(({ amounts, sum }) => {
      it(`summiert ${JSON.stringify(amounts)} centgenau zu ${sum}`, () => {
        service.getCartListByGroupId(aGroup({ id: 7 }), false);
        http.expectOne(`${api}/carts-by-groupid/7`).flush(amounts.map(amount => aCart({ amount })));

        expect(service.sum()).toBe(sum);
        expect(Object.is(service.sum(), -0)).toBeFalse();
      });
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

    it('behält mit silent bei einem Ladefehler den bisherigen Stand (DIV-74)', () => {
      const cartList = [aCart({ id: 1 })];
      const initCartList = [aCart({ id: 1 }), aCart({ id: 2 })];
      service.cartList.set(cartList);
      service.initCartList.set(initCartList);
      const onComplete = jasmine.createSpy('onComplete');

      service.getCartListByGroupId(aGroup({ id: 7 }), false, onComplete, { silent: true });
      http.expectOne(`${api}/carts-by-groupid/7`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(initCartList);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('verwirft eine Antwort, die nach einem Gruppen- oder Sitzungswechsel eintrifft (isCurrent, DIV-74)', () => {
      const aktuell = [aCart({ id: 9 })];
      service.cartList.set(aktuell);
      service.initCartList.set(aktuell);
      const updatesBefore = service.cartUpdated();

      service.getCartListByGroupId(aGroup({ id: 7 }), true, undefined, { isCurrent: () => false });
      http.expectOne(`${api}/carts-by-groupid/7`).flush([aCart({ id: 1 })]);

      expect(ids(service.cartList())).toEqual([9]);
      expect(ids(service.initCartList())).toEqual([9]);
      expect(service.cartUpdated()).toBe(updatesBefore);
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
      const older = aCart({ id: 1, datePurchased: '2025-01-01' });
      const filteredOut = aCart({ id: 3, datePurchased: '2025-03-01' });
      service.cartList.set([older]);
      service.initCartList.set([older, filteredOut]);
      const updatesBefore = service.cartUpdated();
      const newCart = aCart({ id: null, title: 'Pizza', datePurchased: '2025-02-01' });

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

    it('ein Fehler verwirft keinen zwischenzeitlich gespeicherten Einkauf, Regression DIV-1', () => {
      service.cartList.set([]);
      service.initCartList.set([]);
      const pizza = aCart({ id: null, title: 'Pizza' });
      const kino = aCart({ id: null, title: 'Kino' });

      service.addCart(pizza);
      service.addCart(kino);
      const [pizzaReq, kinoReq] = http.match(`${api}/add`);
      kinoReq.flush({ ...kino, id: 2 });
      pizzaReq.flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(ids(service.cartList())).toEqual([2]);
      expect(ids(service.initCartList())).toEqual([2]);
    });
  });

  describe('updateCart', () => {
    it('ersetzt den Einkauf nach dem Speichern in beiden Listen, sortiert neu und meldet die Änderung', () => {
      const newest = aCart({ id: 1, datePurchased: '2025-03-01' });
      const edited = aCart({ id: 2, title: 'Alt', datePurchased: '2025-02-01' });
      const filteredOut = aCart({ id: 3, datePurchased: '2025-01-01' });
      service.cartList.set([newest, edited]);
      service.initCartList.set([newest, edited, filteredOut]);
      const updatesBefore = service.cartUpdated();
      const changed = { ...edited, title: 'Neu', datePurchased: '2025-04-01' };

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
          datePurchased: '2025-02-01',
        });
        const sibling = aCart({
          id: 2, recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true, templateId: c.siblingTemplateId,
          datePurchased: '2025-01-01',
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

  describe('updateSettlementPayment (DIV-74)', () => {
    const group = aGroup({ id: 7 });
    const payment = (): SettlementPaymentDto => ({
      id: 70, amount: 45, groupId: 7, member: aUser({ userName: 'Ben' }), datePurchased: '2025-06-10',
    });
    /** Rolle und Zahler aus Sicht des angemeldeten Nutzers (DIV-110). */
    const alsZahler: Pick<SettlementEditData, 'role' | 'payer'> = { role: 'payer', payer: aUser({ userName: 'Ich' }) };
    const paar = () => [
      aCart({ id: 1, amount: 30, settlementId: 70, datePurchased: '2025-06-15' }),
      aCart({ id: 2, amount: -30, settlementId: 70, datePurchased: '2025-06-15' }),
    ];

    it('sendet PUT, ersetzt beide Einkäufe per Id in beiden Listen, sortiert neu, meldet die Änderung und liefert true', async () => {
      const neuer = aCart({ id: 3, datePurchased: '2025-06-12' });
      service.cartList.set([...paar(), neuer]);
      service.initCartList.set([...paar(), neuer, aCart({ id: 4, datePurchased: '2025-01-01' })]);
      const updatesBefore = service.cartUpdated();
      const dto = payment();

      const result = service.updateSettlementPayment(dto, group, () => true, alsZahler);
      const req = http.expectOne(`${api}/settlement-payment/update`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(dto);
      req.flush([
        aCart({ id: 1, amount: 45, settlementId: 70, datePurchased: '2025-06-10' }),
        aCart({ id: 2, amount: -45, settlementId: 70, datePurchased: '2025-06-10' }),
      ]);

      expect(await result).toBeTrue();
      expect(ids(service.cartList())).toEqual([3, 1, 2]);
      expect(service.cartList().filter(c => c.settlementId === 70).map(c => c.amount)).toEqual([45, -45]);
      expect(ids(service.initCartList())).toEqual([3, 1, 2, 4]);
      expect(service.cartUpdated()).toBe(updatesBefore + 1);
    });

    it('Mitgliedschaftsfehler, angemeldeter Nutzer ist Zahler: State unverändert, Hinweis mit dem gewählten Empfänger (exakte Backend-Meldung, M1), liefert false', async () => {
      const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
      const cartList = paar();
      service.cartList.set(cartList);
      service.initCartList.set(cartList);
      // Der Zahler hat den bisherigen Empfänger (Ben) gegen Cleo getauscht: geprüft und genannt wird der gewählte
      const dto = { ...payment(), member: aUser({ userName: 'Cleo' }) };

      const result = service.updateSettlementPayment(dto, group, () => true, alsZahler);
      http.expectOne(`${api}/settlement-payment/update`).flush(notWithinMembership, { status: 400, statusText: 'Bad Request' });

      expect(await result).toBeFalse();
      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(cartList);
      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
        'alerts.cart.invalid_membership.header', 'alerts.cart.invalid_membership.message_settlementpayment');
      expect(translate).toHaveBeenCalledWith('alerts.cart.invalid_membership.message_settlementpayment', { userName: 'Cleo' });
    });

    it('Mitgliedschaftsfehler, angemeldeter Nutzer ist Empfänger: Hinweis nennt den Zahler, nicht sich selbst (DIV-110)', async () => {
      const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
      const cartList = paar();
      service.cartList.set(cartList);
      service.initCartList.set(cartList);
      // Im Edit-Modus ist `member` immer der Empfänger, hier also der angemeldete Nutzer selbst
      const dto = { ...payment(), member: aUser({ userName: 'Sven' }) };
      const alsEmpfaenger: Pick<SettlementEditData, 'role' | 'payer'> = { role: 'receiver', payer: aUser({ userName: 'Basti' }) };

      const result = service.updateSettlementPayment(dto, group, () => true, alsEmpfaenger);
      http.expectOne(`${api}/settlement-payment/update`).flush(notWithinMembership, { status: 400, statusText: 'Bad Request' });

      expect(await result).toBeFalse();
      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(cartList);
      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
        'alerts.cart.invalid_membership.header', 'alerts.cart.invalid_membership.message_settlementpayment');
      expect(translate).toHaveBeenCalledWith('alerts.cart.invalid_membership.message_settlementpayment', { userName: 'Basti' });
      expect(translate).not.toHaveBeenCalledWith('alerts.cart.invalid_membership.message_settlementpayment', { userName: 'Sven' });
    });

    it('anderer Fehler: State unverändert, allgemeiner Alert, liefert false', async () => {
      const cartList = paar();
      service.cartList.set(cartList);
      service.initCartList.set(cartList);

      const result = service.updateSettlementPayment(payment(), group, () => true, alsZahler);
      http.expectOne(`${api}/settlement-payment/update`).flush('Only the payer may change the receiver', { status: 403, statusText: 'Forbidden' });

      expect(await result).toBeFalse();
      expect(service.cartList()).toEqual(cartList);
      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
        'alerts.cart.settlement_update_failed.header', 'alerts.cart.settlement_update_failed.message');
    });

    it('404: Hinweis „existiert nicht mehr“, stilles Neuladen der Liste, liefert true (Sheet schließt)', async () => {
      service.cartList.set(paar());
      service.initCartList.set(paar());

      const result = service.updateSettlementPayment(payment(), group, () => true, alsZahler);
      http.expectOne(`${api}/settlement-payment/update`).flush('Settlement with Id 70 not found', { status: 404, statusText: 'Not Found' });

      expect(await result).toBeTrue();
      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
        'alerts.cart.settlement_not_found.header', 'alerts.cart.settlement_not_found.message');
      http.expectOne(`${api}/carts-by-groupid/7`).flush([aCart({ id: 5 })]);
      expect(ids(service.cartList())).toEqual([5]);
    });

    it('404 und scheiterndes Neuladen: bisheriger Stand bleibt', async () => {
      service.cartList.set(paar());
      service.initCartList.set(paar());

      const result = service.updateSettlementPayment(payment(), group, () => true, alsZahler);
      http.expectOne(`${api}/settlement-payment/update`).flush('not found', { status: 404, statusText: 'Not Found' });
      http.expectOne(`${api}/carts-by-groupid/7`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(await result).toBeTrue();
      expect(ids(service.cartList())).toEqual([1, 2]);
    });

    it('verwirft die Antwort nach Gruppen- oder Sitzungswechsel: kein State-Update, kein Alert, kein Neuladen', async () => {
      const cartList = paar();
      service.cartList.set(cartList);
      service.initCartList.set(cartList);
      const updatesBefore = service.cartUpdated();

      const erfolg = service.updateSettlementPayment(payment(), group, () => false, alsZahler);
      http.expectOne(`${api}/settlement-payment/update`).flush([aCart({ id: 1, amount: 45, settlementId: 70 })]);
      const fehler = service.updateSettlementPayment(payment(), group, () => false, alsZahler);
      http.expectOne(`${api}/settlement-payment/update`).flush('not found', { status: 404, statusText: 'Not Found' });

      expect(await erfolg).toBeTrue();
      expect(await fehler).toBeTrue();
      http.expectNone(`${api}/carts-by-groupid/7`);
      expect(service.cartList()).toEqual(cartList);
      expect(service.cartUpdated()).toBe(updatesBefore);
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });

  describe('deleteSettlementPayment (DIV-74)', () => {
    const group = aGroup({ id: 7 });
    const paar = () => [aCart({ id: 1, settlementId: 70 }), aCart({ id: 2, settlementId: 70 })];

    it('entfernt nach dem Löschen beide Einkäufe des Settlements aus beiden Listen und meldet die Änderung', () => {
      service.cartList.set([...paar(), aCart({ id: 3 })]);
      service.initCartList.set([...paar(), aCart({ id: 3 }), aCart({ id: 4, settlementId: 71 })]);
      const updatesBefore = service.cartUpdated();

      service.deleteSettlementPayment(70, group, () => true);
      const req = http.expectOne(`${api}/settlement-payment/delete/70`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      expect(ids(service.cartList())).toEqual([3]);
      expect(ids(service.initCartList())).toEqual([3, 4]);
      expect(service.cartUpdated()).toBe(updatesBefore + 1);
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });

    it('lässt beide Listen bei einem Fehler unverändert und zeigt den Lösch-Alert', () => {
      const cartList = [...paar(), aCart({ id: 3 })];
      service.cartList.set(cartList);
      service.initCartList.set(cartList);
      const updatesBefore = service.cartUpdated();

      service.deleteSettlementPayment(70, group, () => true);
      http.expectOne(`${api}/settlement-payment/delete/70`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(cartList);
      expect(service.cartUpdated()).toBe(updatesBefore);
      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
        'alerts.cart.settlement_delete_failed.header', 'alerts.cart.settlement_delete_failed.message');
      expect(alerts.showToast).not.toHaveBeenCalled();
    });

    it('404: kein Fehler-Alert, Hinweis „bereits gelöscht“ und stilles Neuladen der Liste', () => {
      service.cartList.set(paar());
      service.initCartList.set(paar());

      service.deleteSettlementPayment(70, group, () => true);
      http.expectOne(`${api}/settlement-payment/delete/70`)
        .flush('Settlement with Id 70 not found', { status: 404, statusText: 'Not Found' });

      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.cart.settlement_already_deleted');
      http.expectOne(`${api}/carts-by-groupid/7`).flush([aCart({ id: 5 })]);
      expect(ids(service.cartList())).toEqual([5]);
      expect(ids(service.initCartList())).toEqual([5]);
    });

    it('404: scheitert auch das stille Neuladen, bleibt der bisherige Stand erhalten', () => {
      service.cartList.set(paar());
      service.initCartList.set(paar());

      service.deleteSettlementPayment(70, group, () => true);
      http.expectOne(`${api}/settlement-payment/delete/70`).flush('not found', { status: 404, statusText: 'Not Found' });
      http.expectOne(`${api}/carts-by-groupid/7`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(ids(service.cartList())).toEqual([1, 2]);
      expect(ids(service.initCartList())).toEqual([1, 2]);
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });

    [
      { fall: 'Erfolg', antwort: () => null, status: undefined },
      { fall: 'Fehler 500', antwort: () => 'Server error', status: 500 },
      { fall: '404', antwort: () => 'not found', status: 404 },
    ].forEach(({ fall, antwort, status }) => {
      it(`verwirft die Antwort (${fall}) nach Gruppen- oder Sitzungswechsel: kein State-Update, kein Alert, kein Neuladen`, () => {
        const cartList = paar();
        service.cartList.set(cartList);
        service.initCartList.set(cartList);
        const updatesBefore = service.cartUpdated();

        service.deleteSettlementPayment(70, group, () => false);
        const req = http.expectOne(`${api}/settlement-payment/delete/70`);
        if (status) {
          req.flush(antwort(), { status, statusText: 'Error' });
        } else {
          req.flush(antwort());
        }

        http.expectNone(`${api}/carts-by-groupid/7`);
        expect(service.cartList()).toEqual(cartList);
        expect(service.initCartList()).toEqual(cartList);
        expect(service.cartUpdated()).toBe(updatesBefore);
        expect(alerts.showErrorAlert).not.toHaveBeenCalled();
        expect(alerts.showToast).not.toHaveBeenCalled();
      });
    });
  });

  describe('addSettlementPayment', () => {
    const payment = (): SettlementPaymentDto => ({
      amount: 25, groupId: 1, member: aUser({ userName: 'Anna' }), datePurchased: '2025-02-01',
    });

    it('fügt die erzeugten Ausgleichsbuchungen sortiert in beide Listen ein, meldet die Änderung und liefert true', async () => {
      const older = aCart({ id: 1, datePurchased: '2025-01-01' });
      const newest = aCart({ id: 4, datePurchased: '2025-03-01' });
      service.cartList.set([older]);
      service.initCartList.set([newest, older]);
      const updatesBefore = service.cartUpdated();
      const dto = payment();

      const result = service.addSettlementPayment(dto);
      const req = http.expectOne(`${api}/settlement-payment/add`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dto);
      req.flush([
        aCart({ id: 2, datePurchased: '2025-02-01' }),
        aCart({ id: 3, datePurchased: '2025-02-02' }),
      ]);

      expect(ids(service.cartList())).toEqual([3, 2, 1]);
      expect(ids(service.initCartList())).toEqual([4, 3, 2, 1]);
      expect(service.cartUpdated()).toBe(updatesBefore + 1);
      expect(await result).toBeTrue();
    });

    it('lässt beide Listen bei einem Fehler unverändert, zeigt den Mitgliedschafts-Hinweis mit dem Mitglied und liefert false', async () => {
      const cartList = [aCart({ id: 1 })];
      const initCartList = [aCart({ id: 1 }), aCart({ id: 3 })];
      service.cartList.set(cartList);
      service.initCartList.set(initCartList);
      const updatesBefore = service.cartUpdated();
      const instant = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();

      const result = service.addSettlementPayment(payment());
      http.expectOne(`${api}/settlement-payment/add`).flush(notWithinMembership,
        { status: 400, statusText: 'Bad Request' });

      expect(service.cartList()).toEqual(cartList);
      expect(service.initCartList()).toEqual(initCartList);
      expect(service.cartUpdated()).toBe(updatesBefore);
      expect(alerts.showErrorAlert).toHaveBeenCalledWith(
        'alerts.cart.invalid_membership.header', 'alerts.cart.invalid_membership.message_settlementpayment');
      // TranslateService ohne Loader setzt keine Parameter ein – deshalb am Aufruf von instant() prüfen.
      expect(instant).toHaveBeenCalledWith(
        'alerts.cart.invalid_membership.message_settlementpayment', { userName: 'Anna' });
      expect(alerts.showErrorAlert).toHaveBeenCalledTimes(1);
      expect(await result).toBeFalse();
    });

    it('erhaltene Zahlung (DIV-74): sendet payer und nennt im Mitgliedschafts-Hinweis die Gegenpartei (Zahler)', async () => {
      const instant = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
      const dto: SettlementPaymentDto = {
        ...payment(), payer: aUser({ userName: 'Ben' }), member: aUser({ userName: 'Ich' }),
      };

      const result = service.addSettlementPayment(dto);
      const req = http.expectOne(`${api}/settlement-payment/add`);
      expect(req.request.body.payer.userName).toBe('Ben');
      req.flush(notWithinMembership, { status: 400, statusText: 'Bad Request' });

      expect(await result).toBeFalse();
      expect(instant).toHaveBeenCalledWith('alerts.cart.invalid_membership.message_settlementpayment', { userName: 'Ben' });
    });

    // DIV-70: Jeder andere Fehler zeigt einen allgemeinen Hinweis und liefert false. Der Fehlerbody ist nicht immer Text
    // (Netzwerkfehler, JSON, null) – die Prüfung auf den Mitgliedschafts-Text darf daran nicht scheitern.
    [
      {
        name: 'Text-Body (403)',
        fail: (req: TestRequest) => req.flush('User with ID 5 is either a member nor the owner of the group',
          { status: 403, statusText: 'Forbidden' }),
      },
      { name: 'null-Body (500)', fail: (req: TestRequest) => req.flush(null, { status: 500, statusText: 'Server Error' }) },
      {
        name: 'Objekt-Body (400)',
        fail: (req: TestRequest) => req.flush({ message: 'Bad Request' }, { status: 400, statusText: 'Bad Request' }),
      },
      { name: 'Netzwerkfehler (Status 0)', fail: (req: TestRequest) => req.error(new ProgressEvent('error')) },
    ].forEach(c => {
      it(`zeigt bei einem anderen Fehler (${c.name}) den allgemeinen Hinweis, lässt den State unverändert und liefert false`, async () => {
        const cartList = [aCart({ id: 1 })];
        const initCartList = [aCart({ id: 1 }), aCart({ id: 3 })];
        service.cartList.set(cartList);
        service.initCartList.set(initCartList);
        const updatesBefore = service.cartUpdated();

        const result = service.addSettlementPayment(payment());
        c.fail(http.expectOne(`${api}/settlement-payment/add`));

        expect(await result).toBeFalse();
        expect(service.cartList()).toEqual(cartList);
        expect(service.initCartList()).toEqual(initCartList);
        expect(service.cartUpdated()).toBe(updatesBefore);
        expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
          'alerts.cart.settlement_failed.header', 'alerts.cart.settlement_failed.message');
      });
    });
  });

  // addCart/updateCart: Nur der Mitgliedschafts-Fehler hat einen eigenen Hinweis; andere Fehler laufen ohne Alert durch.
  // (addSettlementPayment zeigt seit DIV-70 einen allgemeinen Hinweis, siehe oben.)
  [
    { name: 'addCart', call: (s: CartService) => s.addCart(aCart({ id: null })), url: `${api}/add` },
    { name: 'updateCart', call: (s: CartService) => s.updateCart(aCart({ id: 1 })), url: `${api}/update` },
  ].forEach(c => {
    it(`${c.name} zeigt bei einem anderen Fehler keinen Hinweis`, () => {
      c.call(service);
      http.expectOne(c.url).flush('User with ID 5 is either a member nor the owner of the group',
        { status: 403, statusText: 'Forbidden' });

      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });

    // DIV-70 Phase 4 (A): Der Fehlerbody ist nicht immer Text. `err.error.includes` warf bisher im error-Callback;
    // RxJS meldet das asynchron als unbehandelten Fehler – flush() holt ihn in diesen Test.
    [
      { name: 'null-Body (500)', fail: (req: TestRequest) => req.flush(null, { status: 500, statusText: 'Server Error' }) },
      {
        name: 'Objekt-Body (400)',
        fail: (req: TestRequest) => req.flush({ message: 'Bad Request' }, { status: 400, statusText: 'Bad Request' }),
      },
      { name: 'Netzwerkfehler (Status 0)', fail: (req: TestRequest) => req.error(new ProgressEvent('error')) },
    ].forEach(f => {
      it(`${c.name}: ${f.name} läuft ohne Absturz und ohne Hinweis durch, State unverändert`, fakeAsync(() => {
        const cartList = [aCart({ id: 1 })];
        service.cartList.set(cartList);
        service.initCartList.set(cartList);
        const updatesBefore = service.cartUpdated();

        c.call(service);
        f.fail(http.expectOne(c.url));
        flush();

        expect(alerts.showErrorAlert).not.toHaveBeenCalled();
        expect(service.cartList()).toEqual(cartList);
        expect(service.initCartList()).toEqual(cartList);
        expect(service.cartUpdated()).toBe(updatesBefore);
      }));
    });
  });

  describe('exportCarts (DIV-68)', () => {
    const xlsxMime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    const exportRequest = () => http.expectOne(req => req.url === `${api}/export`);
    const file = new Blob(['PK'], { type: 'application/octet-stream' });

    beforeEach(() => {
      jasmine.clock().install();
      jasmine.clock().mockDate(new Date(2026, 10, 8, 15, 30, 5));
    });

    afterEach(() => jasmine.clock().uninstall());

    it('lädt die aktuelle Gruppe als XLSX, speichert sie unter dem Exportnamen und meldet den Erfolg', async () => {
      const carts = [aCart()];
      service.cartList.set(carts);
      const updatesBefore = service.cartUpdated();

      const result = service.exportCarts({ scope: 'GROUP', format: 'XLSX' }, aGroup({ id: 7, name: 'WG Haushalt' }));
      const req = exportRequest();
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      expect(req.request.params.get('format')).toBe('XLSX');
      expect(req.request.params.get('groupId')).toBe('7');
      req.flush(file);

      expect(await result).toBeTrue();
      expect(fileExport.saveAndOpen).toHaveBeenCalledOnceWith(
        jasmine.any(Blob), 'domains.spendings.title_WG-Haushalt_20261108153005.xlsx', xlsxMime);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('domains.spendings.toast.message');
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
      expect(service.cartList()).toBe(carts);
      expect(service.cartUpdated()).toBe(updatesBefore);
    });

    it('lädt alle Gruppen als CSV ohne groupId und speichert ein ZIP', async () => {
      const result = service.exportCarts({ scope: 'ALL', format: 'CSV' }, aGroup({ id: 7 }));
      const req = exportRequest();
      expect(req.request.params.keys()).toEqual(['format']);
      expect(req.request.params.get('format')).toBe('CSV');
      req.flush(file);

      expect(await result).toBeTrue();
      expect(fileExport.saveAndOpen).toHaveBeenCalledOnceWith(jasmine.any(Blob),
        'domains.spendings.title_domains.spendings.export.filename_all_groups_20261108153005.zip', 'application/zip');
    });

    it('meldet „gespeichert, aber nicht geöffnet“, wenn keine App die Datei öffnen kann', async () => {
      fileExport.saveAndOpen.and.resolveTo('saved');

      const result = service.exportCarts({ scope: 'GROUP', format: 'CSV' }, aGroup());
      exportRequest().flush(file);

      expect(await result).toBeTrue();
      expect(alerts.showToast).toHaveBeenCalledOnceWith('domains.spendings.export.toast_saved_not_opened');
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });

    it('zeigt bei einem HTTP-Fehler den Export-Alert und speichert nichts', async () => {
      const result = service.exportCarts({ scope: 'GROUP', format: 'XLSX' }, aGroup());
      exportRequest().flush(new Blob(['User with ID 5 is either a member nor the owner of the group']),
        { status: 403, statusText: 'Forbidden' });

      expect(await result).toBeFalse();
      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith('alerts.export.header', 'alerts.export.message');
      expect(fileExport.saveAndOpen).not.toHaveBeenCalled();
      expect(alerts.showToast).not.toHaveBeenCalled();
    });

    it('zeigt den Export-Alert, wenn das Speichern fehlschlägt', async () => {
      fileExport.saveAndOpen.and.rejectWith(new Error('disk full'));

      const result = service.exportCarts({ scope: 'GROUP', format: 'XLSX' }, aGroup());
      exportRequest().flush(file);

      expect(await result).toBeFalse();
      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith('alerts.export.header', 'alerts.export.message');
      expect(alerts.showToast).not.toHaveBeenCalled();
    });

    it('sendet für die Platzhalter-Gruppe keinen Request', async () => {
      expect(await service.exportCarts({ scope: 'GROUP', format: 'XLSX' }, Init.DEFAULT_GROUP)).toBeFalse();
      expect(fileExport.saveAndOpen).not.toHaveBeenCalled();
    });
  });
});
