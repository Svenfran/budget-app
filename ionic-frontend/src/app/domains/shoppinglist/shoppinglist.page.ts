import { Component, effect, OnInit, QueryList, ViewChildren } from '@angular/core';
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
  public toggleLists: any = {};
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
    effect(() => {
      this.activeGroup = this.groupService.activeGroup();
      this.isLoading = true;
      if (!this.activeGroup.flag?.includes(INIT_VALUES.DEFAULT)) {
        this.shoppingLists.set([]);
        this.shoppinglistService.getShoppingListsWithItems(this.activeGroup, () => {
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

    this.webSocketService.subscribe(
      `/user/notification/add-list`,
      (parsedData: any) => {
        const shoppinglistDto: ShoppinglistDto = {
          id: parsedData.id,
          name: parsedData.name,
          shoppingItems: []
        } 
        console.log(parsedData);
        this.shoppingLists.update(lists => [...lists, shoppinglistDto])
      },
      { owner: 'shoppinglist', filterByActiveGroup: true }
    )

    this.webSocketService.subscribe(
      `/user/notification/update-list`,
      (parsedData: any) => {
        console.log(parsedData);
        this.shoppingLists.update(lists => lists.map(list => 
          list.id === parsedData.id ? {...list, name: parsedData.name} : list
        ))
      },
      { owner: 'shoppinglist', filterByActiveGroup: true }
    )

    this.webSocketService.subscribe(
      `/user/notification/delete-list`,
      (parsedData: any) => {
        console.log(parsedData);
        this.shoppingLists.update(lists => lists.filter(list =>
          list.id !== parsedData.id
        ))
      },
      { owner: 'shoppinglist', filterByActiveGroup: true }
    )

    this.webSocketService.subscribe(
      `/user/notification/add-item`,
      (parsedData: any) => {
        console.log(parsedData);
        const newItem: ShoppingitemDto = {
          id: parsedData.id!,
          name: parsedData.name,
          completed: parsedData.completed
        }

        this.shoppingLists.update(lists => 
          lists.map(list => 
            list.id === parsedData.shoppingListId 
              ? { 
                  ...list, 
                  shoppingItems: [...list.shoppingItems, newItem] 
                } 
              : list
          )
        );
      },
      { owner: 'shoppinglist', filterByActiveGroup: true }
    )

    this.webSocketService.subscribe(
      `/user/notification/update-item`,
      (parsedData: any) => {
        console.log(parsedData);
        const updatedItem: ShoppingitemDto = {
          id: parsedData.id!,
          name: parsedData.name,
          completed: parsedData.completed
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
      { owner: 'shoppinglist', filterByActiveGroup: true }
    )

    this.webSocketService.subscribe(
      `/user/notification/delete-item`,
      (parsedData: any) => {
        console.log(parsedData);
        this.shoppingLists.update(lists => 
          lists.map(list => 
            list.id === parsedData.shoppingListId
              ? {
                ...list,
                shoppingItems: list.shoppingItems.filter(item => item.id !== parsedData.id)
                }
              : list
          )
        )
      },
      { owner: 'shoppinglist', filterByActiveGroup: true }
    )
    
    this.webSocketService.subscribe(
      `/user/notification/delete-all-items`,
      (parsedData: any) => {
        console.log(parsedData);
        this.shoppingLists.update(lists => 
          lists.map(list =>
            list.id === parsedData[0].shoppingListId
            ? {
                ...list,
                shoppingItems: list.shoppingItems.filter(items =>
                  !parsedData.some((deletedItem: { id: number }) => deletedItem.id === items.id)
                )
              } 
            : list
          )
        )
      },
      { owner: 'shoppinglist', filterByActiveGroup: true }
    )
  }
}
