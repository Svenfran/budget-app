import { Component, OnDestroy, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController } from '@ionic/angular';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuthService } from 'src/app/auth/auth.service';
import { AddEditShoppingItemDto } from 'src/app/models/add-edit-shopping-item-dto';
import { AddEditShoppingListDto } from 'src/app/models/add-edit-shopping-list-dto';
import { GroupSideNav } from 'src/app/models/group-side-nav';
import { ShoppingItemDto } from 'src/app/models/shopping-item-dto';
import { ShoppingListDto } from 'src/app/models/shopping-list-dto';
import { AlertService } from 'src/app/services/alert.service';
import { GroupService } from 'src/app/services/group.service';
import { ShoppingitemService } from 'src/app/services/shoppingitem.service';
import { ShoppinglistService } from 'src/app/services/shoppinglist.service';

@Component({
  selector: 'app-shoppinglist',
  templateUrl: './shoppinglist.page.html',
  styleUrls: ['./shoppinglist.page.scss'],
})
export class ShoppinglistPage implements OnInit, OnDestroy {

  activeGroupName: string;
  activeGroupId: number;
  activeGroup: GroupSideNav;
  userName: string;
  shoppingListWithItems: ShoppingListDto[] = [];
  shoppingItemsForNewList: ShoppingItemDto[] = [];
  allShoppingItems: ShoppingItemDto[] = [];
  allCurrentShoppingItems: ShoppingItemDto[] = [];
  toggleLists: any = {};
  itemName = "";
  isLoading: boolean = false;
  focusIsSet: boolean;
  requestTimeStamp: number = new Date('1900-01-01').getTime();
  INITIAL_REQUEST_TIMESTAMP: number = new Date('1900-01-01').getTime();
  pollSub: Subscription = new Subscription();
  loadedActiveGroup: Promise<boolean>;
  pageLeft: boolean = false;


  constructor(
    private groupService: GroupService,
    private shoppingListService: ShoppinglistService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private shoppingItemService: ShoppingitemService,
    private alertService: AlertService,
    private authService: AuthService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.groupService.activeGroup.subscribe(group => {
      if (group) {
        // console.log(group);
        this.activeGroupName = group.name;
        this.activeGroupId = group.id;
        this.activeGroup = group;
        this.loadedActiveGroup = Promise.resolve(true);
        this.getShoppingListWithItems(group.id);
      } else {
        this.groupService.setActiveGroup(null);
      }
    })
    this.pollForList();
  }

  getShoppingListWithItems(groupId: number) {
    this.isLoading = true;
    if (groupId !== null) {
      this.shoppingListService.getShoppingListsWithItems(groupId, this.INITIAL_REQUEST_TIMESTAMP).subscribe(list => {
        this.shoppingListWithItems = list;
        this.isLoading = false;
      })
    }
  }

  getAllListItemsForGroup(listObj: ShoppingListDto[]): ShoppingItemDto[] {
    let listArray = [];
    listObj.forEach(subList => {
      subList.shoppingItems.forEach(listItem => {
        let shoppingList = new ShoppingItem(
          subList.id, listItem.id, listItem.name, listItem.completed
        )
        listArray.push(shoppingList);
        shoppingList = null;
      });
    });
    return listArray;
  }

  getDifferenceList(listObj1: ShoppingListDto[], listObj2: ShoppingListDto[]): ShoppingListDto[] {
    return listObj1.filter(el1 => listObj2.every(el2 => el2.id !== el1.id));
  }

  getDifferenceItem(listObj1: ShoppingItemDto[], listObj2: ShoppingItemDto[]): ShoppingItemDto[] {
    return listObj1.filter(el1 => listObj2.every(el2 => el2.id !== el1.id || el2.name !== el1.name || el2.completed !== el1.completed));
  }

  updateList(currentList, newList, diffList) {
    diffList.forEach(entry1 => {
      if (!newList.includes(entry1)) {
        let i = currentList.indexOf(entry1);
        currentList.splice(i, 1);
      } else if (newList.includes(entry1)) {
        entry1.shoppingItems = [];
        currentList.push(entry1);
      } 
    });

    currentList.forEach((cList, index) => {
      newList.forEach((nList) => {
        if (nList.id === cList.id && nList.name !== cList.name) {
          currentList[index].name = nList.name;
        }
      })
    });
  }

