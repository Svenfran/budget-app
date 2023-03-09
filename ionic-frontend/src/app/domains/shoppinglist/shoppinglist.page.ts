import { Component, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController } from '@ionic/angular';
import { finalize } from 'rxjs/operators';
import { AddEditShoppingItemDto } from 'src/app/models/add-edit-shopping-item-dto';
import { AddEditShoppingListDto } from 'src/app/models/add-edit-shopping-list-dto';
import { GroupSideNav } from 'src/app/models/group-side-nav';
import { ShoppingItemDto } from 'src/app/models/shopping-item-dto';
import { ShoppingListDto } from 'src/app/models/shopping-list-dto';
import { GroupService } from 'src/app/services/group.service';
import { ShoppingitemService } from 'src/app/services/shoppingitem.service';
import { ShoppinglistService } from 'src/app/services/shoppinglist.service';

@Component({
  selector: 'app-shoppinglist',
  templateUrl: './shoppinglist.page.html',
  styleUrls: ['./shoppinglist.page.scss'],
})
export class ShoppinglistPage implements OnInit {

  activeGroupName: string;
  activeGroupId: number;
  activeGroup: GroupSideNav;
  userName: string;
  shoppingListWithItems: ShoppingListDto[] = [];
  toggleLists: any = {};
  itemName = "";
  isLoading: boolean = false;
  focusIsSet: boolean;

  constructor(
    private groupService: GroupService,
    private shoppingListService: ShoppinglistService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private shoppingItemService: ShoppingitemService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.groupService.activeGroup.subscribe(group => {
      if (group) {
        this.activeGroupName = group.name;
        this.activeGroupId = group.id;
        this.activeGroup = group;
        this.getShoppingListWithItems(group.id);
      }
    })
  }

  getShoppingListWithItems(groupId: number) {
    this.isLoading = true;
    this.shoppingListService.getShoppingListsWithItems(groupId).subscribe(list => {
      this.shoppingListWithItems = list;
      this.isLoading = false;
    })
  }

  async refreshShoppingList(event?: any) {
    this.shoppingListService.getShoppingListsWithItems(this.activeGroup.id).pipe(
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
    this.getShoppingListWithItems(this.activeGroupId);
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
            this.shoppingListService.addShoppingList(newShoppingList).subscribe(() => {
              loadingEl.dismiss();
              this.getShoppingListWithItems(this.activeGroupId);
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
            this.shoppingListService.updateShoppingList(updateShoppingList).subscribe(() => {
              loadingEl.dismiss();
              this.getShoppingListWithItems(this.activeGroupId);
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
    
    this.shoppingItemService.addItemToShoppingList(newItem).subscribe(() => {
      list.shoppingItems[indexItem + 1] = null;
      this.getShoppingListWithItems(this.activeGroupId);
      setTimeout(() => document.querySelectorAll('ion-input')[indexList].setFocus(), 300);
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
            this.shoppingItemService.updateItemOfShoppingList(updateShoppingItem).subscribe(() => {
              loadingEl.dismiss();
              this.getShoppingListWithItems(this.activeGroupId);
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
    this.groupService.currentUser.subscribe(user => {
      this.userName = user.userName;
    })
  }

}
