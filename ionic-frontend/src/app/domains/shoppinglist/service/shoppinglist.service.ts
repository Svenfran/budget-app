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
import { MoveShoppingItemDto } from '../model/move-shopping-item-dto';
import { ShoppingitemMovedDto } from '../model/shoppingitem-moved-dto';
import { INIT_VALUES } from 'src/app/constants/default-values';
import { Group } from 'src/app/model/group';
import { LoadOptions } from 'src/app/model/load-options';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import {
  addItem, addList, deleteAllItems, deleteItem, locateItem, moveItem, reorderItems, reorderLists
} from '../shoppinglist-events';
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
  private moveItemUrl = `${this.apiBaseUrl}/api/groups/shopping-list/move-item`;

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
  /**
   * Während eines Drags (`holdUpdates`) werden asynchrone Änderungen der Listen zurückgestellt, damit die Indizes
   * des CDK zur dargestellten Datenbasis passen (Codex-Review F-002). Die eigene Aktion `move*` wirkt sofort.
   */
  private held = false;
  private deferredUpdates: { apply: () => void; generation: number }[] = [];
  /**
   * Verschieben zwischen Listen (DIV-2), Übergangstabelle M1–M11 im Plan. Höchstens ein offener eigener Move je
   * Eintrag; `conflict`: während des Requests kam ein fremdes `move-item` für ihn (die Antwort löst dann einen
   * Refetch aus statt eines lokalen Rücksprungs).
   */
  private pendingMoves = new Map<number, { sourceListId: number; targetListId: number; conflict: boolean }>();
  /**
   * Bestätigte Zuordnungswechsel, solange ein GET läuft: eine GET-Antwort mit älterem Stand bekommt sie nachgespielt.
   * `openLoads` hält je laufendem GET (eindeutige `loadId`) den `moveSeq` beim Start.
   */
  private moveLog: { seq: number; itemId: number; sourceListId: number; targetListId: number }[] = [];
  private moveSeq = 0;
  private openLoads = new Map<number, number>();
  private loadSeq = 0;

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

  /** Drag-Start: asynchrone Änderungen der Listen bis `releaseUpdates` zurückstellen. */
  public holdUpdates(): void {
    this.syncContext();
    this.held = true;
  }

  /** Drop bzw. Drag-Ende: zurückgestellte Änderungen der aktuellen Kontext-Generation in Eingangsreihenfolge anwenden. */
  public releaseUpdates(): void {
    this.syncContext();
    this.held = false;
    const updates = this.deferredUpdates;
    this.deferredUpdates = [];
    updates.filter(update => update.generation === this.generation).forEach(update => update.apply());
  }

  /** Änderung der Listen sofort anwenden oder, während eines Drags, zurückstellen (auch für die WebSocket-Handler). */
  public applyOrDefer(apply: () => void): void {
    this.syncContext();
    if (!this.held) {
      apply();
      return;
    }
    this.deferredUpdates.push({ apply, generation: this.generation });
  }

  getShoppingListsWithItems(group: Group, onComplete?: () => void, options: LoadOptions = {}): void{
    if (group.flag?.includes(INIT_VALUES.DEFAULT)) {
      this.shoppingLists.set([]);
      onComplete?.();
      return;
    };

    const inContext = this.captureContext();
    const loadId = ++this.loadSeq;
    this.openLoads.set(loadId, this.moveSeq);
    this.http
      .get<ShoppinglistDto[]>(`${this.shoppingListsWithItemsUrl}/${group.id}`)
      .subscribe({
        next: (result) => {
          // überholt (z. B. inzwischen andere Gruppe aktiv): die Listen der aktuellen Gruppe nicht überschreiben
          if (inContext() && options.isCurrent?.() !== false) {
            this.applyOrDefer(() => {
              const visible = this.shoppingLists();
              // Inhalte: Antwort + seither bestätigte und offene Zuordnungswechsel; Reihenfolgen aus der rohen Antwort
              this.shoppingLists.set(this.withMoves(result || [], this.openLoads.get(loadId) ?? this.moveSeq));
              this.acceptLoadedOrder(result || [], visible);
              this.endLoad(loadId);
            });
          } else {
            this.endLoad(loadId);
          }
          onComplete?.();
        },
        error: (err) => {
          this.endLoad(loadId);
          if (options.silent) {
            console.warn('Silent resync of shoppinglists failed:', err);
            onComplete?.();
            return;
          }
          console.error('Error fetching shoppinglists:', err);
          if (inContext()) this.applyOrDefer(() => this.shoppingLists.set([]));
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
          this.applyOrDefer(() => {
            this.shoppingLists.update(items => [...items, newList]);
            this.confirmListAdded(newList.id);
          });
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
          this.applyOrDefer(() => this.shoppingLists.update(items => items.map(list =>
            list.id === result.id ? {...list, name: result.name} : list
          )));
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
          this.applyOrDefer(() => {
            this.shoppingLists.update(items => items.filter(list =>
              list.id !== deleteShoppingList.id
            ));
            this.removeItemScope(deleteShoppingList.id!);
          });
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
          this.applyOrDefer(() => {
            this.listScope = accept(this.listScope, result.revision, result.shoppingListIds);
            this.finishListMove(group, false);
          });
        },
        error: (err) => {
          if (!inContext()) return;
          console.error('Error reordering shopping lists:', err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.reorder.error'));
          this.applyOrDefer(() => this.finishListMove(group, true));
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
          this.applyOrDefer(() => this.appendItem(result));
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

            this.applyOrDefer(() => this.shoppingLists.update(lists =>
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
            ));
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
            this.applyOrDefer(() => this.shoppingLists.update(lists => deleteItem(lists, deleteShoppingItem)));
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
            this.applyOrDefer(() => this.shoppingLists.update(lists => deleteAllItems(lists, deleteShoppingItems)));
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
          this.applyOrDefer(() => {
            const current = this.itemScopes.get(listId);
            if (current) this.itemScopes.set(listId, accept(current, result.revision, result.shoppingItemIds));
            this.finishItemMove(group, listId, false);
          });
        },
        error: (err) => {
          if (!inContext()) return;
          console.error('Error reordering shopping items:', err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.reorder.error'));
          this.applyOrDefer(() => this.finishItemMove(group, listId, true));
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

  /**
   * Verschiebt einen Eintrag optimistisch in eine andere Liste an `toIndex` (M1). Verworfen, wenn für ihn schon ein
   * Move offen ist, Quelle = Ziel, er nicht in der Quelle steht oder das Ziel fehlt. Bei einem Fehler springt er
   * zurück (Toast, danach stiller Refetch).
   */
  moveShoppingItemToList(group: Group, itemId: number, sourceListId: number, targetListId: number, toIndex: number): void {
    this.syncContext();
    if (this.pendingMoves.has(itemId) || sourceListId === targetListId) return;
    const source = this.shoppingLists().find(list => list.id === sourceListId);
    const target = this.shoppingLists().find(list => list.id === targetListId);
    if (!source || !target || !source.shoppingItems.some(item => item.id === itemId)) return;
    const sourceIds = source.shoppingItems.map(item => item.id);
    const targetIds = target.shoppingItems.map(item => item.id);
    const ids = [...targetIds];
    ids.splice(Math.max(0, Math.min(toIndex, ids.length)), 0, itemId);

    const inContext = this.captureContext();
    this.beginItemScope(sourceListId, sourceIds);
    this.beginItemScope(targetListId, targetIds);
    this.pendingMoves.set(itemId, { sourceListId, targetListId, conflict: false });
    this.shoppingLists.update(lists => reorderItems(moveItem(lists, itemId, sourceListId, targetListId), targetListId, ids));
    const body: MoveShoppingItemDto = {
      groupId: group.id, shoppingItemId: itemId, sourceShoppingListId: sourceListId, targetShoppingListId: targetListId,
      targetShoppingItemIds: ids
    };
    this.http
      .put<ShoppingitemMovedDto>(this.moveItemUrl, body)
      .subscribe({
        next: (result) => {
          if (!inContext()) return;
          this.applyOrDefer(() => this.finishMove(group, itemId, result));
        },
        error: (err) => {
          if (!inContext()) return;
          console.error('Error moving shopping item:', err);
          this.alertService.showToast(this.translate.instant('alerts.shoppinglist.move.error'));
          this.applyOrDefer(() => this.finishMove(group, itemId, null));
        }
      });
  }

  /** Eigener Move des Eintrags noch offen (die Page sperrt ihn so lange gegen erneutes Ziehen). */
  isMovePending(itemId: number): boolean {
    return this.pendingMoves.has(itemId);
  }

  /**
   * WebSocket `move-item` (M4–M7): beide Reihenfolgen übernehmen, wenn neuer; die Zuordnung nur ändern, wenn der
   * lokale Stand zum Event passt, sonst still neu laden. Der Eintrag wird nie aus dem Event erzeugt.
   */
  applyItemMove(data: ShoppingitemMovedDto): void {
    this.syncContext();
    const itemId = data.shoppingItemId;
    const sourceListId = data.source.shoppingListId;
    const targetListId = data.target.shoppingListId;
    // überholt (M5b): das Ziel ist mindestens so neu bestätigt wie das Event und enthält den Eintrag nicht
    const confirmedTarget = this.itemScopes.get(targetListId)?.confirmed;
    const overtaken = !!confirmedTarget && confirmedTarget.revision >= data.target.revision
      && !confirmedTarget.ids.includes(itemId);
    for (const order of [data.source, data.target]) {
      const scope = this.itemScopes.get(order.shoppingListId) ?? emptyScope(UNKNOWN_REVISION);
      this.itemScopes.set(order.shoppingListId, accept(scope, order.revision, order.shoppingItemIds));
    }

    let refetch = false;
    const pending = this.pendingMoves.get(itemId);
    if (pending) {
      this.pendingMoves.set(itemId, { ...pending, conflict: true });
    } else {
      const location = locateItem(this.shoppingLists(), itemId);
      const targetExists = this.shoppingLists().some(list => list.id === targetListId);
      if (location === sourceListId && targetExists) {
        if (!overtaken) {
          this.shoppingLists.update(lists => moveItem(lists, itemId, sourceListId, targetListId));
          this.logMove(itemId, sourceListId, targetListId);
        }
      } else if (location === targetListId) {
        this.logMove(itemId, sourceListId, targetListId);
      } else {
        refetch = true;
      }
    }
    this.reconcileItems(sourceListId);
    this.reconcileItems(targetListId);
    if (refetch) this.getShoppingListsWithItems(this.groupService.activeGroup(), undefined, { silent: true });
  }

  /** WebSocket `add-item`: Eintrag anhängen (idempotent) und in die bestätigte Reihenfolge aufnehmen. */
  onItemAdded(data: AddEditShoppingItemDto): void {
    this.syncContext();
    this.appendItem(data);
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
    this.deferredUpdates = [];
    this.pendingMoves.clear();
    this.moveLog = [];
    this.openLoads.clear();
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

  /**
   * Neuer Eintrag (REST-Antwort oder `add-item`): anhängen, wenn er in keiner Liste steht, und nur dann in die
   * bestätigte Reihenfolge seiner Liste aufnehmen. Steht er schon in einer anderen Liste (z. B. inzwischen verschoben,
   * DIV-2), bleibt deren bestätigte Reihenfolge unberührt.
   */
  private appendItem(data: AddEditShoppingItemDto): void {
    const lists = this.shoppingLists();
    const updated = addItem(lists, data);
    if (updated === lists) return;
    this.shoppingLists.set(updated);
    this.confirmItemAdded(data.shoppingListId, data.id!);
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

  private beginItemScope(listId: number, currentIds: number[]): void {
    this.itemScopes.set(listId, begin(withFallback(this.itemScopes.get(listId) ?? emptyScope(UNKNOWN_REVISION), currentIds)));
  }

  /**
   * PUT-Antwort eines eigenen Moves (M2/M3), `result = null` bei einem Fehler. Ohne fremdes Event (`conflict`) springt
   * der Eintrag bei einem Fehler lokal zurück; mit `conflict` klärt ein stiller Refetch die Zuordnung.
   */
  private finishMove(group: Group, itemId: number, result: ShoppingitemMovedDto | null): void {
    const move = this.pendingMoves.get(itemId);
    if (!move) return;
    this.pendingMoves.delete(itemId);
    const failed = result === null;
    if (result) {
      for (const order of [result.source, result.target]) {
        const scope = this.itemScopes.get(order.shoppingListId);
        if (scope) this.itemScopes.set(order.shoppingListId, accept(scope, order.revision, order.shoppingItemIds));
      }
      if (!move.conflict) this.logMove(itemId, move.sourceListId, move.targetListId);
    } else if (!move.conflict) {
      this.shoppingLists.update(lists => moveItem(lists, itemId, move.targetListId, move.sourceListId));
    }
    let refetch = move.conflict;
    for (const listId of [move.sourceListId, move.targetListId]) {
      const scope = this.itemScopes.get(listId);
      if (!scope) continue; // Liste inzwischen gelöscht
      const finished = finish(scope, failed);
      this.itemScopes.set(listId, finished.scope);
      if (finished.reconcile) this.reconcileItems(listId);
      refetch ||= finished.refetch;
    }
    if (refetch) this.getShoppingListsWithItems(group, undefined, { silent: true });
  }

  /** Bestätigten Zuordnungswechsel für laufende GETs merken (nur solange einer läuft). */
  private logMove(itemId: number, sourceListId: number, targetListId: number): void {
    if (this.openLoads.size === 0) return;
    this.moveLog.push({ seq: ++this.moveSeq, itemId, sourceListId, targetListId });
  }

  /** GET-Inhalte + seit `startSeq` bestätigte und offene eigene Zuordnungswechsel (wirken nur bei passender Quelle). */
  private withMoves(lists: ShoppinglistDto[], startSeq: number): ShoppinglistDto[] {
    let result = lists;
    for (const entry of this.moveLog) {
      if (entry.seq > startSeq) result = moveItem(result, entry.itemId, entry.sourceListId, entry.targetListId);
    }
    for (const [itemId, move] of this.pendingMoves) {
      result = moveItem(result, itemId, move.sourceListId, move.targetListId);
    }
    return result;
  }

  /** GET beendet (Erfolg, Fehler oder verworfen): Log bis zum ältesten noch laufenden GET kürzen. */
  private endLoad(loadId: number): void {
    this.openLoads.delete(loadId);
    if (this.openLoads.size === 0) {
      this.moveLog = [];
      return;
    }
    const oldestStart = Math.min(...this.openLoads.values());
    this.moveLog = this.moveLog.filter(entry => entry.seq > oldestStart);
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
