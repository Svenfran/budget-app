import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { Init } from 'src/app/constants/default-values';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { environment } from 'src/environments/environment';
import { aGroup, aLoggedInUser, aShoppingItem, aShoppingList } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { AddEditShoppingItemDto } from '../model/add-edit-shopping-item-dto';
import { AddEditShoppinglistDto } from '../model/add-edit-shoppinglist-dto';
import { ShoppinglistDto } from '../model/shoppinglist-dto';
import { ShoppinglistService } from './shoppinglist.service';

// Die Einkaufslisten-Specs prüfen nur Zustand und HTTP, keine Navigation (z. B. server-unavailable),
// damit sie gültig bleiben, wenn die Listen offline-fähig werden.
describe('ShoppinglistService', () => {
  let service: ShoppinglistService;
  let http: HttpTestingController;
  let existing: ShoppinglistDto[];
  let alerts: jasmine.SpyObj<AlertService>;
  let groupService: GroupService;
  /** steuerbarer Anmeldestatus (Logout/Login), den GroupService beobachtet */
  let user$: BehaviorSubject<User | null>;
  const api = `${environment.apiBaseUrl}/api/groups`;
  const forbidden = { status: 403, statusText: 'Forbidden' };
  const forbiddenBody = 'User with ID 5 is either a member nor the owner of the group';

  const listDto = (overrides: Partial<AddEditShoppinglistDto> = {}): AddEditShoppinglistDto =>
    ({ id: null, name: 'Baumarkt', groupId: 1, ...overrides });
  const itemDto = (overrides: Partial<AddEditShoppingItemDto> = {}): AddEditShoppingItemDto =>
    ({ id: null, name: 'Milch', completed: false, shoppingListId: 10, groupId: 1, ...overrides });

  beforeEach(() => {
    alerts = alertServiceSpy();
    user$ = new BehaviorSubject<User | null>(aLoggedInUser());
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(user$);
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AlertService, useValue: alerts },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
      ],
    });
    groupService = TestBed.inject(GroupService);
    service = TestBed.inject(ShoppinglistService);
    http = TestBed.inject(HttpTestingController);

    // Zwei Listen, damit sich prüfen lässt, dass nur die betroffene Liste geändert wird.
    existing = [
      aShoppingList({
        id: 10, name: 'Supermarkt',
        shoppingItems: [aShoppingItem({ id: 11 }), aShoppingItem({ id: 12, completed: true }), aShoppingItem({ id: 13, completed: true })],
      }),
      aShoppingList({ id: 20, name: 'Drogerie', shoppingItems: [aShoppingItem({ id: 21, completed: true })] }),
    ];
    service.getShoppingLists().set(existing);
  });

  afterEach(() => http.verify());

  describe('Laden', () => {
    const url = `${api}/shopping-lists-with-items/7`;

    it('lädt die Listen der Gruppe mit allen Einträgen', () => {
      const loaded = [aShoppingList({ shoppingItems: [aShoppingItem()] })];
      const onComplete = jasmine.createSpy('onComplete');

      service.getShoppingListsWithItems(aGroup({ id: 7 }), onComplete);
      http.expectOne(url).flush(loaded);

      expect(service.getShoppingLists()()).toEqual(loaded);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('leert die Listen bei einem Ladefehler', () => {
      const onComplete = jasmine.createSpy('onComplete');

      service.getShoppingListsWithItems(aGroup({ id: 7 }), onComplete);
      http.expectOne(url).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.getShoppingLists()()).toEqual([]);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('still: lässt die Listen bei einem Ladefehler unverändert', () => {
      spyOn(console, 'warn');
      const onComplete = jasmine.createSpy('onComplete');

      service.getShoppingListsWithItems(aGroup({ id: 7 }), onComplete, { silent: true });
      http.expectOne(url).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.getShoppingLists()()).toBe(existing);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });

    it('still: ersetzt die Listen bei Erfolg, solange die Gruppe noch aktuell ist', () => {
      const loaded = [aShoppingList()];

      service.getShoppingListsWithItems(aGroup({ id: 7 }), undefined, { silent: true, isCurrent: () => true });
      http.expectOne(url).flush(loaded);

      expect(service.getShoppingLists()()).toEqual(loaded);
    });

    it('still: verwirft eine verspätete Antwort für Gruppe A, wenn inzwischen die Listen von Gruppe B geladen sind', () => {
      let activeGroupId = 7;
      const listenVonB = [aShoppingList({ name: 'Listen von B' })];

      service.getShoppingListsWithItems(aGroup({ id: 7 }), undefined, { silent: true, isCurrent: () => activeGroupId === 7 });
      activeGroupId = 8;
      service.getShoppingListsWithItems(aGroup({ id: 8 }));
      http.expectOne(`${api}/shopping-lists-with-items/8`).flush(listenVonB);
      http.expectOne(url).flush([aShoppingList({ name: 'Listen von A' })]);

      expect(service.getShoppingLists()()).toEqual(listenVonB);
    });

    it('leert die Listen für die Platzhalter-Gruppe ohne Request', () => {
      const onComplete = jasmine.createSpy('onComplete');

      service.getShoppingListsWithItems(Init.DEFAULT_GROUP, onComplete);

      http.expectNone(() => true);
      expect(service.getShoppingLists()()).toEqual([]);
      expect(onComplete).toHaveBeenCalledTimes(1);
    });
  });

  describe('Listen', () => {
    it('hängt eine neue Liste nach dem Speichern ohne Einträge an', () => {
      const dto = listDto();

      service.addShoppingList(dto);
      const req = http.expectOne(`${api}/shopping-list/add`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dto);
      req.flush({ ...dto, id: 30 });

      expect(service.getShoppingLists()()).toEqual([...existing, { id: 30, name: 'Baumarkt', shoppingItems: [] }]);
    });

    it('lässt die Listen unverändert und zeigt einen Toast, wenn das Anlegen fehlschlägt', () => {
      service.addShoppingList(listDto());
      http.expectOne(`${api}/shopping-list/add`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
    });

    it('benennt die Liste nach dem Speichern um und behält ihre Einträge', () => {
      const dto = listDto({ id: 10, name: 'Wochenmarkt' });

      service.updateShoppingList(dto);
      const req = http.expectOne(`${api}/shopping-list/update`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(dto);
      req.flush(dto);

      expect(service.getShoppingLists()()).toEqual([{ ...existing[0], name: 'Wochenmarkt' }, existing[1]]);
    });

    it('lässt die Listen unverändert und zeigt einen Toast, wenn das Umbenennen fehlschlägt', () => {
      service.updateShoppingList(listDto({ id: 10, name: 'Wochenmarkt' }));
      http.expectOne(`${api}/shopping-list/update`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
    });

    it('entfernt die Liste nach dem Löschen', () => {
      const dto = listDto({ id: 10, name: 'Supermarkt' });

      service.deleteShoppingList(dto);
      const req = http.expectOne(`${api}/shopping-list/delete`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dto);
      req.flush(null);

      expect(service.getShoppingLists()()).toEqual([existing[1]]);
    });

    it('lässt die Listen unverändert und zeigt einen Toast, wenn das Löschen fehlschlägt', () => {
      service.deleteShoppingList(listDto({ id: 10, name: 'Supermarkt' }));
      http.expectOne(`${api}/shopping-list/delete`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
    });
  });

  describe('Einträge', () => {
    it('Abhaken (notifyOnError = false) scheitert: kein Toast', () => {
      service.updateItemOfShoppingList(itemDto({ id: 11, completed: true, shoppingListId: 10 }), false);
      http.expectOne(`${api}/shopping-list/update-item`).flush(forbiddenBody, forbidden);

      expect(alerts.showToast).not.toHaveBeenCalled();
    });

    it('hängt einen neuen Eintrag nach dem Speichern nur an die Zielliste an', () => {
      const dto = itemDto({ shoppingListId: 20 });

      service.addItemToShoppingList(dto);
      const req = http.expectOne(`${api}/shopping-list/add-item`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dto);
      req.flush({ ...dto, id: 22 });

      expect(service.getShoppingLists()()).toEqual([
        existing[0],
        { ...existing[1], shoppingItems: [...existing[1].shoppingItems, { id: 22, name: 'Milch', completed: false }] },
      ]);
    });

    it('lässt die Listen unverändert und zeigt einen Toast, wenn das Anlegen eines Eintrags fehlschlägt', () => {
      service.addItemToShoppingList(itemDto({ shoppingListId: 20 }));
      http.expectOne(`${api}/shopping-list/add-item`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
    });

    it('ersetzt den Eintrag nach dem Speichern in der Liste, die ihn enthält', () => {
      const dto = itemDto({ id: 11, name: 'Hafermilch', completed: true, shoppingListId: 10 });

      service.updateItemOfShoppingList(dto);
      const req = http.expectOne(`${api}/shopping-list/update-item`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(dto);
      req.flush(dto);

      const [item11, ...otherItems] = existing[0].shoppingItems;
      expect(item11.id).toBe(11);
      expect(service.getShoppingLists()()).toEqual([
        { ...existing[0], shoppingItems: [{ id: 11, name: 'Hafermilch', completed: true }, ...otherItems] },
        existing[1],
      ]);
    });

    it('lässt die Listen unverändert und zeigt einen Toast, wenn das Ändern eines Eintrags fehlschlägt', () => {
      service.updateItemOfShoppingList(itemDto({ id: 11, name: 'Hafermilch', shoppingListId: 10 }));
      http.expectOne(`${api}/shopping-list/update-item`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
    });

    it('entfernt den Eintrag nach dem Löschen aus seiner Liste', () => {
      const dto = itemDto({ id: 11, shoppingListId: 10 });

      service.deleteItemFromShoppingList(dto);
      const req = http.expectOne(`${api}/shopping-list/delete-item`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dto);
      req.flush(null);

      expect(service.getShoppingLists()()).toEqual([
        { ...existing[0], shoppingItems: existing[0].shoppingItems.filter(i => i.id !== 11) },
        existing[1],
      ]);
    });

    it('lässt die Listen unverändert und zeigt einen Toast, wenn das Löschen eines Eintrags fehlschlägt', () => {
      service.deleteItemFromShoppingList(itemDto({ id: 11, shoppingListId: 10 }));
      http.expectOne(`${api}/shopping-list/delete-item`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
    });

    it('entfernt nach dem Löschen der erledigten Einträge nur die gesendeten Einträge der Liste', () => {
      const dtos = [
        itemDto({ id: 12, completed: true, shoppingListId: 10 }),
        itemDto({ id: 13, completed: true, shoppingListId: 10 }),
      ];

      service.deleteAllCompletedShoppingItems(dtos);
      const req = http.expectOne(`${api}/shopping-list/delete-all-items`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dtos);
      req.flush(null);

      // Eintrag 21 ist ebenfalls erledigt, gehört aber zu einer anderen Liste und bleibt.
      expect(service.getShoppingLists()()).toEqual([
        { ...existing[0], shoppingItems: [existing[0].shoppingItems[0]] },
        existing[1],
      ]);
    });

    it('lässt die Listen unverändert und zeigt einen Toast, wenn das Löschen der erledigten Einträge fehlschlägt', () => {
      service.deleteAllCompletedShoppingItems([itemDto({ id: 12, completed: true, shoppingListId: 10 })]);
      http.expectOne(`${api}/shopping-list/delete-all-items`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
    });
  });

  // Reihenfolge (DIV-1): Fälle entlang der Übergangstabelle im Plan (Abschnitt 7). Die reinen Übergänge prüft
  // shoppinglist-order.spec.ts, hier geht es um Service-Zustand, Requests und Kontextwechsel.
  describe('Reihenfolge', () => {
    const group = aGroup({ id: 7, name: 'WG' });
    const andereGruppe = aGroup({ id: 8, name: 'Urlaub' });
    const loadUrl = `${api}/shopping-lists-with-items/7`;
    const listReorderUrl = `${api}/shopping-list/reorder`;
    const itemReorderUrl = `${api}/shopping-list/reorder-items`;
    const serverError = { status: 500, statusText: 'Server Error' };
    const toastKey = 'alerts.shoppinglist.reorder.error';

    const listIds = () => service.getShoppingLists()().map(list => list.id);
    const itemIdsOf = (listId: number) =>
      service.getShoppingLists()().find(list => list.id === listId)?.shoppingItems.map(item => item.id);

    /** GET-Antwort: Listen in der angegebenen Reihenfolge, Liste 1 mit den Einträgen `items`. */
    const response = (ids: number[], listRevision: number, items: number[] = [], itemRevision = 0): ShoppinglistDto[] =>
      ids.map(id => ({
        id, name: `Liste ${id}`, listOrderRevision: listRevision,
        itemOrderRevision: id === 1 ? itemRevision : 0,
        shoppingItems: id === 1 ? items.map(itemId => aShoppingItem({ id: itemId })) : [],
      }));

    function load(lists: ShoppinglistDto[]) {
      service.getShoppingListsWithItems(group);
      http.expectOne(loadUrl).flush(lists);
    }

    const addList = (id: number) => {
      service.addShoppingList(listDto({ groupId: 7 }));
      http.expectOne(`${api}/shopping-list/add`).flush(listDto({ id, groupId: 7 }));
    };
    const addItem = (listId: number, id: number) => {
      service.addItemToShoppingList(itemDto({ shoppingListId: listId, groupId: 7 }));
      http.expectOne(`${api}/shopping-list/add-item`).flush(itemDto({ id, shoppingListId: listId, groupId: 7 }));
    };
    const listOrder = (revision: number, shoppingListIds: number[]) => ({ groupId: 7, revision, shoppingListIds });
    const itemOrder = (listId: number, revision: number, shoppingItemIds: number[]) =>
      ({ groupId: 7, shoppingListId: listId, revision, shoppingItemIds });

    beforeEach(() => {
      spyOn(console, 'error');
      spyOn(console, 'warn');
      groupService.setActiveGroup(group);
      TestBed.flushEffects();
    });

    describe('Grundfälle', () => {
      it('Liste verschieben: sofort sichtbar, PUT mit der neuen Reihenfolge, Erfolg übernimmt die Antwort', () => {
        load(response([1, 2, 3], 5));

        service.moveShoppingList(group, 3, 0);

        expect(listIds()).toEqual([3, 1, 2]);
        const req = http.expectOne(listReorderUrl);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ groupId: 7, revision: 5, shoppingListIds: [3, 1, 2] });
        req.flush(listOrder(6, [3, 1, 2]));

        expect(listIds()).toEqual([3, 1, 2]);
        expect(alerts.showToast).not.toHaveBeenCalled();
      });

      it('Fehler beim Speichern: Rücksprung auf die bestätigte Reihenfolge, Toast, stiller Refetch; dessen Fehler lässt den Stand', () => {
        load(response([1, 2, 3], 5));

        service.moveShoppingList(group, 3, 0);
        http.expectOne(listReorderUrl).flush('Server error', serverError);

        expect(listIds()).toEqual([1, 2, 3]);
        expect(alerts.showToast).toHaveBeenCalledOnceWith(toastKey);
        http.expectOne(loadUrl).flush('Server error', serverError);
        expect(listIds()).toEqual([1, 2, 3]);
      });

      it('unbekannte ID oder gleicher Index: kein Request, keine Änderung', () => {
        load(response([1, 2, 3], 5));

        service.moveShoppingList(group, 99, 0);
        service.moveShoppingList(group, 2, 1);
        service.moveShoppingItem(group, 1, 99, 0);

        http.expectNone(listReorderUrl);
        http.expectNone(itemReorderUrl);
        expect(listIds()).toEqual([1, 2, 3]);
      });

      it('Eintrag verschieben: sofort sichtbar, PUT nur für seine Liste, Erfolg übernimmt die Antwort', () => {
        load(response([1, 2], 5, [11, 12, 13], 2));

        service.moveShoppingItem(group, 1, 13, 0);

        expect(itemIdsOf(1)).toEqual([13, 11, 12]);
        const req = http.expectOne(itemReorderUrl);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ groupId: 7, shoppingListId: 1, revision: 2, shoppingItemIds: [13, 11, 12] });
        req.flush(itemOrder(1, 3, [13, 11, 12]));

        expect(itemIdsOf(1)).toEqual([13, 11, 12]);
      });

      it('Eintrag verschieben scheitert: Rücksprung, Toast, stiller Refetch', () => {
        load(response([1, 2], 5, [11, 12, 13], 2));

        service.moveShoppingItem(group, 1, 13, 0);
        http.expectOne(itemReorderUrl).flush('Server error', serverError);

        expect(itemIdsOf(1)).toEqual([11, 12, 13]);
        expect(alerts.showToast).toHaveBeenCalledOnceWith(toastKey);
        http.expectOne(loadUrl).flush(response([1, 2], 5, [11, 12, 13], 2));
        expect(itemIdsOf(1)).toEqual([11, 12, 13]);
      });
    });

    describe('Revisionen', () => {
      it('ein neueres Event vor der alten PUT-Antwort gewinnt', () => {
        load(response([1, 2, 3], 5));

        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);
        service.applyListOrder(listOrder(8, [2, 3, 1]));
        expect(listIds()).toEqual([3, 1, 2]); // eigener Request offen: noch optimistisch
        req.flush(listOrder(6, [3, 1, 2]));

        expect(listIds()).toEqual([2, 3, 1]);
      });

      it('zwei überlappende eigene Moves: Antwort 2 (Revision 7) vor Antwort 1 (Revision 6) ergibt Revision 7', () => {
        load(response([1, 2, 3], 5));

        service.moveShoppingList(group, 3, 0);
        const erste = http.expectOne(listReorderUrl);
        service.moveShoppingList(group, 1, 2);
        const zweite = http.expectOne(listReorderUrl);
        expect(listIds()).toEqual([3, 2, 1]);
        zweite.flush(listOrder(7, [3, 2, 1]));
        erste.flush(listOrder(6, [3, 1, 2]));

        expect(listIds()).toEqual([3, 2, 1]);
      });

      it('Review 2 F-002 (Listen): A offen, Event bestätigt, B scheitert, A-Antwort veraltet: Event-Reihenfolge, Toast, Refetch', () => {
        load(response([1, 2, 3], 5));

        service.moveShoppingList(group, 3, 0);
        const a = http.expectOne(listReorderUrl);
        service.applyListOrder(listOrder(7, [2, 1, 3]));
        service.moveShoppingList(group, 1, 2);
        http.expectOne(listReorderUrl).flush('Server error', serverError);
        a.flush(listOrder(6, [3, 1, 2]));

        expect(listIds()).toEqual([2, 1, 3]);
        expect(alerts.showToast).toHaveBeenCalledOnceWith(toastKey);
        http.expectOne(loadUrl).flush(response([2, 1, 3], 7));
        expect(listIds()).toEqual([2, 1, 3]);
      });

      it('Review 2 F-002 (Einträge): dieselbe Folge für die Einträge einer Liste', () => {
        load(response([1], 5, [11, 12, 13], 2));

        service.moveShoppingItem(group, 1, 13, 0);
        const a = http.expectOne(itemReorderUrl);
        service.applyItemOrder(itemOrder(1, 4, [12, 11, 13]));
        service.moveShoppingItem(group, 1, 11, 2);
        http.expectOne(itemReorderUrl).flush('Server error', serverError);
        a.flush(itemOrder(1, 3, [13, 11, 12]));

        expect(itemIdsOf(1)).toEqual([12, 11, 13]);
        expect(alerts.showToast).toHaveBeenCalledOnceWith(toastKey);
        http.expectOne(loadUrl).flush(response([1], 5, [12, 11, 13], 4));
        expect(itemIdsOf(1)).toEqual([12, 11, 13]);
      });
    });

    describe('Reorder-Event vor Add-Event (Review 2 F-004)', () => {
      it('reorder-lists [2,1,3] vor add-list 2 bei lokal [1,3]: nach dem Add [2,1,3]', () => {
        load(response([1, 3], 5));

        service.applyListOrder(listOrder(6, [2, 1, 3]));
        expect(listIds()).toEqual([1, 3]);
        service.onListAdded(listDto({ id: 2, groupId: 7 }));

        expect(listIds()).toEqual([2, 1, 3]);
      });

      it('reorder-items [12,11,13] vor add-item 12 bei lokal [11,13]: nach dem Add [12,11,13]', () => {
        load(response([1], 5, [11, 13], 2));

        service.applyItemOrder(itemOrder(1, 3, [12, 11, 13]));
        service.onItemAdded(itemDto({ id: 12, shoppingListId: 1, groupId: 7 }));

        expect(itemIdsOf(1)).toEqual([12, 11, 13]);
      });

      it('reorder-items für eine noch unbekannte Liste: nach add-list und add-items gilt die bestätigte Reihenfolge', () => {
        load(response([1], 5));

        service.applyItemOrder(itemOrder(40, 1, [42, 41]));
        service.onListAdded(listDto({ id: 40, groupId: 7 }));
        service.onItemAdded(itemDto({ id: 41, shoppingListId: 40, groupId: 7 }));
        service.onItemAdded(itemDto({ id: 42, shoppingListId: 40, groupId: 7 }));

        expect(listIds()).toEqual([1, 40]);
        expect(itemIdsOf(40)).toEqual([42, 41]);
      });
    });

    describe('Fehlgeschlagene CRUD-Requests verwerfen nichts (E7, Review 2 F-003)', () => {
      it('Umbenennen scheitert nach einem bestätigten Reorder-Event: Event-Reihenfolge bleibt', () => {
        load(response([1, 2, 3], 5));

        service.updateShoppingList(listDto({ id: 1, name: 'Neu', groupId: 7 }));
        service.applyListOrder(listOrder(6, [3, 2, 1]));
        http.expectOne(`${api}/shopping-list/update`).flush(forbiddenBody, forbidden);

        expect(listIds()).toEqual([3, 2, 1]);
      });

      it('Eintrag ändern und Eintrag löschen scheitern nach einem Reorder-Event: Event-Reihenfolge bleibt', () => {
        load(response([1], 5, [11, 12, 13], 2));

        service.updateItemOfShoppingList(itemDto({ id: 11, name: 'Neu', shoppingListId: 1, groupId: 7 }));
        service.deleteItemFromShoppingList(itemDto({ id: 12, shoppingListId: 1, groupId: 7 }));
        service.applyItemOrder(itemOrder(1, 3, [13, 12, 11]));
        http.expectOne(`${api}/shopping-list/update-item`).flush(forbiddenBody, forbidden);
        http.expectOne(`${api}/shopping-list/delete-item`).flush(forbiddenBody, forbidden);

        expect(itemIdsOf(1)).toEqual([13, 12, 11]);
      });
    });

    describe('Rücksprung nach Neuanlagen (Review 3 F-001)', () => {
      it('Listen B und C per REST angelegt, Move scheitert: [A,B,C], auch wenn der Refetch scheitert', () => {
        load(response([1], 5));
        addList(2);
        addList(3);

        service.moveShoppingList(group, 3, 1);
        expect(listIds()).toEqual([1, 3, 2]);
        http.expectOne(listReorderUrl).flush('Server error', serverError);
        http.expectOne(loadUrl).flush('Server error', serverError);

        expect(listIds()).toEqual([1, 2, 3]);
      });

      it('Listen B und C per add-list-Event, Move scheitert: [A,B,C]', () => {
        load(response([1], 5));
        service.onListAdded(listDto({ id: 2, groupId: 7 }));
        service.onListAdded(listDto({ id: 3, groupId: 7 }));

        service.moveShoppingList(group, 3, 1);
        http.expectOne(listReorderUrl).flush('Server error', serverError);
        http.expectOne(loadUrl).flush('Server error', serverError);

        expect(listIds()).toEqual([1, 2, 3]);
      });

      it('Einträge per REST und Event angelegt, Move scheitert: Anlagereihenfolge', () => {
        load(response([1], 5, [11], 2));
        addItem(1, 12);
        service.onItemAdded(itemDto({ id: 13, shoppingListId: 1, groupId: 7 }));

        service.moveShoppingItem(group, 1, 13, 0);
        http.expectOne(itemReorderUrl).flush('Server error', serverError);
        http.expectOne(loadUrl).flush('Server error', serverError);

        expect(itemIdsOf(1)).toEqual([11, 12, 13]);
      });

      it('neu angelegte Liste: Einträge anlegen, Move scheitert, Rücksprung auf die Anlagereihenfolge', () => {
        load(response([1], 5));
        addList(2);
        addItem(2, 21);
        addItem(2, 22);

        service.moveShoppingItem(group, 2, 22, 0);
        expect(itemIdsOf(2)).toEqual([22, 21]);
        http.expectOne(itemReorderUrl).flush('Server error', serverError);
        http.expectOne(loadUrl).flush('Server error', serverError);

        expect(itemIdsOf(2)).toEqual([21, 22]);
      });

      it('Gruppe ohne Listen: das erste echte reorder-lists-Event (Revision 1) wird übernommen', () => {
        load([]);
        addList(1);
        addList(2);

        service.applyListOrder(listOrder(1, [2, 1]));

        expect(listIds()).toEqual([2, 1]);
      });
    });

    describe('Kontext-Generation (Review 3 F-002, Review 4 F-001)', () => {
      const switchTo = (target: typeof group) => {
        groupService.setActiveGroup(target);
        TestBed.flushEffects();
      };

      it('Gruppenwechsel während des PUT: Erfolg ändert die Listen der neuen Gruppe nicht', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        switchTo(andereGruppe);
        const listenDerNeuenGruppe = [aShoppingList({ id: 50 }), aShoppingList({ id: 51 })];
        service.getShoppingLists().set(listenDerNeuenGruppe);
        req.flush(listOrder(6, [3, 1, 2]));

        expect(service.getShoppingLists()()).toBe(listenDerNeuenGruppe);
      });

      it('Gruppenwechsel während des PUT: Fehler ohne Toast und ohne Refetch', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        switchTo(andereGruppe);
        req.flush('Server error', serverError);

        expect(alerts.showToast).not.toHaveBeenCalled();
        http.expectNone(() => true);
      });

      it('Logout und Login mit derselben Gruppe während des PUT: Fehler ohne Toast und ohne Refetch', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        user$.next(null);
        user$.next(aLoggedInUser());
        switchTo(group);
        req.flush('Server error', serverError);

        expect(alerts.showToast).not.toHaveBeenCalled();
        http.expectNone(() => true);
      });

      it('A → B → A während des PUT, ohne Laden dazwischen: die Antwort wird verworfen', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        switchTo(andereGruppe);
        switchTo(group);
        req.flush('Server error', serverError);

        expect(alerts.showToast).not.toHaveBeenCalled();
        http.expectNone(() => true);
      });

      it('Logout ohne anschließendes Laden während des PUT: weder Toast noch Refetch', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        user$.next(null);
        req.flush('Server error', serverError);

        expect(alerts.showToast).not.toHaveBeenCalled();
        http.expectNone(() => true);
      });

      it('Logout ohne anschließendes Laden während des GET: weder Inhalte noch Reihenfolge übernommen', () => {
        service.getShoppingListsWithItems(group);
        const req = http.expectOne(loadUrl);

        user$.next(null);
        req.flush(response([1, 2, 3], 5));

        expect(service.getShoppingLists()()).toBe(existing);
      });

      it('Pull-to-refresh derselben Gruppe während des PUT: offene Requests bleiben, die spätere Antwort greift', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        load(response([1, 2, 3], 5));
        req.flush('Server error', serverError);

        expect(alerts.showToast).toHaveBeenCalledOnceWith(toastKey);
        expect(listIds()).toEqual([1, 2, 3]);
        http.expectOne(loadUrl).flush(response([1, 2, 3], 5));
      });

      it('Pull-to-refresh während des PUT, danach Erfolg: die gespeicherte Reihenfolge wird angezeigt', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        load(response([1, 2, 3], 5));
        req.flush(listOrder(6, [3, 1, 2]));

        expect(listIds()).toEqual([3, 1, 2]);
      });

      it('verspäteter GET-Erfolg nach Gruppenwechsel: weder Inhalte noch Revisionen übernommen', () => {
        service.getShoppingListsWithItems(group);
        const req = http.expectOne(loadUrl);

        groupService.setActiveGroup(andereGruppe);
        req.flush(response([1, 2, 3], 9));

        expect(service.getShoppingLists()()).toBe(existing);
      });

      it('verspäteter GET-Fehler (nicht still) nach Gruppenwechsel: die Listen der neuen Gruppe bleiben', () => {
        service.getShoppingListsWithItems(group);
        const req = http.expectOne(loadUrl);

        groupService.setActiveGroup(andereGruppe);
        req.flush('Server error', serverError);

        expect(service.getShoppingLists()()).toBe(existing);
      });

      it('Kontextwechsel setzt den Order-Zustand zurück (danach greift auch eine niedrige Revision)', () => {
        load(response([1, 2, 3], 5));

        switchTo(andereGruppe);
        switchTo(group);
        service.applyListOrder(listOrder(1, [3, 2, 1]));

        expect(listIds()).toEqual([3, 2, 1]);
      });

      it('Umbenennung der aktiven Gruppe setzt den Order-Zustand nicht zurück', () => {
        load(response([1, 2, 3], 5));

        switchTo(aGroup({ id: 7, name: 'Neue WG' }));
        service.applyListOrder(listOrder(1, [3, 2, 1]));

        expect(listIds()).toEqual([1, 2, 3]);
      });
    });

    describe('Laden', () => {
      it('GET mit älterer Revision als ein bestätigtes Event: Reihenfolge des Events bleibt, Inhalte werden übernommen', () => {
        load(response([1, 2, 3], 5));
        service.applyListOrder(listOrder(7, [3, 1, 2]));

        const neu = response([1, 2, 3], 6);
        neu[0].name = 'Umbenannt';
        load(neu);

        expect(listIds()).toEqual([3, 1, 2]);
        expect(service.getShoppingLists()()[1].name).toBe('Umbenannt');
      });

      it('GET übernimmt die gespeicherte Reihenfolge samt Revision (gleiche Revision gilt)', () => {
        load(response([1, 2, 3], 5));
        load(response([2, 3, 1], 5));

        service.applyListOrder(listOrder(5, [1, 2, 3]));

        expect(listIds()).toEqual([2, 3, 1]);
      });
    });
  });
});
