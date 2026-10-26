import { Component, effect, OnInit, QueryList, untracked, ViewChildren } from '@angular/core';
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
import { addItem, addList, deleteAllItems, deleteItem, deleteList, updateItem, updateList } from './shoppinglist-events';

@Component({
  selector: 'app-shoppinglist',
  templateUrl: './shoppinglist.page.html',
  styleUrls: ['./shoppinglist.page.scss'],
  standalone: false
})
export class ShoppinglistPage implements OnInit {
  @ViewChildren(IonInput) inputFields!: QueryList<IonInput>; 
  public shoppingLists = this.shoppinglistService.getShoppingLists();
  public isLoading: boolean = true;
  /** Aufklappzustand je Listen-ID (nicht Index), damit hinzukommende/entfernte Listen ihn nicht verschieben. */
  public toggleLists: { [listId: number]: boolean } = {};
  public newItemInputs: { [listId: number]: string } = {};
  public activeGroup = this.groupService.activeGroup();


  constructor(
    private shoppinglistService: ShoppinglistService,
    private alertService: AlertService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private webSocketService: WebSocketService,
    private groupService: GroupService,
    private translate: TranslateService
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
    const deletedItem: AddEditShoppingItemDto = {
      id: item.id,
      name: item.name,
      completed: item.completed,
      shoppingListId: list.id,
      groupId: this.activeGroup.id,
    }
    this.shoppinglistService.deleteItemFromShoppingList(deletedItem);
  }

  markAsDone(list: ShoppinglistDto, item: ShoppingitemDto) {
    item.completed = !item.completed;
    let updateShoppingItem: AddEditShoppingItemDto = {
      id: item.id,
      name: item.name,
      completed: item.completed,
      groupId: this.activeGroup.id,
      shoppingListId: list.id
    };
    this.shoppinglistService.updateItemOfShoppingList(updateShoppingItem);
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

  private subscribeToTopics() {
    const options = { owner: 'shoppinglist', filterByActiveGroup: true };
    const onList = (action: string, apply: (lists: ShoppinglistDto[], data: AddEditShoppinglistDto) => ShoppinglistDto[]) =>
      this.webSocketService.subscribe<AddEditShoppinglistDto>(
        `/user/notification/${action}`, data => this.shoppingLists.update(lists => apply(lists, data)), options);
    const onItem = (action: string, apply: (lists: ShoppinglistDto[], data: AddEditShoppingItemDto) => ShoppinglistDto[]) =>
      this.webSocketService.subscribe<AddEditShoppingItemDto>(
        `/user/notification/${action}`, data => this.shoppingLists.update(lists => apply(lists, data)), options);

    onList('add-list', addList);
    onList('update-list', updateList);
    onList('delete-list', deleteList);
    onItem('add-item', addItem);
    onItem('update-item', updateItem);
    onItem('delete-item', deleteItem);
    this.webSocketService.subscribe<AddEditShoppingItemDto[]>(
      `/user/notification/delete-all-items`, data => this.shoppingLists.update(lists => deleteAllItems(lists, data)), options);
  }

  trackByListId(_: number, list: ShoppinglistDto): number {
    return list.id;
  }

  trackByItemId(_: number, item: ShoppingitemDto): number {
    return item.id;
  }
}
