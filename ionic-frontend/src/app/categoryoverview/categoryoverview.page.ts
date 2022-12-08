import { Component, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController } from '@ionic/angular';
import { CategoryDto } from '../models/category';
import { GroupSideNav } from '../models/group-side-nav';
import { CartService } from '../services/cart.service';
import { CategoryService } from '../services/category.service';
import { GroupService } from '../services/group.service';

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
    private cartService: CartService
  ) { }

  ngOnInit() {
    this.getActiveGroupId();
  }

  getActiveGroupId() {
    this.groupService.activeGroup.subscribe(group => {
      this.isLoading = true;
      this.categoryService.getCategoriesByGroup(group.id).subscribe((categories) => {
        this.activeGroup = group;
        this.categories = categories;
        this.isLoading = false;
      });
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
            this.categoryService.addCategory(newCategory).subscribe(() => {
              loadingEl.dismiss();
              this.categoryService.getCategoriesByGroup(this.activeGroup.id).subscribe(categories => {
                this.categories = categories;
              });
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
            this.categoryService.updateCategory(updateCategory).subscribe(() => {
              loadingEl.dismiss();
              this.categoryService.getCategoriesByGroup(this.activeGroup.id).subscribe(categories => {
                this.categories = categories;
              });
              this.cartService.setCartModified(true);
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
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: () => {
          this.loadingCtrl.create({
            message: "Lösche Kategorie..."
          }).then(loadingEl => {
            let deleteCategory = new CategoryDto(category.id, category.name, category.groupId);
            this.categoryService.deleteCategory(deleteCategory).subscribe(() => {
              loadingEl.dismiss();
              this.categories = this.categories.filter(cat => cat.id !== category.id );
            }, errRes => {
              loadingEl.dismiss();
              let message = "Kategorie kann nicht gelöscht werden, diese ist bereits einigen deiner Ausgaben zugeordnet!"
              this.showAlert(message);
              console.log(errRes.error);
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  private showAlert(message: string) {
    this.alertCtrl.create({
      header: "Löschen fehlgeschlagen",
      message: message,
      buttons:["Ok"]
    }).then(alertEl => alertEl.present());
  }
}
