import { Component, effect, OnInit } from '@angular/core';
import { CategoryService } from '../service/category.service';
import { CategoryDto } from '../model/category-dto';
import { GroupService } from '../service/group.service';
import { AlertController, LoadingController } from '@ionic/angular';
import { INIT_NUMBERS, INIT_VALUES, SETTLEMENTPAYMENT_CATEGORIES } from '../constants/default-values';
import { CartService } from '../domains/cartlist/service/cart.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-categoryoverview',
  templateUrl: './categoryoverview.page.html',
  styleUrls: ['./categoryoverview.page.scss'],
  standalone: false
})
export class CategoryoverviewPage implements OnInit {

  public categories = this.categoryService.categories;
  public isLoading: boolean = false;
  public activeGroup = this.groupService.activeGroup;

  constructor(
    private categoryService: CategoryService,
    private groupService: GroupService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private cartService: CartService,
    private translate: TranslateService
  ) { 
    effect(() => {
      this.activeGroup = this.groupService.activeGroup;
      this.isLoading = true;
      if (!this.activeGroup().flag?.includes(INIT_VALUES.DEFAULT)) {
        this.categoryService.getCategoriesByGroup(this.activeGroup());
      }
      this.isLoading = false;
    });
  }

  ngOnInit() {
  }

  refreshCategories(event: CustomEvent) {
    setTimeout(() => {
      this.categoryService.getCategoriesByGroup(this.activeGroup());
      (event.target as HTMLIonRefresherElement).complete();
    }, 2000);
  }

  isSettlementpayment(category: string): boolean {
    return this.categoryService.isSettlementpayment(category);
  }

  onCreateCategory() {
    this.alertCtrl.create({
      header: this.translate.instant("alerts.category.new.header"),
      buttons: [{
        text: this.translate.instant("alerts.category.new.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.category.new.ok"),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.category.new.loading")
          }).then(loadingEl => {
            if (!data) return;
            const trimmedCategoryName = data.categoryName.trim();
            if (trimmedCategoryName === "") return;

            const newCategory: CategoryDto = {
              name: trimmedCategoryName,
              groupId: this.activeGroup().id
            };
            this.categoryService.addCategory(newCategory);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "categoryName",
          placeholder: this.translate.instant("alerts.category.new.placeholder"),
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

  onUpdateCategory(category: CategoryDto) {
    this.alertCtrl.create({
      header: this.translate.instant("alerts.category.edit.header"),
      buttons: [{
        text: this.translate.instant("alerts.category.edit.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.category.edit.ok"),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.category.edit.loading")
          }).then(loadingEl => {
            const foundCategory = this.categories().find(c => c.id === category.id);

            if (!data) return;
            const trimmedCategoryName = data.categoryName.trim();
            if (trimmedCategoryName === "") return;

            const updatedCategory: CategoryDto = {
              id: foundCategory?.id,
              name: trimmedCategoryName,
              groupId: foundCategory?.groupId!
            };
            this.categoryService.updateCategory(updatedCategory);
            this.cartService.triggerUpdate();
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "categoryName",
          value: category.name,
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
  
  onDeleteCategory(category: CategoryDto) {
    this.alertCtrl.create({
      header: this.translate.instant("alerts.category.delete.header"),
      message: this.translate.instant("alerts.category.delete.message", {categoryName: category.name}),
      buttons: [{
        text: this.translate.instant("alerts.category.delete.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.category.delete.ok"),
        handler: () => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.category.delete.loading")
          }).then(loadingEl => {
            const deletedCategory = this.categories().find(c => c.id === category.id);
            this.categoryService.deleteCategory(deletedCategory!);
            loadingEl.dismiss();
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }


}
