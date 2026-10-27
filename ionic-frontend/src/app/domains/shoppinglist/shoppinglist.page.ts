import {
  Component, computed, effect, ElementRef, OnDestroy, OnInit, QueryList, Renderer2, untracked, ViewChild, ViewChildren
} from '@angular/core';
import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { ShoppinglistService } from './service/shoppinglist.service';
import { AddEditShoppinglistDto } from './model/add-edit-shoppinglist-dto';
import { AlertService } from 'src/app/service/alert.service';
import { AlertController, IonInput, IonItemSliding, LoadingController } from '@ionic/angular';
import { ShoppinglistDto } from './model/shoppinglist-dto';
import { AddEditShoppingItemDto } from './model/add-edit-shopping-item-dto';
import { ShoppingitemDto } from './model/shoppingitem-dto';
import { WebSocketService } from 'src/app/service/websocket.service';
import { GroupService } from 'src/app/service/group.service';
import { INIT_NUMBERS, INIT_VALUES } from 'src/app/constants/default-values';
import { TranslateService } from '@ngx-translate/core';
import { deleteAllItems, deleteItem, deleteList, updateItem, updateList } from './shoppinglist-events';
import { ShoppinglistOrderDto } from './model/shoppinglist-order-dto';
import { ShoppingitemOrderDto } from './model/shoppingitem-order-dto';
import { ShoppingitemMovedDto } from './model/shoppingitem-moved-dto';

@Component({
  selector: 'app-shoppinglist',
  templateUrl: './shoppinglist.page.html',
  styleUrls: ['./shoppinglist.page.scss'],
  standalone: false
})
export class ShoppinglistPage implements OnInit, OnDestroy {
  @ViewChildren(IonInput) inputFields!: QueryList<IonInput>; 
  @ViewChild('scrollHost', { static: true }) scrollHost!: ElementRef<HTMLElement>;
  public shoppingLists = this.shoppinglistService.getShoppingLists();
  public isLoading: boolean = true;
  /** Aufklappzustand je Listen-ID (nicht Index), damit hinzukommende/entfernte Listen ihn nicht verschieben. */
  public toggleLists: { [listId: number]: boolean } = {};
  public newItemInputs: { [listId: number]: string } = {};
  public activeGroup = this.groupService.activeGroup();
  /** Drag & Drop startet nach Antippen und kurzem Halten (DIV-1, E1); Wert am Gerät abgestimmt (Phase 4a). */
  public dragStartDelay = { touch: 300, mouse: 0 };
  /** Ende des letzten Drags: ein direkt folgender Click gehört zur Geste und wird ignoriert. */
  private dragEndedAt = 0;
  private static readonly CLICK_AFTER_DRAG_MS = 400;
  /** IDs aller Item-Container: jeder ist mit den übrigen verbunden (das CDK lässt den eigenen aus), DIV-2. */
  public connectedItemLists = computed(() => this.shoppingLists().map(list => this.itemDropListId(list.id)));


  constructor(
    private shoppinglistService: ShoppinglistService,
    private alertService: AlertService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private webSocketService: WebSocketService,
    private groupService: GroupService,
    private translate: TranslateService,
    private renderer: Renderer2
  ) {
    // Anzeige: hält den Gruppennamen aktuell (auch bei Umbenennung), ohne neu zu laden
    effect(() => {
      this.activeGroup = this.groupService.activeGroup();
    });
    // Laden: nur bei echtem Gruppenwechsel (activeGroupId), neuer Anmelde-Sitzung (session, auch bei gleicher Gruppe
    // nach Logout/Login) und den übrigen Triggern, nicht bei Umbenennung
    effect(() => {
      this.groupService.activeGroupId();
      this.groupService.session();
      const activeGroup = untracked(() => this.groupService.activeGroup());
      // ein unterbrochener Drag gibt den Puffer frei; Updates der alten Gruppe bzw. Sitzung verwirft der Service
      this.shoppinglistService.releaseUpdates();
      this.isLoading = true;
      if (!activeGroup.flag?.includes(INIT_VALUES.DEFAULT)) {
        this.shoppingLists.set([]);
        this.shoppinglistService.getShoppingListsWithItems(activeGroup, () => {
          this.isLoading = false;
        });
      } else {
        this.isLoading = false;
      }
    });
  }

