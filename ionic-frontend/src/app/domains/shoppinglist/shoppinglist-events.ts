import { AddEditShoppingItemDto } from './model/add-edit-shopping-item-dto';
import { AddEditShoppinglistDto } from './model/add-edit-shoppinglist-dto';
import { ShoppingitemDto } from './model/shoppingitem-dto';
import { ShoppinglistDto } from './model/shoppinglist-dto';

/*
 * Zustandsänderungen der Einkaufslisten durch WebSocket-Events (DIV-36), als pure Funktionen (lists, data) => lists.
 * Unbekannte Listen/Einträge ändern nichts; `add*` sind idempotent, damit sich ein Event und ein gleichzeitiger
 * Refetch nicht zu Duplikaten addieren.
 */

export function addList(lists: ShoppinglistDto[], data: AddEditShoppinglistDto): ShoppinglistDto[] {
  if (lists.some(list => list.id === data.id)) return lists;
  return [...lists, { id: data.id!, name: data.name, shoppingItems: [] }];
}

export function updateList(lists: ShoppinglistDto[], data: AddEditShoppinglistDto): ShoppinglistDto[] {
  return lists.map(list => list.id === data.id ? { ...list, name: data.name } : list);
}

export function deleteList(lists: ShoppinglistDto[], data: AddEditShoppinglistDto): ShoppinglistDto[] {
  return lists.filter(list => list.id !== data.id);
}

export function addItem(lists: ShoppinglistDto[], data: AddEditShoppingItemDto): ShoppinglistDto[] {
  const target = lists.find(list => list.id === data.shoppingListId);
  if (!target || target.shoppingItems.some(item => item.id === data.id)) return lists;
  const newItem = toItem(data);
  return lists.map(list => list === target ? { ...list, shoppingItems: [...list.shoppingItems, newItem] } : list);
}

export function updateItem(lists: ShoppinglistDto[], data: AddEditShoppingItemDto): ShoppinglistDto[] {
  const updatedItem = toItem(data);
  return lists.map(list =>
    list.shoppingItems.some(item => item.id === updatedItem.id)
      ? { ...list, shoppingItems: list.shoppingItems.map(item => item.id === updatedItem.id ? updatedItem : item) }
      : list
  );
}

export function deleteItem(lists: ShoppinglistDto[], data: AddEditShoppingItemDto): ShoppinglistDto[] {
  return lists.map(list =>
    list.id === data.shoppingListId
      ? { ...list, shoppingItems: list.shoppingItems.filter(item => item.id !== data.id) }
      : list
  );
}

export function deleteAllItems(lists: ShoppinglistDto[], data: AddEditShoppingItemDto[]): ShoppinglistDto[] {
  if (data.length === 0) return lists;
  const listId = data[0].shoppingListId;
  const deletedIds = new Set(data.map(item => item.id));
  return lists.map(list =>
    list.id === listId
      ? { ...list, shoppingItems: list.shoppingItems.filter(item => !deletedIds.has(item.id)) }
      : list
  );
}

function toItem(data: AddEditShoppingItemDto): ShoppingitemDto {
  return { id: data.id!, name: data.name, completed: data.completed };
}

/*
 * Reihenfolge (DIV-1): sortiert tolerant nach einer ID-Reihenfolge. Unbekannte IDs werden ignoriert, lokal vorhandene,
 * aber nicht enthaltene Einträge folgen in ihrer bisherigen Reihenfolge. Ändert sich nichts, kommt dasselbe Array
 * zurück (kein unnötiges Signal-Update).
 */

export function reorderLists(lists: ShoppinglistDto[], ids: number[]): ShoppinglistDto[] {
  return orderByIds(lists, ids);
}

export function reorderItems(lists: ShoppinglistDto[], listId: number, ids: number[]): ShoppinglistDto[] {
  const target = lists.find(list => list.id === listId);
  if (!target) return lists;
  const shoppingItems = orderByIds(target.shoppingItems, ids);
  if (shoppingItems === target.shoppingItems) return lists;
  return lists.map(list => list === target ? { ...list, shoppingItems } : list);
}

function orderByIds<T extends { id: number }>(entries: T[], ids: number[]): T[] {
  const position = new Map<number, number>();
  ids.forEach(id => { if (!position.has(id)) position.set(id, position.size); });
  const sorted = entries
    .map((entry, index) => ({ entry, rank: position.get(entry.id) ?? position.size + index }))
    .sort((a, b) => a.rank - b.rank)
    .map(({ entry }) => entry);
  return sorted.every((entry, index) => entry === entries[index]) ? entries : sorted;
}
