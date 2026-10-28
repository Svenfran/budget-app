import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
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
import { ShoppingitemMovedDto } from '../model/shoppingitem-moved-dto';
import { deleteItem, updateItem } from '../shoppinglist-events';
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
  /** add-item legt an (201); 200 hieße „offener Eintrag gleichen Namens vorhanden“ (DIV-23) */
  const created = { status: 201, statusText: 'Created' };

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
      req.flush({ ...dto, id: 22 }, created);

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

    // Einträge können die Liste wechseln (DIV-2): Antworten und Events mit veralteter shoppingListId
    describe('nach einem Listenwechsel', () => {
      it('Anlegen: steht der Eintrag bei der POST-Antwort schon in einer anderen Liste, entsteht kein Duplikat', () => {
        const verschoben = aShoppingItem({ id: 22, name: 'Milch' });
        const lists = [{ ...existing[0], shoppingItems: [...existing[0].shoppingItems, verschoben] }, existing[1]];
        service.getShoppingLists().set(lists);

        service.addItemToShoppingList(itemDto({ shoppingListId: 20 }));
        http.expectOne(`${api}/shopping-list/add-item`).flush(itemDto({ id: 22, shoppingListId: 20 }), created);

        expect(service.getShoppingLists()()).toBe(lists);
      });

      it('add-item-Event für einen Eintrag, der schon in einer anderen Liste steht: keine Änderung', () => {
        const lists = service.getShoppingLists()();

        service.onItemAdded(itemDto({ id: 11, shoppingListId: 20 }));

        expect(service.getShoppingLists()()).toBe(lists);
      });

      it('Löschen mit veralteter shoppingListId entfernt den Eintrag aus der Liste, in der er steht', () => {
        service.deleteItemFromShoppingList(itemDto({ id: 21, shoppingListId: 10 }));
        http.expectOne(`${api}/shopping-list/delete-item`).flush(null);

        expect(service.getShoppingLists()()).toEqual([existing[0], { ...existing[1], shoppingItems: [] }]);
      });

      it('Erledigte löschen mit veralteter shoppingListId entfernt auch den Eintrag in der anderen Liste', () => {
        service.deleteAllCompletedShoppingItems([
          itemDto({ id: 12, completed: true, shoppingListId: 10 }),
          itemDto({ id: 21, completed: true, shoppingListId: 10 }),
        ]);
        http.expectOne(`${api}/shopping-list/delete-all-items`).flush(null);

        expect(service.getShoppingLists()()).toEqual([
          { ...existing[0], shoppingItems: [existing[0].shoppingItems[0], existing[0].shoppingItems[2]] },
          { ...existing[1], shoppingItems: [] },
        ]);
      });
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
      http.expectOne(`${api}/shopping-list/add-item`).flush(itemDto({ id, shoppingListId: listId, groupId: 7 }), created);
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

    describe('Zurückstellen während eines Drags (holdUpdates/releaseUpdates, Codex-Review F-002)', () => {
      it('GET- und REST-Antworten werden während des Holds zurückgestellt und beim Release in Eingangsreihenfolge angewandt', () => {
        load(response([1, 2, 3], 5));
        service.getShoppingListsWithItems(group, undefined, { silent: true });
        const get = http.expectOne(loadUrl);
        service.updateShoppingList(listDto({ id: 2, name: 'Umbenannt', groupId: 7 }));
        const update = http.expectOne(`${api}/shopping-list/update`);

        service.holdUpdates();
        get.flush(response([3, 2], 5));
        update.flush(listDto({ id: 2, name: 'Umbenannt', groupId: 7 }));
        expect(listIds()).toEqual([1, 2, 3]);

        service.releaseUpdates();

        expect(listIds()).toEqual([3, 2]);
        expect(service.getShoppingLists()()[1].name).toBe('Umbenannt');
      });

      it('ohne Hold wirken Änderungen sofort; ein Kontextwechsel während des Holds verwirft die zurückgestellten', () => {
        const applied: string[] = [];
        service.applyOrDefer(() => applied.push('sofort'));

        service.holdUpdates();
        service.applyOrDefer(() => applied.push('alte Gruppe'));
        groupService.setActiveGroup(andereGruppe);
        service.releaseUpdates();
        service.applyOrDefer(() => applied.push('danach'));

        expect(applied).toEqual(['sofort', 'danach']);
      });
    });

    describe('GET während eines offenen eigenen Reorders (Codex-Review F-001)', () => {
      const withItems = (lists: ShoppinglistDto[], listId: number, itemIds: number[]) =>
        lists.map(list => list.id === listId ? { ...list, shoppingItems: itemIds.map(id => aShoppingItem({ id })) } : list);

      it('Listen: die optimistische Reihenfolge bleibt vor Abschluss des PUT, eine neue Liste aus dem GET kommt ans Ende', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        load(response([1, 2, 3, 4], 5));

        expect(listIds()).toEqual([3, 1, 2, 4]);
        req.flush(listOrder(6, [3, 1, 2]));
        expect(listIds()).toEqual([3, 1, 2, 4]);
      });

      it('Listen: eine im GET gelöschte Liste verschwindet; scheitert danach der PUT, gilt der bestätigte Stand', () => {
        load(response([1, 2, 3], 5));
        service.moveShoppingList(group, 3, 0);
        const req = http.expectOne(listReorderUrl);

        load(response([1, 3], 5));
        expect(listIds()).toEqual([3, 1]);

        req.flush('Server error', serverError);
        expect(listIds()).toEqual([1, 3]);
        expect(alerts.showToast).toHaveBeenCalledOnceWith(toastKey);
        http.expectOne(loadUrl).flush(response([1, 3], 5));
      });

      it('Einträge: die optimistische Reihenfolge bleibt vor Abschluss des PUT, ein neuer Eintrag kommt ans Ende', () => {
        load(response([1, 2], 5, [11, 12, 13], 2));
        service.moveShoppingItem(group, 1, 13, 0);
        const req = http.expectOne(itemReorderUrl);

        load(withItems(response([1, 2], 5, [], 2), 1, [11, 12, 13, 14]));

        expect(itemIdsOf(1)).toEqual([13, 11, 12, 14]);
        req.flush(itemOrder(1, 3, [13, 11, 12]));
        expect(itemIdsOf(1)).toEqual([13, 11, 12, 14]);
      });

      it('nur der Bereich mit offenem Request bleibt optimistisch: die Listenreihenfolge folgt dem GET', () => {
        load(response([1, 2, 3], 5, [11, 12, 13], 2));
        service.moveShoppingItem(group, 1, 13, 0);
        const req = http.expectOne(itemReorderUrl);

        load(withItems(response([2, 1, 3], 6, [], 2), 1, [11, 12, 13]));

        expect(listIds()).toEqual([2, 1, 3]);
        expect(itemIdsOf(1)).toEqual([13, 11, 12]);
        req.flush(itemOrder(1, 3, [13, 11, 12]));
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

    // Verschieben zwischen Listen (DIV-2): Fälle entlang der Übergangstabelle M1–M11 im Plan (Abschnitt 7).
    // Zustand jeweils vor und nach dem flush.
    describe('Eintrag zwischen Listen verschieben (DIV-2)', () => {
      const moveUrl = `${api}/shopping-list/move-item`;
      const moveToast = 'alerts.shoppinglist.move.error';

      /** Listen in Reihenfolge der Schlüssel (IDs aufsteigend) mit Einträgen und optionalen Item-Revisionen. */
      const lists = (items: Record<number, number[]>, revisions: Record<number, number> = {}): ShoppinglistDto[] =>
        Object.entries(items).map(([id, itemIds]) => ({
          id: +id, name: `Liste ${id}`, listOrderRevision: 5, itemOrderRevision: revisions[+id] ?? 0,
          shoppingItems: itemIds.map(itemId => aShoppingItem({ id: itemId, name: `Artikel ${itemId}` })),
        }));
      /** Sichtbarer Zustand: Liste → IDs ihrer Einträge. */
      const state = () => service.getShoppingLists()().reduce<Record<number, number[]>>((result, list) =>
        ({ ...result, [list.id]: list.shoppingItems.map(item => item.id) }), {});
      const moved = (itemId: number, source: [number, number, number[]], target: [number, number, number[]]): ShoppingitemMovedDto => ({
        groupId: 7, shoppingItemId: itemId,
        source: itemOrder(source[0], source[1], source[2]), target: itemOrder(target[0], target[1], target[2]),
      });
      /** Stilles GET starten (Resync), die Antwort flusht der Test. */
      const startLoad = () => {
        service.getShoppingListsWithItems(group, undefined, { silent: true });
        return http.expectOne(loadUrl);
      };
      const switchTo = (target: typeof group) => {
        groupService.setActiveGroup(target);
        TestBed.flushEffects();
      };

      describe('eigener Move', () => {
        it('PUT mit Quelle, Ziel und Zielreihenfolge; sofort sichtbar; Erfolg übernimmt beide Revisionen', () => {
          load(lists({ 1: [11, 12], 2: [21, 22] }));
          const milch = service.getShoppingLists()()[0].shoppingItems[0];

          service.moveShoppingItemToList(group, 11, 1, 2, 1);
          const req = http.expectOne(moveUrl);

          expect(req.request.method).toBe('PUT');
          expect(req.request.body).toEqual({
            groupId: 7, shoppingItemId: 11, sourceShoppingListId: 1, targetShoppingListId: 2, targetShoppingItemIds: [21, 11, 22]
          });
          expect(state()).toEqual({ 1: [12], 2: [21, 11, 22] });
          expect(service.getShoppingLists()()[1].shoppingItems[1]).toBe(milch);
          expect(service.isMovePending(11)).toBeTrue();

          req.flush(moved(11, [1, 1, [12]], [2, 1, [21, 11, 22]]));
          expect(state()).toEqual({ 1: [12], 2: [21, 11, 22] });
          expect(service.isMovePending(11)).toBeFalse();

          // ein älteres reorder-items (Revision 0) ändert nichts mehr
          service.applyItemOrder(itemOrder(2, 0, [22, 21]));
          expect(state()).toEqual({ 1: [12], 2: [21, 11, 22] });
        });

        it('zweiter Move desselben Eintrags während des ersten und ungültige Moves: kein Request, keine Änderung', () => {
          load(lists({ 1: [11, 12], 2: [21], 3: [] }));
          service.moveShoppingItemToList(group, 11, 1, 2, 0);
          const req = http.expectOne(moveUrl);
          const vorher = state();

          service.moveShoppingItemToList(group, 11, 2, 3, 0);
          service.moveShoppingItemToList(group, 12, 1, 1, 0);
          service.moveShoppingItemToList(group, 21, 1, 3, 0);
          service.moveShoppingItemToList(group, 12, 1, 9, 0);

          http.expectNone(moveUrl);
          expect(state()).toEqual(vorher);
          req.flush(moved(11, [1, 1, [12]], [2, 1, [11, 21]]));
        });
      });

      describe('Fehler', () => {
        it('Toast, der Eintrag steht vor dem Refetch wieder an seinem Platz in der Quelle', () => {
          load(lists({ 1: [11, 12, 13], 2: [21] }));
          service.moveShoppingItemToList(group, 12, 1, 2, 0);
          expect(state()).toEqual({ 1: [11, 13], 2: [12, 21] });

          http.expectOne(moveUrl).flush('Server error', serverError);

          expect(state()).toEqual({ 1: [11, 12, 13], 2: [21] });
          expect(alerts.showToast).toHaveBeenCalledOnceWith(moveToast);
          expect(service.isMovePending(12)).toBeFalse();
          http.expectOne(loadUrl).flush(lists({ 1: [11, 12, 13], 2: [21] }));
          expect(state()).toEqual({ 1: [11, 12, 13], 2: [21] });
        });

        it('scheitert auch der Refetch, bleibt der Eintrag in der Quelle', () => {
          load(lists({ 1: [11, 12, 13], 2: [21] }));
          service.moveShoppingItemToList(group, 12, 1, 2, 1);

          http.expectOne(moveUrl).flush('Server error', serverError);
          http.expectOne(loadUrl).flush('Server error', serverError);

          expect(state()).toEqual({ 1: [11, 12, 13], 2: [21] });
        });

        it('fremdes move-item während des Requests (conflict), danach eigener Fehler: kein Rücksprung, Refetch klärt', () => {
          load(lists({ 1: [11, 12], 2: [21], 3: [] }));
          service.moveShoppingItemToList(group, 12, 1, 2, 1);
          const req = http.expectOne(moveUrl);

          service.applyItemMove(moved(12, [1, 1, [11]], [3, 1, [12]]));
          expect(state()).toEqual({ 1: [11], 2: [21, 12], 3: [] });

          req.flush('Not found', { status: 404, statusText: 'Not Found' });
          expect(itemIdsOf(1)).toEqual([11]);
          expect(alerts.showToast).toHaveBeenCalledOnceWith(moveToast);
          http.expectOne(loadUrl).flush(lists({ 1: [11], 2: [21], 3: [12] }, { 1: 1, 3: 1 }));

          expect(state()).toEqual({ 1: [11], 2: [21], 3: [12] });
        });

        it('fremdes move-item während des Requests (conflict), danach eigener Erfolg: stiller Refetch', () => {
          load(lists({ 1: [11, 12], 2: [21], 3: [] }));
          service.moveShoppingItemToList(group, 12, 1, 2, 1);
          const req = http.expectOne(moveUrl);
          service.applyItemMove(moved(12, [2, 2, [21]], [3, 1, [12]]));

          req.flush(moved(12, [1, 1, [11]], [2, 1, [21, 12]]));

          expect(alerts.showToast).not.toHaveBeenCalled();
          http.expectOne(loadUrl).flush(lists({ 1: [11], 2: [21], 3: [12] }, { 1: 1, 2: 2, 3: 1 }));
          expect(state()).toEqual({ 1: [11], 2: [21], 3: [12] });
        });

        it('Kontextwechsel während des Moves: Antwort verworfen, kein Toast, kein Refetch', () => {
          load(lists({ 1: [11], 2: [] }));
          service.moveShoppingItemToList(group, 11, 1, 2, 0);
          const req = http.expectOne(moveUrl);

          switchTo(andereGruppe);
          req.flush('Server error', serverError);

          expect(alerts.showToast).not.toHaveBeenCalled();
          expect(service.isMovePending(11)).toBeFalse();
        });
      });

      describe('Events', () => {
        it('reorder-items des Ziels (Revision 2, enthält X) vor move-item (Revision 1): X steht laut Revision 2 im Ziel', () => {
          load(lists({ 1: [11, 12], 2: [21, 22] }));

          service.applyItemOrder(itemOrder(2, 2, [21, 11, 22]));
          service.applyItemMove(moved(11, [1, 1, [12]], [2, 1, [11, 21, 22]]));

          expect(state()).toEqual({ 1: [12], 2: [21, 11, 22] });
        });

        it('vertauschte Events B→C vor A→B: Refetch, das ältere Event ist überholt, danach steht X in C', () => {
          load(lists({ 1: [11], 2: [], 3: [] }));

          service.applyItemMove(moved(11, [2, 2, []], [3, 1, [11]]));
          const refetch = http.expectOne(loadUrl);
          service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]]));
          expect(state()).toEqual({ 1: [11], 2: [], 3: [] });

          refetch.flush(lists({ 1: [], 2: [], 3: [11] }, { 1: 1, 2: 2, 3: 1 }));
          expect(state()).toEqual({ 1: [], 2: [], 3: [11] });
        });

        it('überholtes Event nach einem GET mit späterem Rück-Move (M5b): kein Move', () => {
          load(lists({ 1: [11], 2: [] }, { 1: 2, 2: 2 }));

          service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]]));

          expect(state()).toEqual({ 1: [11], 2: [] });
        });

        it('delete-item vor einem verspäteten move-item: X erscheint nicht wieder, Refetch', () => {
          load(lists({ 1: [11, 12], 2: [] }));
          service.getShoppingLists().update(current => deleteItem(current, itemDto({ id: 11, shoppingListId: 1, groupId: 7 })));

          service.applyItemMove(moved(11, [1, 1, [12]], [2, 1, [11]]));

          expect(state()).toEqual({ 1: [12], 2: [] });
          http.expectOne(loadUrl).flush(lists({ 1: [12], 2: [] }, { 1: 1, 2: 1 }));
          expect(state()).toEqual({ 1: [12], 2: [] });
        });

        it('update-item vor move-item: Name und Erledigt-Status bleiben erhalten', () => {
          load(lists({ 1: [11], 2: [] }));
          service.getShoppingLists().update(current =>
            updateItem(current, itemDto({ id: 11, name: 'Hafermilch', completed: true, shoppingListId: 1, groupId: 7 })));

          service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]]));

          expect(service.getShoppingLists()()[1].shoppingItems).toEqual([{ id: 11, name: 'Hafermilch', completed: true }]);
        });

        it('move-item vor add-list: Refetch; nach dessen Antwort steht X in der neuen Liste', () => {
          load(lists({ 1: [11] }));

          service.applyItemMove(moved(11, [1, 1, []], [5, 1, [11]]));
          expect(state()).toEqual({ 1: [11] });

          http.expectOne(loadUrl).flush(lists({ 1: [], 5: [11] }, { 1: 1, 5: 1 }));
          expect(state()).toEqual({ 1: [], 5: [11] });
        });

        it('move-item vor add-list und add-item (Eintrag lokal unbekannt): Refetch, X wird nicht aus dem Event erzeugt', () => {
          load(lists({ 1: [] }));

          service.applyItemMove(moved(11, [1, 1, []], [5, 1, [11]]));
          expect(state()).toEqual({ 1: [] });

          http.expectOne(loadUrl).flush(lists({ 1: [], 5: [11] }, { 1: 1, 5: 1 }));
          expect(state()).toEqual({ 1: [], 5: [11] });
        });

        it('move-item während holdUpdates wirkt erst nach releaseUpdates', () => {
          load(lists({ 1: [11], 2: [] }));

          service.holdUpdates();
          service.applyOrDefer(() => service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]])));
          expect(state()).toEqual({ 1: [11], 2: [] });

          service.releaseUpdates();
          expect(state()).toEqual({ 1: [], 2: [11] });
        });
      });

      describe('GET (F-002)', () => {
        it('GET gestartet, eigener Move bestätigt, alte GET-Antwort: X bleibt im Ziel', () => {
          load(lists({ 1: [11, 12], 2: [21] }));
          const get = startLoad();
          service.moveShoppingItemToList(group, 11, 1, 2, 0);
          http.expectOne(moveUrl).flush(moved(11, [1, 1, [12]], [2, 1, [11, 21]]));

          get.flush(lists({ 1: [11, 12], 2: [21] }));

          expect(state()).toEqual({ 1: [12], 2: [11, 21] });
          // die bestätigten Reihenfolgen stammen aus der neueren Revision: Revision 0 aus dem GET ändert nichts
          service.applyItemOrder(itemOrder(2, 1, [21, 11]));
          expect(state()).toEqual({ 1: [12], 2: [11, 21] });
        });

        it('GET gestartet, move-item-Event, alte GET-Antwort: X bleibt im Ziel', () => {
          load(lists({ 1: [11, 12], 2: [21] }));
          const get = startLoad();
          service.applyItemMove(moved(11, [1, 1, [12]], [2, 1, [21, 11]]));

          get.flush(lists({ 1: [11, 12], 2: [21] }));

          expect(state()).toEqual({ 1: [12], 2: [21, 11] });
        });

        it('GET während eines offenen eigenen Moves: X bleibt an der gewählten Position; scheitert der PUT, springt er zurück', () => {
          load(lists({ 1: [11, 12], 2: [21, 22] }));
          service.moveShoppingItemToList(group, 11, 1, 2, 1);
          const req = http.expectOne(moveUrl);

          load(lists({ 1: [11, 12], 2: [21, 22] }));
          expect(state()).toEqual({ 1: [12], 2: [21, 11, 22] });

          req.flush('Server error', serverError);
          expect(state()).toEqual({ 1: [11, 12], 2: [21, 22] });
          http.expectOne(loadUrl).flush(lists({ 1: [11, 12], 2: [21, 22] }));
        });

        it('nach Ende aller GETs wirkt ein bestätigter Move nicht mehr auf spätere Ladeantworten', () => {
          load(lists({ 1: [11], 2: [] }));
          const get = startLoad();
          service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]]));
          get.flush(lists({ 1: [], 2: [11] }, { 1: 1, 2: 1 }));

          // später zurückverschoben (z. B. vom eigenen Zweitgerät, ohne Event)
          load(lists({ 1: [11], 2: [] }, { 1: 2, 2: 2 }));

          expect(state()).toEqual({ 1: [11], 2: [] });
        });

        for (const ende of ['Erfolg', 'Fehler']) {
          it(`zwei parallele GETs mit gleichem Start, GET 1 endet mit ${ende}, dann die alte Antwort von GET 2: X bleibt im Ziel`, () => {
            load(lists({ 1: [11], 2: [] }));
            const get1 = startLoad();
            const get2 = startLoad();
            service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]]));

            if (ende === 'Erfolg') get1.flush(lists({ 1: [], 2: [11] }, { 1: 1, 2: 1 }));
            else get1.flush('Server error', serverError);
            get2.flush(lists({ 1: [11], 2: [] }));
            expect(state()).toEqual({ 1: [], 2: [11] });

            // danach ist der Log leer: ein späteres GET mit X in A wird übernommen
            load(lists({ 1: [11], 2: [] }, { 1: 2, 2: 2 }));
            expect(state()).toEqual({ 1: [11], 2: [] });
          });
        }

        it('M6 mit laufendem GET: A→B (neuer) vor B→A (älter), dann GET mit Zwischenstand X in A: X steht in B', () => {
          load(lists({ 1: [], 2: [11] }, { 1: 1, 2: 1 }));
          const get = startLoad();

          service.applyItemMove(moved(11, [1, 3, []], [2, 3, [11]]));
          service.applyItemMove(moved(11, [2, 2, []], [1, 2, [11]]));
          get.flush(lists({ 1: [11], 2: [] }, { 1: 2, 2: 2 }));

          expect(state()).toEqual({ 1: [], 2: [11] });
        });

        it('Grenze der Nachspielung: GET mit späterem Rück-Move verschiebt vorübergehend falsch, dessen Event korrigiert', () => {
          load(lists({ 1: [11], 2: [] }));
          const get = startLoad();
          service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]]));

          get.flush(lists({ 1: [11], 2: [] }, { 1: 2, 2: 2 }));
          expect(state()).toEqual({ 1: [], 2: [11] });

          service.applyItemMove(moved(11, [2, 2, []], [1, 2, [11]]));
          expect(state()).toEqual({ 1: [11], 2: [] });
        });

        it('Kontextwechsel leert offene Moves und Log: nach Rückkehr wird eine Ladeantwort unverändert übernommen', () => {
          load(lists({ 1: [11], 2: [] }));
          const get = startLoad();
          service.applyItemMove(moved(11, [1, 1, []], [2, 1, [11]]));
          service.moveShoppingItemToList(group, 11, 2, 1, 0);
          const req = http.expectOne(moveUrl);

          switchTo(andereGruppe);
          switchTo(group);
          expect(service.isMovePending(11)).toBeFalse();
          get.flush(lists({ 1: [11], 2: [] }));
          req.flush(moved(11, [2, 2, []], [1, 2, [11]]));
          load(lists({ 1: [11], 2: [] }));

          expect(state()).toEqual({ 1: [11], 2: [] });
        });
      });
    });
  });

  describe('Vorschläge und mehrere Einträge (DIV-23)', () => {
    const wg = aGroup({ id: 1, name: 'WG' });
    const andereGruppe = aGroup({ id: 2, name: 'Urlaub' });
    const switchTo = (target: typeof wg) => {
      groupService.setActiveGroup(target);
      TestBed.flushEffects();
    };
    const itemsOf = (listId: number) =>
      service.getShoppingLists()().find(list => list.id === listId)!.shoppingItems.map(item => item.id);
    const addItemsUrl = `${api}/shopping-list/add-items`;

    beforeEach(() => switchTo(wg));

    describe('Eintrag anlegen: Duplikat (200)', () => {
      it('200 mit dem vorhandenen Eintrag: Hinweis-Toast mit dem Namen, kein zweiter Eintrag', () => {
        const instant = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();

        service.addItemToShoppingList(itemDto({ name: 'milch', shoppingListId: 10, groupId: 1 }));
        http.expectOne(`${api}/shopping-list/add-item`)
          .flush(itemDto({ id: 11, name: 'Milch', shoppingListId: 10, groupId: 1 }), { status: 200, statusText: 'OK' });

        expect(itemsOf(10)).toEqual([11, 12, 13]);
        expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.item.duplicate');
        expect(instant).toHaveBeenCalledWith('alerts.shoppinglist.item.duplicate', { itemName: 'Milch' });
      });

      it('201: Eintrag angehängt, kein Toast', () => {
        service.addItemToShoppingList(itemDto({ shoppingListId: 10, groupId: 1 }));
        http.expectOne(`${api}/shopping-list/add-item`).flush(itemDto({ id: 14, shoppingListId: 10, groupId: 1 }), created);

        expect(itemsOf(10)).toEqual([11, 12, 13, 14]);
        expect(alerts.showToast).not.toHaveBeenCalled();
      });

      it('200 nach einem Gruppenwechsel: kein Toast, keine Änderung', () => {
        service.addItemToShoppingList(itemDto({ shoppingListId: 10, groupId: 1 }));
        const req = http.expectOne(`${api}/shopping-list/add-item`);

        switchTo(andereGruppe);
        req.flush(itemDto({ id: 11, shoppingListId: 10, groupId: 1 }), { status: 200, statusText: 'OK' });

        expect(alerts.showToast).not.toHaveBeenCalled();
        expect(service.getShoppingLists()()).toBe(existing);
      });
    });

    it('lädt die Vorschläge einer Liste per GET', () => {
      const vorschlaege = { groupId: 1, shoppingListId: 10, defaultSuggestions: false, names: ['Brot', 'Kaffee'] };
      let geliefert: unknown;

      service.getSuggestions(1, 10).subscribe(result => geliefert = result);
      const req = http.expectOne(`${api}/shopping-list/suggestions/1/10`);
      expect(req.request.method).toBe('GET');
      req.flush(vorschlaege);

      expect(geliefert).toEqual(vorschlaege);
    });

    describe('mehrere Einträge anlegen', () => {
      const dto = { groupId: 1, shoppingListId: 20, names: ['Milch', 'Brot', 'Eier'] };
      const angelegt = (id: number, name: string) => itemDto({ id, name, shoppingListId: 20, groupId: 1 });

      it('POST mit den ausgewählten Namen; nur die gelieferten Einträge werden in Antwortreihenfolge angehängt', () => {
        service.addItemsToShoppingList(dto);
        const req = http.expectOne(addItemsUrl);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(dto);
        req.flush([angelegt(23, 'Brot'), angelegt(22, 'Eier')], created);

        expect(itemsOf(20)).toEqual([21, 23, 22]);
        expect(service.getShoppingLists()()[1].shoppingItems.slice(1)).toEqual([
          { id: 23, name: 'Brot', completed: false }, { id: 22, name: 'Eier', completed: false },
        ]);
        expect(service.getShoppingLists()()[0]).toBe(existing[0]);
        expect(alerts.showToast).not.toHaveBeenCalled();
      });

      it('Fehler: Listen unverändert, auch mit inzwischen eingetroffenen Änderungen, und Toast', () => {
        service.addItemsToShoppingList(dto);
        const req = http.expectOne(addItemsUrl);
        service.onItemAdded(itemDto({ id: 30, name: 'Kaffee', shoppingListId: 20, groupId: 1 }));
        const zwischenstand = service.getShoppingLists()();

        req.flush(forbiddenBody, forbidden);

        expect(service.getShoppingLists()()).toBe(zwischenstand);
        expect(itemsOf(20)).toEqual([21, 30]);
        expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.save.error');
      });

      it('Antwort nach einem Gruppenwechsel wird verworfen', () => {
        service.addItemsToShoppingList(dto);
        const req = http.expectOne(addItemsUrl);

        switchTo(andereGruppe);
        const listenDerNeuenGruppe = [aShoppingList({ id: 50 })];
        service.getShoppingLists().set(listenDerNeuenGruppe);
        req.flush([angelegt(23, 'Brot')], created);

        expect(service.getShoppingLists()()).toBe(listenDerNeuenGruppe);
      });

      it('Antwort während eines Drags wird zurückgestellt und beim Release angewandt', () => {
        service.addItemsToShoppingList(dto);
        const req = http.expectOne(addItemsUrl);

        service.holdUpdates();
        req.flush([angelegt(23, 'Brot')], created);
        expect(itemsOf(20)).toEqual([21]);

        service.releaseUpdates();
        expect(itemsOf(20)).toEqual([21, 23]);
      });
    });

    describe('Kontext des Vorschlagsdialogs', () => {
      it('gilt, solange Gruppe, Sitzung und Liste unverändert sind', () => {
        const inContext = service.captureSuggestionContext(10);

        expect(inContext()).toBeTrue();
      });

      it('ungültig nach einem Gruppenwechsel, auch zurück zur ersten Gruppe', () => {
        const inContext = service.captureSuggestionContext(10);

        switchTo(andereGruppe);
        expect(inContext()).toBeFalse();
        switchTo(wg);
        expect(inContext()).toBeFalse();
      });

      it('ungültig nach Logout und Login mit derselben Gruppe', () => {
        const inContext = service.captureSuggestionContext(10);

        user$.next(null);
        user$.next(aLoggedInUser());
        switchTo(wg);

        expect(inContext()).toBeFalse();
      });

      it('ungültig, wenn die Liste inzwischen gelöscht wurde', () => {
        const inContext = service.captureSuggestionContext(10);

        service.getShoppingLists().set([existing[1]]);

        expect(inContext()).toBeFalse();
      });
    });
  });
});