  ngOnInit() {
    // einmal registrieren; der WebSocketService abonniert nach jedem (Re-)Connect selbst neu
    this.subscribeToTopics();
  }
  
  ngOnDestroy() {
    // nur die eigenen Topics, die globalen von AppComponent bleiben bestehen
    this.webSocketService.unsubscribeOwner('shoppinglist');
    this.shoppinglistService.releaseUpdates();
  }

  refreshShoppingList(event: CustomEvent) {
    setTimeout(() => {
      this.shoppinglistService.getShoppingListsWithItems(this.activeGroup);
      (event.target as HTMLIonRefresherElement).complete();
    }, 2000);
  }

  onCreateList() {
    this.alertService.presentInputAlert({
      header: this.translate.instant('alerts.shoppinglist.new.header'),
      placeholder: this.translate.instant('alerts.shoppinglist.new.placeholder'),
      okText: this.translate.instant('alerts.shoppinglist.new.ok'),
      cancelText: this.translate.instant('alerts.shoppinglist.new.cancel'),
      onConfirm: async (listName) => {
        const loading = await this.alertService.presentLoading(
          this.translate.instant('alerts.shoppinglist.new.loading')
        );

        const newShoppingList: AddEditShoppinglistDto = {
          id: null,
          name: listName.trim(),
          groupId: this.activeGroup.id,
        };
        this.shoppinglistService.addShoppingList(newShoppingList);
        loading.dismiss();
      },
    });
  }