  updateItems(currentList, allNewShoppingItems, diffItem) {
    diffItem.forEach(entry => {
      let items = currentList.filter(list => list.id === entry.listId)[0];
      if (typeof items !== 'undefined') {
        if (!allNewShoppingItems.includes(entry)) {
          items.shoppingItems.forEach((el, index) => {
            if (el.id === entry.id) {
              items.shoppingItems.splice(index, 1);
            }
          });
        } else if (allNewShoppingItems.includes(entry)) {  
          currentList.forEach(cList => {
            if (cList.id === entry.listId) {
              cList.shoppingItems.push(new ShoppingItemDto(entry.id, entry.name, entry.completed));
            }
          });
        }
        items.shoppingItems.forEach((item, index) => {
          if ((item.id === entry.id) && (item.name !== entry.name || item.completed !== entry.completed)) {
            item.shoppingItems[index].name = entry.name;
            item.shoppingItems[index].completed = entry.completed;
          }
        })
        items.shoppingItems.sort((a, b) => a.id < b.id ? -1 : 1);
      }
    });
  }

  pollForList() {
    if (this.activeGroup.id !== null) {
      this.pollSub = this.shoppingListService.getShoppingListsWithItems(this.activeGroupId, this.requestTimeStamp).subscribe(list => {  
        let diffList = [];
        let diffItem = [];
        this.allShoppingItems = [];
        this.allCurrentShoppingItems = [];
  
        this.allShoppingItems = this.getAllListItemsForGroup(list);
        this.allCurrentShoppingItems =this.getAllListItemsForGroup(this.shoppingListWithItems);
  
        diffList = [
          ...this.getDifferenceList(this.shoppingListWithItems, list),
          ...this.getDifferenceList(list, this.shoppingListWithItems)
        ];
  
        diffItem = [
          ...this.getDifferenceItem(this.allCurrentShoppingItems, this.allShoppingItems),
          ...this.getDifferenceItem(this.allShoppingItems, this.allCurrentShoppingItems)
        ];
  
        this.updateList(this.shoppingListWithItems, list, diffList);
  
        this.updateItems(this.shoppingListWithItems, this.allShoppingItems, diffItem);
  
        this.requestTimeStamp = new Date().getTime();

        if (!this.pageLeft) {
          this.pollForList();
        }
      }, errRes => {
        if (errRes.status !== 404 && !this.pageLeft) {
          this.pollForList();
        }
      }) 
    }
  }

  ngOnDestroy() {
    this.pollSub.unsubscribe();
  }

  async refreshShoppingList(event?: any) {
    this.shoppingListService.getShoppingListsWithItems(this.activeGroup.id, this.INITIAL_REQUEST_TIMESTAMP).pipe(
      finalize(() => {
        if (event) {
          event.target.complete();
        }
      })
    ).subscribe(list => {
      this.shoppingListWithItems = list;
    })
  }

  ionViewWillEnter() {
    // console.log(this.activeGroup.id);
    this.pageLeft = false;
    if (this.activeGroup.id !== null) {
      this.getShoppingListWithItems(this.activeGroupId);
      this.pollForList();
    }
  }

  ionViewDidLeave() {
    this.pageLeft = true;
  }

