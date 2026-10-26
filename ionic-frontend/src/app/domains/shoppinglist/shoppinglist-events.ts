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
