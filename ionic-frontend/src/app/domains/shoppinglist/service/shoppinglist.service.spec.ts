import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Init } from 'src/app/constants/default-values';
import { environment } from 'src/environments/environment';
import { aGroup, aShoppingItem, aShoppingList } from 'src/testing/test-data';
import { provideServiceTesting } from 'src/testing/test-providers';
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
  const api = `${environment.apiBaseUrl}/api/groups`;
  const forbidden = { status: 403, statusText: 'Forbidden' };
  const forbiddenBody = 'User with ID 5 is either a member nor the owner of the group';

  const listDto = (overrides: Partial<AddEditShoppinglistDto> = {}): AddEditShoppinglistDto =>
    ({ id: null, name: 'Baumarkt', groupId: 1, ...overrides });
  const itemDto = (overrides: Partial<AddEditShoppingItemDto> = {}): AddEditShoppingItemDto =>
    ({ id: null, name: 'Milch', completed: false, shoppingListId: 10, groupId: 1, ...overrides });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideServiceTesting() });
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
    const url = `${api}/shopping-lists-with-items/7?requestTimeStamp=${new Date('1900-01-01').getTime()}`;

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
      http.expectOne(`${api}/shopping-lists-with-items/8?requestTimeStamp=${new Date('1900-01-01').getTime()}`).flush(listenVonB);
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

    it('lässt die Listen unverändert, wenn das Anlegen fehlschlägt', () => {
      service.addShoppingList(listDto());
      http.expectOne(`${api}/shopping-list/add`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
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

    it('lässt die Listen unverändert, wenn das Umbenennen fehlschlägt', () => {
      service.updateShoppingList(listDto({ id: 10, name: 'Wochenmarkt' }));
      http.expectOne(`${api}/shopping-list/update`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
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

    it('lässt die Listen unverändert, wenn das Löschen fehlschlägt', () => {
      service.deleteShoppingList(listDto({ id: 10, name: 'Supermarkt' }));
      http.expectOne(`${api}/shopping-list/delete`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
    });
  });

  describe('Einträge', () => {
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

    it('lässt die Listen unverändert, wenn das Anlegen eines Eintrags fehlschlägt', () => {
      service.addItemToShoppingList(itemDto({ shoppingListId: 20 }));
      http.expectOne(`${api}/shopping-list/add-item`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
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

    it('lässt die Listen unverändert, wenn das Ändern eines Eintrags fehlschlägt', () => {
      service.updateItemOfShoppingList(itemDto({ id: 11, name: 'Hafermilch', shoppingListId: 10 }));
      http.expectOne(`${api}/shopping-list/update-item`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
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

    it('lässt die Listen unverändert, wenn das Löschen eines Eintrags fehlschlägt', () => {
      service.deleteItemFromShoppingList(itemDto({ id: 11, shoppingListId: 10 }));
      http.expectOne(`${api}/shopping-list/delete-item`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
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

    it('lässt die Listen unverändert, wenn das Löschen der erledigten Einträge fehlschlägt', () => {
      service.deleteAllCompletedShoppingItems([itemDto({ id: 12, completed: true, shoppingListId: 10 })]);
      http.expectOne(`${api}/shopping-list/delete-all-items`).flush(forbiddenBody, forbidden);

      expect(service.getShoppingLists()()).toEqual(existing);
    });
  });
});