  onCreateList() {
    this.alertCtrl.create({
      header: "Neue Einkaufsliste:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Erstelle Einkaufsliste..."
          }).then(loadingEl => {
            let newShoppingList = new AddEditShoppingListDto(null, data.listName, this.activeGroupId);
            this.shoppingListService.addShoppingList(newShoppingList).subscribe((list) => {
              loadingEl.dismiss();
              // this.getShoppingListWithItems(this.activeGroupId);
              let newShoppingListDto = new ShoppingListDto(
                list.id,
                list.name,
                this.shoppingItemsForNewList
              )
              this.shoppingListWithItems.push(newShoppingListDto);
              this.shoppingItemsForNewList = [];
            })
          })
        }
      }],
      inputs: [
        {
          name: "listName",
          placeholder: "Name der Einkaufsliste"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));

  }

  onUpdateList(list: ShoppingListDto, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: "Einkaufsliste bearbeiten:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Bearbeite Einkaufsliste..."
          }).then(loadingEl => {
            let updateShoppingList = new AddEditShoppingListDto(list.id, data.listName, this.activeGroupId);
            this.shoppingListService.updateShoppingList(updateShoppingList).subscribe((nList) => {
              loadingEl.dismiss();
              list.name = nList.name;
              // this.getShoppingListWithItems(this.activeGroupId);
            })
          })
        }
      }],
      inputs: [
        {
          name: "listName",
          value: list.name
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  onDeleteList(list: ShoppingListDto, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: "Löschen:",
      message: `Möchtest du die Einkaufsliste "${list.name}" wirklich löschen inkl. aller Einträge?`,
      buttons: [{
        text: "Nein",
        role: "cancel"
      }, {
        text: "Ja",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Lösche Einkaufsliste..."
          }).then(loadingEl => {
            let deleteShoppingList = new AddEditShoppingListDto(list.id, list.name, this.activeGroupId);
            this.shoppingListService.deleteShoppingList(deleteShoppingList).subscribe(() => {
              loadingEl.dismiss();
              this.shoppingListWithItems = this.shoppingListWithItems.filter(sList => sList.id !== list.id );
              // this.getShoppingListWithItems(this.activeGroupId);
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  onCreateItem(list: ShoppingListDto, indexList: number, indexItem: number) {
    let newItemName = list.shoppingItems[indexItem + 1].toString(); 
    
    let newItem = new AddEditShoppingItemDto(
      null, newItemName, false, list.id, this.activeGroupId
    );
    
    this.shoppingItemService.addItemToShoppingList(newItem).subscribe((item) => {
      list.shoppingItems[indexItem + 1] = null;
      list.shoppingItems.push(item);
      // this.getShoppingListWithItems(this.activeGroupId);
      setTimeout(() => document.querySelectorAll('ion-input')[indexList].setFocus(), 300);
      // document.querySelectorAll('ion-input')[indexList].setFocus();
    })
    
  }

  onUpdateItem(list: ShoppingListDto, item: ShoppingItemDto) {
    this.alertCtrl.create({
      header: "Eintrag bearbeiten:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Bearbeite Eintrag..."
          }).then(loadingEl => {
            let updateShoppingItem = new AddEditShoppingItemDto(
              item.id, data.itemName, item.completed, list.id, this.activeGroupId
            );
            this.shoppingItemService.updateItemOfShoppingList(updateShoppingItem).subscribe((item) => {
              loadingEl.dismiss();
              let updateItem = list.shoppingItems.filter(i => i.id == item.id)[0];
              updateItem.name = item.name;
              // this.getShoppingListWithItems(this.activeGroupId);
              
            })
          })
        }
      }],
      inputs: [
        {
          name: "itemName",
          value: item.name
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  onDeleteItem(list: ShoppingListDto, item: ShoppingItemDto) {
    let deleteShoppingItem = new AddEditShoppingItemDto(
      item.id, item.name, item.completed, list.id, this.activeGroupId
    );
    
    this.shoppingItemService.deleteItemFromShoppingList(deleteShoppingItem).subscribe(() => {
      let index = this.shoppingListWithItems.indexOf(
        this.shoppingListWithItems.filter(sList => sList.id == list.id)[0]
      )
      let newListItems = this.shoppingListWithItems[index].shoppingItems.filter(lItem => lItem.id !== item.id);
      this.shoppingListWithItems[index].shoppingItems = newListItems;
      // this.getShoppingListWithItems(this.activeGroupId);
    })
  }

  deleteList(sList: ShoppingListDto) {
    let newShoppingList = [];
    newShoppingList = this.shoppingListWithItems.filter(list => list.id == sList.id)
    newShoppingList[0].shoppingItems.forEach(item => {
      if (item.completed) {
        this.onDeleteItem(newShoppingList[0], item);
      }
    })
  }

  markAsDone(list: ShoppingListDto, item: ShoppingItemDto) {
    item.completed = !item.completed;
    let updateShoppingItem = new AddEditShoppingItemDto(
      item.id, item.name, item.completed, list.id, this.activeGroupId
    );
    this.shoppingItemService.updateItemOfShoppingList(updateShoppingItem).subscribe(() => {});
  }

  getCurrentUser() {
    // this.groupService.currentUser.subscribe(user => {
    //   this.userName = user.userName;
    // })
    this.authService.userName.subscribe(name => {
      this.userName = name;
    })
    return this.userName;
  }

  onCreateGroup() {
    this.alertService.createGroup();
  }

}

class ShoppingItem {
  constructor (
    public listId: number,
    public id : number,
    public name: string,
    public completed: boolean
  ) {}
}