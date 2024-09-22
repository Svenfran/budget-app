import { Component, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController } from '@ionic/angular';
import { CategoryDto } from '../models/category';
import { GroupSideNav } from '../models/group-side-nav';
import { CartService } from '../services/cart.service';
import { CategoryService } from '../services/category.service';
import { GroupService } from '../services/group.service';
import { AlertService } from '../services/alert.service';
import { StorageService } from '../services/storage.service';
import { from } from 'rxjs';

@Component({
  selector: 'app-categoryoverview',
  templateUrl: './categoryoverview.page.html',
  styleUrls: ['./categoryoverview.page.scss'],
})
export class CategoryoverviewPage implements OnInit {

  activeGroup: GroupSideNav;
  categories: CategoryDto[] = [];
  isLoading: boolean = false;

  constructor(
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private groupService: GroupService,
    private categoryService: CategoryService,
    private cartService: CartService,
    private alertService: AlertService,
    private storageService: StorageService
  ) { }

  ngOnInit() {
    this.getActiveGroupId();
  }

  getActiveGroupId() {
    this.groupService.activeGroup.subscribe(group => {
      this.isLoading = true;
      let groupId = null;
      if (group == null) {
        return from(this.storageService.getActiveGroup()).subscribe(actGr => {
          groupId = actGr.id;
        });
      } else {
        groupId = group.id;
      }
      this.categoryService.getCategoriesByGroup(groupId).subscribe((categories) => {
        this.activeGroup = group;
        this.categories = categories;
        this.isLoading = false;
      }, errRes => {
        this.isLoading = false;
        console.log(errRes.error);
      });
    }, errRes => {
      this.isLoading = false;
      console.log(errRes.error);
    });
  }


  onCreateCategory() {
    this.alertCtrl.create({
      header: "Neue Kategorie:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Erstelle Kategorie..."
          }).then(loadingEl => {
            let newCategory = new CategoryDto(null, data.categoryName, this.activeGroup.id);
            this.categoryService.addCategory(newCategory).subscribe((category) => {
              loadingEl.dismiss();
              this.categories.push(category);
              this.categories.sort((a, b) => (a.name < b.name ? -1 : 1))
            }, errRes => {
              if (errRes.status === 0) {
                loadingEl.dismiss();
                this.alertService.showAlertSeverUnavailable();
              }
            })
          })
        }
      }],
      inputs: [
        {
          name: "categoryName",
          placeholder: "Name der Kategorie"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  onUpdateCategory(category: CategoryDto) {
    this.alertCtrl.create({
      header: "Kategorie bearbeiten:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Bearbeite Kategorie..."
          }).then(loadingEl => {
            let updateCategory = new CategoryDto(category.id, data.categoryName, category.groupId);
            this.categoryService.updateCategory(updateCategory).subscribe((category) => {
              loadingEl.dismiss();
              let updateCategory = this.categories.filter(c => c.id == category.id)[0];
              updateCategory.name = category.name;
              this.categories.sort((a, b) => (a.name < b.name ? -1 : 1))
              this.cartService.setCartModified(true);
            }, errRes => {
              if (errRes.status === 0) {
                loadingEl.dismiss();
                this.alertService.showAlertSeverUnavailable();
              }
            })
          })
        }
      }],
      inputs: [
        {
          name: "categoryName",
          value: category.name
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  onDeleteCategory(category: CategoryDto) {
    this.alertCtrl.create({
      header: "Löschen",
      message: `Möchtest du die Kategorie "${category.name}" wirklich löschen?`,
      buttons: [{
        text: "Nein",
        role: "cancel"
      }, {
        text: "Ja",
        handler: () => {
          this.loadingCtrl.create({
            message: "Lösche Kategorie..."
          }).then(loadingEl => {
            let deleteCategory = new CategoryDto(category.id, category.name, category.groupId);
            this.categoryService.deleteCategory(deleteCategory).subscribe(() => {
              loadingEl.dismiss();
              this.categories = this.categories.filter(cat => cat.id !== category.id );
            }, errRes => {
              if (errRes.status === 0) {
                loadingEl.dismiss();
                this.alertService.showAlertSeverUnavailable();
              } else if (errRes.status === 404) {
                loadingEl.dismiss();
                let header = "Löschen fehlgeschlagen";
                let message = "Kategorie kann nicht gelöscht werden, diese ist bereits einigen deiner Ausgaben zugeordnet!"
                this.alertService.showAlert(header, message);
              }
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }
}
