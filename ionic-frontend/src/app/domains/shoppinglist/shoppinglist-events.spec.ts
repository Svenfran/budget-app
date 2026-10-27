import { aShoppingItem, aShoppingList } from 'src/testing/test-data';
import { AddEditShoppingItemDto } from './model/add-edit-shopping-item-dto';
import { AddEditShoppinglistDto } from './model/add-edit-shoppinglist-dto';
import { ShoppinglistDto } from './model/shoppinglist-dto';
import {
  addItem, addList, deleteAllItems, deleteItem, deleteList, reorderItems, reorderLists, updateItem, updateList
} from './shoppinglist-events';

describe('shoppinglist-events', () => {
  const GROUP_ID = 1;
  const listEvent = (overrides: Partial<AddEditShoppinglistDto>): AddEditShoppinglistDto =>
    ({ id: 100, name: 'Neue Liste', groupId: GROUP_ID, ...overrides });
  const itemEvent = (list: ShoppinglistDto, overrides: Partial<AddEditShoppingItemDto>): AddEditShoppingItemDto =>
    ({ id: 200, name: 'Brot', completed: false, shoppingListId: list.id, groupId: GROUP_ID, ...overrides });
  const itemIds = (list: ShoppinglistDto) => list.shoppingItems.map(item => item.id);

  describe('Listen', () => {
    it('addList hängt eine neue, leere Liste an', () => {
      const wocheneinkauf = aShoppingList();

      const result = addList([wocheneinkauf], listEvent({ id: 100, name: 'Baumarkt' }));

      expect(result).toEqual([wocheneinkauf, { id: 100, name: 'Baumarkt', shoppingItems: [] }]);
    });

    it('addList ist idempotent: dieselbe Liste doppelt (Event + Refetch) ergibt kein Duplikat', () => {
      const lists = addList([], listEvent({ id: 100 }));

      const result = addList(lists, listEvent({ id: 100 }));

      expect(result.map(list => list.id)).toEqual([100]);
    });

    it('updateList ändert nur den Namen der betroffenen Liste, die Einträge bleiben', () => {
      const milch = aShoppingItem();
      const liste = aShoppingList({ name: 'Alt', shoppingItems: [milch] });
      const andere = aShoppingList({ name: 'Andere' });

      const result = updateList([liste, andere], listEvent({ id: liste.id, name: 'Neu' }));

      expect(result).toEqual([{ ...liste, name: 'Neu' }, andere]);
    });

    it('deleteList entfernt nur die betroffene Liste; eine unbekannte Liste ändert nichts', () => {
      const liste = aShoppingList();
      const andere = aShoppingList();

      expect(deleteList([liste, andere], listEvent({ id: liste.id }))).toEqual([andere]);
      expect(deleteList([liste, andere], listEvent({ id: 999 }))).toEqual([liste, andere]);
    });
  });

  describe('Einträge', () => {
    it('addItem hängt den Eintrag an die richtige Liste an', () => {
      const liste = aShoppingList();
      const andere = aShoppingList();

      const result = addItem([liste, andere], itemEvent(liste, { id: 200, name: 'Brot' }));

      expect(result[0].shoppingItems).toEqual([{ id: 200, name: 'Brot', completed: false }]);
      expect(result[1]).toBe(andere);
    });

    it('addItem ist idempotent: derselbe Eintrag doppelt ergibt kein Duplikat', () => {
      const liste = aShoppingList();
      const lists = addItem([liste], itemEvent(liste, { id: 200 }));

      const result = addItem(lists, itemEvent(liste, { id: 200 }));

      expect(itemIds(result[0])).toEqual([200]);
    });

    it('addItem für eine unbekannte Liste ändert nichts und wirft nicht', () => {
      const liste = aShoppingList();
      const lists = [liste];

      const result = addItem(lists, { ...itemEvent(liste, {}), shoppingListId: 999 });

      expect(result).toBe(lists);
    });

    it('updateItem ersetzt den Eintrag in seiner Liste, andere Einträge bleiben', () => {
      const brot = aShoppingItem({ name: 'Brot' });
      const milch = aShoppingItem({ name: 'Milch' });
      const liste = aShoppingList({ shoppingItems: [brot, milch] });

      const result = updateItem([liste], itemEvent(liste, { id: brot.id, name: 'Vollkornbrot', completed: true }));

      expect(result[0].shoppingItems).toEqual([{ id: brot.id, name: 'Vollkornbrot', completed: true }, milch]);
    });

    it('deleteItem entfernt nur den betroffenen Eintrag', () => {
      const brot = aShoppingItem();
      const milch = aShoppingItem();
      const liste = aShoppingList({ shoppingItems: [brot, milch] });

      const result = deleteItem([liste], itemEvent(liste, { id: brot.id }));

      expect(itemIds(result[0])).toEqual([milch.id]);
    });

    it('deleteAllItems entfernt die gelöschten Einträge der Liste', () => {
      const brot = aShoppingItem({ completed: true });
      const milch = aShoppingItem({ completed: true });
      const eier = aShoppingItem();
      const liste = aShoppingList({ shoppingItems: [brot, milch, eier] });

      const result = deleteAllItems([liste], [itemEvent(liste, { id: brot.id }), itemEvent(liste, { id: milch.id })]);

      expect(itemIds(result[0])).toEqual([eier.id]);
    });

    it('deleteAllItems mit leerem Array ändert nichts und wirft nicht', () => {
      const lists = [aShoppingList({ shoppingItems: [aShoppingItem()] })];

      expect(deleteAllItems(lists, [])).toBe(lists);
    });
  });

  describe('Reihenfolge (DIV-1)', () => {
    const listIds = (lists: ShoppinglistDto[]) => lists.map(list => list.id);
    const [a, b, c] = [aShoppingList({ id: 1 }), aShoppingList({ id: 2 }), aShoppingList({ id: 3 })];

    it('reorderLists sortiert exakt nach der ID-Reihenfolge', () => {
      expect(listIds(reorderLists([a, b, c], [3, 1, 2]))).toEqual([3, 1, 2]);
    });

    it('reorderLists ignoriert unbekannte und doppelte IDs, lokal fehlende kommen in alter Reihenfolge ans Ende', () => {
      expect(listIds(reorderLists([a, b, c], [99, 3, 3]))).toEqual([3, 1, 2]);
    });

    it('reorderLists gibt dasselbe Array zurück, wenn sich nichts ändert', () => {
      const lists = [a, b, c];

      expect(reorderLists(lists, [1, 2, 3])).toBe(lists);
      expect(reorderLists(lists, [])).toBe(lists);
    });

    it('reorderItems sortiert nur die Einträge der Zielliste, andere Listen bleiben dieselben Objekte', () => {
      const [x, y, z] = [aShoppingItem({ id: 11 }), aShoppingItem({ id: 12 }), aShoppingItem({ id: 13 })];
      const liste = aShoppingList({ id: 10, shoppingItems: [x, y, z] });
      const andere = aShoppingList({ id: 20, shoppingItems: [aShoppingItem({ id: 21 }), aShoppingItem({ id: 22 })] });

      const result = reorderItems([liste, andere], 10, [13, 99, 11]);

      expect(itemIds(result[0])).toEqual([13, 11, 12]);
      expect(result[1]).toBe(andere);
    });

    it('reorderItems mit unbekannter Liste oder unveränderter Reihenfolge gibt dasselbe Array zurück', () => {
      const liste = aShoppingList({ id: 10, shoppingItems: [aShoppingItem({ id: 11 }), aShoppingItem({ id: 12 })] });
      const lists = [liste];

      expect(reorderItems(lists, 99, [12, 11])).toBe(lists);
      expect(reorderItems(lists, 10, [11, 12])).toBe(lists);
    });
  });
});