  onUpdateList(list: ShoppinglistDto, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: this.translate.instant('alerts.shoppinglist.edit.header'),
      buttons: [{
        text: this.translate.instant('alerts.shoppinglist.edit.cancel'),
        role: "cancel"
      }, {
        text: this.translate.instant('alerts.shoppinglist.edit.ok'),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant('alerts.shoppinglist.edit.loading')
          }).then(loadingEl => {

            if (!data) return;
            const trimmedListName = data.listName.trim();
            if (trimmedListName === "") return;

            const updateShoppingList: AddEditShoppinglistDto = {
              id: list.id, 
              name: trimmedListName, 
              groupId: this.activeGroup.id
            };

            this.shoppinglistService.updateShoppingList(updateShoppingList)
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "listName",
          value: list.name,
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector('ion-alert input') as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }))
  }

  onDeleteList(list: ShoppinglistDto, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: this.translate.instant('alerts.shoppinglist.delete.header'),
      message: this.translate.instant('alerts.shoppinglist.delete.message', {listName: list.name}),
      buttons: [{
        text: this.translate.instant('alerts.shoppinglist.delete.cancel'),
        role: "cancel"
      }, {
        text: this.translate.instant('alerts.shoppinglist.delete.ok'),
        handler: () => {
          this.loadingCtrl.create({
            message: this.translate.instant('alerts.shoppinglist.delete.loading')
          }).then(loadingEl => {
            const deletedShoppinglist: AddEditShoppinglistDto = {
              id: list.id,
              name: list.name,
              groupId: this.activeGroup.id
            }
            this.shoppinglistService.deleteShoppingList(deletedShoppinglist);
            loadingEl.dismiss();
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }
  
  onCreateItem(list: ShoppinglistDto) {
    if (!this.newItemInputs[list.id]?.trim()) return;
    const trimmedItemName = this.newItemInputs[list.id]?.trim();
    if (trimmedItemName === "") return;

    const newItem: AddEditShoppingItemDto = {
      id: null,
      name: trimmedItemName,
      completed: false,
      shoppingListId: list.id,
      groupId: this.activeGroup.id,
    }
    this.newItemInputs[list.id] = '';
    this.shoppinglistService.addItemToShoppingList(newItem);
    setTimeout(() => { this.focusInput(list.id); }, 300);
  }

  private focusInput(listId: number) {
    const inputElement = this.inputFields.find((_, index) => this.shoppingLists()[index].id === listId);
    if (inputElement) {
      inputElement.setFocus();
    } else {
      console.warn('Kein passendes Input-Feld gefunden für List ID:', listId);
    }
  }

  onUpdateItem(list: ShoppinglistDto, item: ShoppingitemDto) {
    if (this.isClickAfterDrag()) return;
    this.alertCtrl.create({
      header: this.translate.instant('alerts.shoppinglist.item.edit.header'),
      buttons: [{
        text: this.translate.instant('alerts.shoppinglist.item.edit.cancel'),
        role: "cancel"
      }, {
        text: this.translate.instant('alerts.shoppinglist.item.edit.ok'),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant('alerts.shoppinglist.item.edit.loading')
          }).then(loadingEl => {
            if (!data) return;
            const trimmedItemName = data.itemName.trim();
            if (trimmedItemName === "") return;

            const updateShoppingItem: AddEditShoppingItemDto = {
              id: item.id,
              name: trimmedItemName,
              completed: item.completed,
              groupId: this.activeGroup.id,
              shoppingListId: list.id
            };
            
            this.shoppinglistService.updateItemOfShoppingList(updateShoppingItem);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "itemName",
          value: item.name,
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector('ion-alert input') as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }));
  }

  onDeleteItem(list: ShoppinglistDto, item: ShoppingitemDto) {
    if (this.isClickAfterDrag()) return;
    const deletedItem: AddEditShoppingItemDto = {
      id: item.id,
      name: item.name,
      completed: item.completed,
      shoppingListId: list.id,
      groupId: this.activeGroup.id,
    }
    this.shoppinglistService.deleteItemFromShoppingList(deletedItem);
  }

  toggleList(list: ShoppinglistDto) {
    if (this.isClickAfterDrag()) return;
    this.toggleLists[list.id] = !this.toggleLists[list.id];
  }

  markAsDone(list: ShoppinglistDto, item: ShoppingitemDto) {
    if (this.isClickAfterDrag()) return;
    item.completed = !item.completed;
    let updateShoppingItem: AddEditShoppingItemDto = {
      id: item.id,
      name: item.name,
      completed: item.completed,
      groupId: this.activeGroup.id,
      shoppingListId: list.id
    };
    this.shoppinglistService.updateItemOfShoppingList(updateShoppingItem, false);
  }

  deleteListOfItems(list: ShoppinglistDto) {
    let items: AddEditShoppingItemDto[] = [];
    list.shoppingItems.forEach(item => {
      if (item.completed) {
        items.push({
          id: item.id,
          name: item.name,
          completed: item.completed,
          shoppingListId: list.id,
          groupId: this.activeGroup.id
        });
      }
    })
    if (items.length === 0) return;
    this.shoppinglistService.deleteAllCompletedShoppingItems(items);
  }

  // Drag & Drop (DIV-1). Das CDK löst synchron erst `ended`, dann `dropped` aus; der Move wird deshalb im Drop-Handler
  // gegen den während der Geste unveränderten Zustand berechnet, erst danach gibt der Service zurückgestellte
  // Änderungen (WebSocket, GET, REST, PUT-Antworten) frei.

  /**
   * `itemDrag` (DIV-2): Die Klasse für die Ablagefläche leerer Listen wird synchron gesetzt, nicht per Binding – das
   * CDK misst die Container direkt nach `started`, ohne Change Detection dazwischen.
   */
  onDragStarted(slidingItem?: IonItemSliding, itemDrag = false) {
    this.shoppinglistService.holdUpdates();
    slidingItem?.close();
    if (itemDrag) this.renderer.addClass(this.scrollHost.nativeElement, 'item-drag-active');
  }

  onDragEnded() {
    this.renderer.removeClass(this.scrollHost.nativeElement, 'item-drag-active');
    this.dragEndedAt = Date.now();
    // Rückfall für einen Drag ohne Drop; nach einem Drop ist der Puffer schon leer
    queueMicrotask(() => this.shoppinglistService.releaseUpdates());
  }

  /** Drop einer Liste: die gezogene ID wird im aktuellen Zustand neu aufgelöst, eine inzwischen gelöschte verworfen. */
  onListDropped(event: CdkDragDrop<unknown, unknown, number>) {
    const listId = event.item.data;
    if (this.shoppingLists().some(list => list.id === listId)) {
      this.shoppinglistService.moveShoppingList(this.activeGroup, listId, event.currentIndex);
    }
    this.shoppinglistService.releaseUpdates();
  }

  /**
   * Drop eines Eintrags: innerhalb seiner Liste nur umsortieren (Liste und Eintrag werden im aktuellen Zustand neu
   * aufgelöst), aus einer anderen Liste verschieben (DIV-2; Quelle/Ziel aus den Container-Daten, der Service verwirft
   * den Move, wenn Eintrag oder Liste inzwischen fehlen). Das CDK meldet als Ziel den zuletzt betretenen Container,
   * auch wenn außerhalb losgelassen wurde; verschoben wird nur, wenn der Zeiger über ihm liegt (Codex-Review F-001).
   */
  onItemDropped(list: ShoppinglistDto, event: CdkDragDrop<number, number, number>) {
    const itemId = event.item.data;
    if (event.previousContainer === event.container) {
      const current = this.shoppingLists().find(l => l.id === list.id);
      if (current?.shoppingItems.some(item => item.id === itemId)) {
        this.shoppinglistService.moveShoppingItem(this.activeGroup, list.id, itemId, event.currentIndex);
      }
    } else if (event.isPointerOverContainer) {
      this.shoppinglistService.moveShoppingItemToList(this.activeGroup, itemId, event.previousContainer.data,
        event.container.data, event.currentIndex);
    }
    this.shoppinglistService.releaseUpdates();
  }

  /** Ein Eintrag mit offenem Move ist bis zur Antwort nicht erneut ziehbar (höchstens ein Move je Eintrag). */
  isMovePending(itemId: number): boolean {
    return this.shoppinglistService.isMovePending(itemId);
  }

  itemDropListId(listId: number): string {
    return `item-list-${listId}`;
  }

  /** Einträge nur in aufgeklappte Listen ablegen (E1); das CDK prüft das schon beim Start des Drags. */
  canEnterItemList = (_drag: CdkDrag, drop: CdkDropList<number>): boolean => !!this.toggleLists[drop.data];

  private isClickAfterDrag(): boolean {
    return Date.now() - this.dragEndedAt < ShoppinglistPage.CLICK_AFTER_DRAG_MS;
  }

  private subscribeToTopics() {
    const options = { owner: 'shoppinglist', filterByActiveGroup: true };
    // alle Handler laufen über den Puffer des Service: während eines Drags werden sie zurückgestellt (DIV-1)
    const on = <T>(action: string, handler: (data: T) => void) =>
      this.webSocketService.subscribe<T>(`/user/notification/${action}`,
        data => this.shoppinglistService.applyOrDefer(() => handler(data)), options);
    const onList = (action: string, apply: (lists: ShoppinglistDto[], data: AddEditShoppinglistDto) => ShoppinglistDto[]) =>
      on<AddEditShoppinglistDto>(action, data => this.shoppingLists.update(lists => apply(lists, data)));
    const onItem = (action: string, apply: (lists: ShoppinglistDto[], data: AddEditShoppingItemDto) => ShoppinglistDto[]) =>
      on<AddEditShoppingItemDto>(action, data => this.shoppingLists.update(lists => apply(lists, data)));

    on<AddEditShoppinglistDto>('add-list', data => this.shoppinglistService.onListAdded(data));
    onList('update-list', updateList);
    on<AddEditShoppinglistDto>('delete-list', data => {
      this.shoppingLists.update(lists => deleteList(lists, data));
      this.shoppinglistService.removeItemScope(data.id!);
    });
    on<AddEditShoppingItemDto>('add-item', data => this.shoppinglistService.onItemAdded(data));
    onItem('update-item', updateItem);
    onItem('delete-item', deleteItem);
    on<AddEditShoppingItemDto[]>('delete-all-items', data => this.shoppingLists.update(lists => deleteAllItems(lists, data)));
    // Reihenfolge (DIV-1): der Service übernimmt nur neuere Revisionen
    on<ShoppinglistOrderDto>('reorder-lists', data => this.shoppinglistService.applyListOrder(data));
    on<ShoppingitemOrderDto>('reorder-items', data => this.shoppinglistService.applyItemOrder(data));
    // Verschieben zwischen Listen (DIV-2): der Service ändert die Zuordnung nur bei passendem lokalem Stand
    on<ShoppingitemMovedDto>('move-item', data => this.shoppinglistService.applyItemMove(data));
  }

  trackByListId(_: number, list: ShoppinglistDto): number {
    return list.id;
  }

  trackByItemId(_: number, item: ShoppingitemDto): number {
    return item.id;
  }
}
