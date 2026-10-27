import { HttpClient } from '@angular/common/http';
import { effect, Injectable, signal, untracked, WritableSignal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { environment } from 'src/environments/environment';
import { AddEditShoppinglistDto } from '../model/add-edit-shoppinglist-dto';
import { ShoppinglistDto } from '../model/shoppinglist-dto';
import { AddEditShoppingItemDto } from '../model/add-edit-shopping-item-dto';
import { ShoppingitemDto } from '../model/shoppingitem-dto';
import { ShoppinglistOrderDto } from '../model/shoppinglist-order-dto';
import { ShoppingitemOrderDto } from '../model/shoppingitem-order-dto';
import { INIT_VALUES } from 'src/app/constants/default-values';
import { Group } from 'src/app/model/group';
import { LoadOptions } from 'src/app/model/load-options';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { addItem, addList, reorderItems, reorderLists } from '../shoppinglist-events';
import {
  accept, acceptFromLoad, begin, confirmAdded, emptyScope, finish, OrderScope, UNKNOWN_REVISION
} from '../shoppinglist-order';

@Injectable({
  providedIn: 'root'
})
export class ShoppinglistService {

  private shoppingLists: WritableSignal<ShoppinglistDto[]> = signal<ShoppinglistDto[]>([]);
  private apiBaseUrl = environment.apiBaseUrl;
  private shoppingListsWithItemsUrl = `${this.apiBaseUrl}/api/groups/shopping-lists-with-items`;
  private addShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/add`;
  private deleteShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/delete`;
  private updateShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/update`;
  private reorderShoppingListsUrl = `${this.apiBaseUrl}/api/groups/shopping-list/reorder`;

  private addItemToShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/add-item`;
  private updateItemOfShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/update-item`;
  private deleteItemFromShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/delete-item`;
  private deleteAllItemsFromShoppingListUrl = `${this.apiBaseUrl}/api/groups/shopping-list/delete-all-items`;
  private reorderShoppingItemsUrl = `${this.apiBaseUrl}/api/groups/shopping-list/reorder-items`;

  // Reihenfolge (DIV-1): bestätigter Stand und offene Requests je Bereich, siehe shoppinglist-order.ts
  private listScope: OrderScope = emptyScope(UNKNOWN_REVISION);
  private itemScopes = new Map<number, OrderScope>();
  /**
   * Kontext-Generation: steigt bei jedem Wechsel von aktiver Gruppe oder Anmelde-Sitzung (auch Logout ohne
   * anschließendes Laden). Asynchrone Callbacks, die in einer älteren Generation gestartet wurden, werden verworfen.
   */
  private generation = 0;
  private contextGroupId: number;
  private contextSession: number;

  constructor(
    private http: HttpClient,
    private groupService: GroupService,
    private alertService: AlertService,
    private translate: TranslateService
  ) {
    this.contextGroupId = this.groupService.activeGroupId();
    this.contextSession = this.groupService.session();
    effect(() => {
      this.groupService.activeGroupId();
      this.groupService.session();
      untracked(() => this.syncContext());
    });
  }

  public getShoppingLists() {
    return this.shoppingLists;
  }

  /** Kontext-Generation für den Drag-Puffer der Seite (Phase 4). */
  public currentGeneration(): number {
    this.syncContext();
    return this.generation;
  }

  getShoppingListsWithItems(group: Group, onComplete?: () => void, options: LoadOptions = {}): void{
    if (group.flag?.includes(INIT_VALUES.DEFAULT)) {
      this.shoppingLists.set([]);
      onComplete?.();
      return;
    };

    const inContext = this.captureContext();
    this.http
      .get<ShoppinglistDto[]>(`${this.shoppingListsWithItemsUrl}/${group.id}`)
      .subscribe({
        next: (result) => {
          // überholt (z. B. inzwischen andere Gruppe aktiv): die Listen der aktuellen Gruppe nicht überschreiben
          if (inContext() && options.isCurrent?.() !== false) {
            const visible = this.shoppingLists();
            this.shoppingLists.set(result || []);
            this.acceptLoadedOrder(result || [], visible);
          }
          onComplete?.();
        },
        error: (err) => {
          if (options.silent) {
            console.warn('Silent resync of shoppinglists failed:', err);
            onComplete?.();
            return;
          }
          console.error('Error fetching shoppinglists:', err);
          if (inContext()) this.shoppingLists.set([]);
          onComplete?.();
        },
      });
  }

  addShoppingList(newShoppingList: AddEditShoppinglistDto): void {
    const inContext = this.captureContext();
    this.http
      .post<AddEditShoppinglistDto>(this.addShoppingListUrl, newShoppingList)
      .subscribe({
        next: (result) => {
          if (!inContext()) return;
          const newList: ShoppinglistDto = {
              id: result.id!,
              name: result.name,
              shoppingItems: []
          }
          this.shoppingLists.update(items => [...items, newList]);
          this.confirmListAdded(newList.id);
        },
        error: (err) => {
          console.error('Error adding shopping list:', err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.save.error'));
        },
    });
  }

  updateShoppingList(updateShoppingList: AddEditShoppinglistDto): void {
    this.http
      .put<AddEditShoppinglistDto>(this.updateShoppingListUrl, updateShoppingList)
      .subscribe({
        next: (result) => {
          this.shoppingLists.update(items => items.map(list =>
            list.id === result.id ? {...list, name: result.name} : list
          ))
        },
        error: (err) => {
          console.error("Error updating shopping list:", err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.save.error'));
        }
      })
  }

  deleteShoppingList(deleteShoppingList: AddEditShoppinglistDto): void {
    this.http
      .post<void>(this.deleteShoppingListUrl, deleteShoppingList)
      .subscribe({
        next: () => {
          this.shoppingLists.update(items => items.filter(list =>
            list.id !== deleteShoppingList.id
          ))
          this.removeItemScope(deleteShoppingList.id!);
        },
        error: (err) => {
          console.error('Error deleting shopping list:', err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.save.error'));
        }
      });
  }

  /**
   * Verschiebt eine Liste optimistisch an `toIndex` und speichert die Reihenfolge. Bei einem Fehler springt die
   * Anzeige auf die bestätigte Reihenfolge zurück (Toast, danach stiller Refetch).
   */
  moveShoppingList(group: Group, listId: number, toIndex: number): void {
    this.syncContext();
    const ids = moveId(this.shoppingLists().map(list => list.id), listId, toIndex);
    if (!ids) return;

    const inContext = this.captureContext();
    this.listScope = begin(withFallback(this.listScope, this.shoppingLists().map(list => list.id)));
    this.shoppingLists.update(lists => reorderLists(lists, ids));
    const body: ShoppinglistOrderDto = { groupId: group.id, revision: this.listScope.confirmed.revision, shoppingListIds: ids };
    this.http
      .put<ShoppinglistOrderDto>(this.reorderShoppingListsUrl, body)
      .subscribe({
        next: (result) => {
          if (!inContext()) return;
          this.listScope = accept(this.listScope, result.revision, result.shoppingListIds);
          this.finishListMove(group, false);
        },
        error: (err) => {
          if (!inContext()) return;
          console.error('Error reordering shopping lists:', err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.reorder.error'));
          this.finishListMove(group, true);
        }
      });
  }

  /** Bestätigte Listenreihenfolge (WebSocket `reorder-lists`): übernimmt sie, wenn sie neuer ist. */
  applyListOrder(data: ShoppinglistOrderDto): void {
    this.syncContext();
    this.listScope = accept(this.listScope, data.revision, data.shoppingListIds);
    this.reconcileLists();
  }

  /** WebSocket `add-list`: Liste anhängen (idempotent) und in die bestätigte Reihenfolge aufnehmen. */
  onListAdded(data: AddEditShoppinglistDto): void {
    this.syncContext();
    this.shoppingLists.update(lists => addList(lists, data));
    this.confirmListAdded(data.id!);
  }

  /** Nach dem Löschen einer Liste (REST oder WebSocket `delete-list`). */
  removeItemScope(listId: number): void {
    this.syncContext();
    this.itemScopes.delete(listId);
  }

  // SHOPPINGITEMS

  addItemToShoppingList(newShoppingItem: AddEditShoppingItemDto): void {
    const inContext = this.captureContext();
    this.http
      .post<AddEditShoppingItemDto>(this.addItemToShoppingListUrl, newShoppingItem)
      .subscribe({
        next: (result) => {
          if (!inContext()) return;
          const newItem: ShoppingitemDto = {
            id: result.id!,
            name: result.name,
            completed: result.completed
          }

          this.shoppingLists.update(lists =>
            lists.map(list =>
              list.id === result.shoppingListId
                ? {
                    ...list,
                    shoppingItems: [...list.shoppingItems, newItem]
                  }
                : list
            )
          );
          this.confirmItemAdded(result.shoppingListId, newItem.id);
        },
        error: (err) => {
          console.error("Error adding shopping item:", err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.save.error'));
        }
      })
    }

    /**
     * `notifyOnError = false` beim Abhaken: kein Toast, damit Abhaken ohne Netz (Offline-Story) nicht bei jedem
     * Antippen einen Fehler meldet.
     */
    updateItemOfShoppingList(updateShoppingItem: AddEditShoppingItemDto, notifyOnError = true): void {
      this.http
        .put<AddEditShoppingItemDto>(this.updateItemOfShoppingListUrl, updateShoppingItem)
        .subscribe({
          next: (result) => {
            const updatedItem: ShoppingitemDto = {
              id: result.id!,
              name: result.name,
              completed: result.completed
            }

            this.shoppingLists.update(lists =>
              lists.map(list =>
                list.shoppingItems.some(item => item.id === updatedItem.id)
                  ? {
                      ...list,
                      shoppingItems: list.shoppingItems.map(item =>
                        item.id === updatedItem.id ? updatedItem : item
                      )
                    }
                  : list
              )
            )
          },
          error: (err) => {
            console.error("Error updating shopping item:", err);
            if (notifyOnError) this.alertService.showToast(this.translate.instant('alerts.shoppinglist.save.error'));
          }
        })
    }

    deleteItemFromShoppingList(deleteShoppingItem: AddEditShoppingItemDto): void {
      this.http
        .post<void>(this.deleteItemFromShoppingListUrl, deleteShoppingItem)
        .subscribe({
          next: () => {
            this.shoppingLists.update(lists =>
              lists.map(list =>
                list.id === deleteShoppingItem.shoppingListId
                  ? {
                    ...list,
                    shoppingItems: list.shoppingItems.filter(item => item.id !== deleteShoppingItem.id)
                    }
                  : list
              )
            )
          },
          error: (err) => {
            console.error("Error deleting shopping item:", err);
            this.alertService.showToast(this.translate.instant('alerts.shoppinglist.save.error'));
          }
        })
    }

    deleteAllCompletedShoppingItems(deleteShoppingItems: AddEditShoppingItemDto[]): void {
      this.http
        .post<void>(this.deleteAllItemsFromShoppingListUrl, deleteShoppingItems)
        .subscribe({
          next: () => {
            this.shoppingLists.update(lists =>
              lists.map(list =>
                list.id === deleteShoppingItems[0].shoppingListId
                ? {
                    ...list,
                    shoppingItems: list.shoppingItems.filter(items =>
                      !deleteShoppingItems.some(deletedItem => deletedItem.id === items.id)
                    )
                  }
                : list
              )
            )

          },
          error: (err) => {
            console.error("Error deleting completed items:", err);
            this.alertService.showToast(this.translate.instant('alerts.shoppinglist.save.error'));
          }
        })
    }

  /** Wie `moveShoppingList`, für einen Eintrag innerhalb seiner Liste. */
  moveShoppingItem(group: Group, listId: number, itemId: number, toIndex: number): void {
    this.syncContext();
    const list = this.shoppingLists().find(l => l.id === listId);
    if (!list) return;
    const currentIds = list.shoppingItems.map(item => item.id);
    const ids = moveId(currentIds, itemId, toIndex);
    if (!ids) return;

    const inContext = this.captureContext();
    const scope = begin(withFallback(this.itemScopes.get(listId) ?? emptyScope(UNKNOWN_REVISION), currentIds));
    this.itemScopes.set(listId, scope);
    this.shoppingLists.update(lists => reorderItems(lists, listId, ids));
    const body: ShoppingitemOrderDto = {
      groupId: group.id, shoppingListId: listId, revision: scope.confirmed.revision, shoppingItemIds: ids
    };
    this.http
      .put<ShoppingitemOrderDto>(this.reorderShoppingItemsUrl, body)
      .subscribe({
        next: (result) => {
          if (!inContext()) return;
          const current = this.itemScopes.get(listId);
          if (current) this.itemScopes.set(listId, accept(current, result.revision, result.shoppingItemIds));
          this.finishItemMove(group, listId, false);
        },
        error: (err) => {
          if (!inContext()) return;
          console.error('Error reordering shopping items:', err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.reorder.error'));
          this.finishItemMove(group, listId, true);
        }
      });
  }

  /** Bestätigte Reihenfolge der Einträge einer Liste (WebSocket `reorder-items`), auch für eine noch unbekannte Liste. */
  applyItemOrder(data: ShoppingitemOrderDto): void {
    this.syncContext();
    const scope = this.itemScopes.get(data.shoppingListId) ?? emptyScope(UNKNOWN_REVISION);
    this.itemScopes.set(data.shoppingListId, accept(scope, data.revision, data.shoppingItemIds));
    this.reconcileItems(data.shoppingListId);
  }

  /** WebSocket `add-item`: Eintrag anhängen (idempotent) und in die bestätigte Reihenfolge aufnehmen. */
  onItemAdded(data: AddEditShoppingItemDto): void {
    this.syncContext();
    this.shoppingLists.update(lists => addItem(lists, data));
    this.confirmItemAdded(data.shoppingListId, data.id!);
  }

  // Reihenfolge: Hilfsmethoden

  /** Gruppe oder Sitzung gewechselt: Generation erhöhen und den gesamten Order-Zustand verwerfen. */
  private syncContext(): void {
    const groupId = this.groupService.activeGroupId();
    const session = this.groupService.session();
    if (groupId === this.contextGroupId && session === this.contextSession) return;
    this.contextGroupId = groupId;
    this.contextSession = session;
    this.generation++;
    this.listScope = emptyScope(UNKNOWN_REVISION);
    this.itemScopes.clear();
  }

  /**
   * Prüfung „noch derselbe Kontext wie beim Start?“ für asynchrone Callbacks: Generation und zusätzlich Gruppe und
   * Sitzung direkt (falls der Wechsel noch nicht durch den effect gelaufen ist).
   */
  private captureContext(): () => boolean {
    this.syncContext();
    const generation = this.generation;
    const groupId = this.contextGroupId;
    const session = this.contextSession;
    return () => generation === this.generation
      && groupId === this.groupService.activeGroupId()
      && session === this.groupService.session();
  }

  /**
   * GET: Reihenfolgen und Revisionen übernehmen (≥-Regel), offene Requests bleiben unberührt. Bereiche ohne offene
   * eigene Requests zeigen danach die bestätigte Reihenfolge (`reconcile`), Bereiche mit offenen Requests behalten die
   * bisher sichtbare, optimistische Reihenfolge (Codex-Review F-001) – tolerant auf die neuen Inhalte angewandt.
   */
  private acceptLoadedOrder(lists: ShoppinglistDto[], previous: ShoppinglistDto[]): void {
    const listRevision = lists[0]?.listOrderRevision ?? UNKNOWN_REVISION;
    this.listScope = acceptFromLoad(this.listScope, listRevision, lists.map(list => list.id));
    for (const list of lists) {
      const scope = this.itemScopes.get(list.id) ?? emptyScope(UNKNOWN_REVISION);
      this.itemScopes.set(list.id, acceptFromLoad(scope, list.itemOrderRevision ?? UNKNOWN_REVISION,
        list.shoppingItems.map(item => item.id)));
    }
    if (this.listScope.pending > 0) {
      this.shoppingLists.update(current => reorderLists(current, previous.map(list => list.id)));
    } else {
      this.reconcileLists();
    }
    for (const list of lists) {
      if ((this.itemScopes.get(list.id)?.pending ?? 0) > 0) {
        const previousIds = previous.find(p => p.id === list.id)?.shoppingItems.map(item => item.id) ?? [];
        this.shoppingLists.update(current => reorderItems(current, list.id, previousIds));
      } else {
        this.reconcileItems(list.id);
      }
    }
  }

  private confirmListAdded(listId: number): void {
    this.listScope = confirmAdded(withFallback(this.listScope, this.shoppingLists().map(list => list.id)), listId);
    // neue Listen haben serverseitig itemOrderRevision 0; ein vorher eingetroffenes reorder-items bleibt erhalten
    if (!this.itemScopes.has(listId)) this.itemScopes.set(listId, emptyScope(0));
    this.reconcileLists();
  }

  private confirmItemAdded(listId: number, itemId: number): void {
    const visibleIds = this.shoppingLists().find(list => list.id === listId)?.shoppingItems.map(item => item.id) ?? [];
    const scope = withFallback(this.itemScopes.get(listId) ?? emptyScope(UNKNOWN_REVISION), visibleIds);
    this.itemScopes.set(listId, confirmAdded(scope, itemId));
    this.reconcileItems(listId);
  }

  private finishListMove(group: Group, failed: boolean): void {
    const result = finish(this.listScope, failed);
    this.listScope = result.scope;
    if (result.reconcile) this.reconcileLists();
    if (result.refetch) this.getShoppingListsWithItems(group, undefined, { silent: true });
  }

  private finishItemMove(group: Group, listId: number, failed: boolean): void {
    const scope = this.itemScopes.get(listId);
    if (!scope) return; // Liste inzwischen gelöscht
    const result = finish(scope, failed);
    this.itemScopes.set(listId, result.scope);
    if (result.reconcile) this.reconcileItems(listId);
    if (result.refetch) this.getShoppingListsWithItems(group, undefined, { silent: true });
  }

  /** Sichtbare Listenreihenfolge = bestätigte, solange keine eigenen Requests offen sind. */
  private reconcileLists(): void {
    if (this.listScope.pending > 0) return;
    this.shoppingLists.update(lists => reorderLists(lists, this.listScope.confirmed.ids));
  }

  private reconcileItems(listId: number): void {
    const scope = this.itemScopes.get(listId);
    if (!scope || scope.pending > 0) return;
    this.shoppingLists.update(lists => reorderItems(lists, listId, scope.confirmed.ids));
  }
}

/** Neue ID-Reihenfolge nach dem Verschieben von `id` an `toIndex`; `null`, wenn die ID fehlt oder sich nichts ändert. */
function moveId(ids: number[], id: number, toIndex: number): number[] | null {
  const from = ids.indexOf(id);
  const to = Math.max(0, Math.min(toIndex, ids.length - 1));
  if (from < 0 || from === to) return null;
  const moved = ids.filter(other => other !== id);
  moved.splice(to, 0, id);
  return moved;
}

/** Ohne bekannten Stand (noch nichts geladen oder bestätigt) gilt die aktuelle Anzeige als Rücksprungstand. */
function withFallback(scope: OrderScope, currentIds: number[]): OrderScope {
  if (scope.confirmed.ids.length > 0) return scope;
  return { ...scope, confirmed: { ...scope.confirmed, ids: currentIds } };
}
