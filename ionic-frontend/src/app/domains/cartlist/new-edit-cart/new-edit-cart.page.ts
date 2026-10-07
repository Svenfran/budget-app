import { Component, effect, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonDatetime, LoadingController, MenuController } from '@ionic/angular';
import { CategoryService } from 'src/app/service/category.service';
import { GroupService } from 'src/app/service/group.service';
import { CartService } from '../service/cart.service';
import { Cart } from '../model/cart';
import { User } from 'src/app/auth/user';
import { AuthService } from 'src/app/auth/auth.service';
import moment from 'moment';
import { CategoryDto } from 'src/app/model/category-dto';
import { INIT_VALUES } from 'src/app/constants/default-values';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { TranslateService } from '@ngx-translate/core';
import { LanguageService } from 'src/app/service/language.service';


@Component({
  selector: 'app-new-edit-cart',
  templateUrl: './new-edit-cart.page.html',
  styleUrls: ['./new-edit-cart.page.scss'],
  standalone: false
})
export class NewEditCartPage implements OnInit {
  @ViewChild(IonDatetime) datetime!: IonDatetime;

  public isAddMode: boolean = true;
  public cartId: string | null = null;
  public form!: FormGroup;
  public showPicker = false;
  public dateValue = "";
  public formattedString = "";
  public minDate = "";
  public maxDate = "";
  public today = new Date();
  public user!: User;
  public categories = this.categoryService.categories;
  public activeGroup = this.groupService.activeGroup();
  public cartList = this.cartService.cartList;
  public zeitraeume = this.groupService.groupMembershipHistory;
  public recurrenceTypes = [
    { value: RECURRENCE_TYPE.DAILY, label: this.translate.instant("carts.recurrence_types.daily") },
    { value: RECURRENCE_TYPE.WEEKLY, label: this.translate.instant("carts.recurrence_types.weekly") },
    { value: RECURRENCE_TYPE.MONTHLY, label: this.translate.instant("carts.recurrence_types.monthly") },
    { value: RECURRENCE_TYPE.YEARLY, label: this.translate.instant("carts.recurrence_types.yearly") },
    { value: RECURRENCE_TYPE.NONE, label: this.translate.instant("carts.recurrence_types.none") }
  ];
  public hasActiveTemplate: boolean = false;
  public nextExecutionDate: Date | null = null;
  public currentLang = this.langService.currentLang;

  constructor(
    private route: ActivatedRoute,
    private fb: FormBuilder,
    private categoryService: CategoryService,
    private groupService: GroupService,
    private cartService: CartService,
    private authService: AuthService,
    private menuCtrl: MenuController,
    private loadingCtrl: LoadingController,
    private router: Router,
    private translate: TranslateService,
    private langService: LanguageService
  ) {
    effect(() => {
      const activeGroup = this.groupService.activeGroup();
      if (!activeGroup.flag?.includes(INIT_VALUES.DEFAULT)) {
        this.groupService.getGroupMembershipHistoryForGroupAndUser(activeGroup);
      }
    })
  }

  ngOnInit() {
    this.initializeForm();
    this.categoryService.getCategoriesByGroup(this.activeGroup);

    this.cartId = this.route.snapshot.paramMap.get('id');
    this.isAddMode = !this.cartId;

    this.authService.user.subscribe(user => {
      if (user) this.user = user;
    })

    this.setInitialFormValues();
  }

  ionViewWillLeave() {
    this.menuCtrl.enable(true, 'm1');
  }

  ionViewWillEnter() {
    this.menuCtrl.enable(false, 'm1');
  }

  initializeForm() {
    this.form = this.fb.group({
      id:[''],
      title: ['',[ Validators.required ]],
      amount: ['',[ Validators.required, Validators.pattern('[+-]?([0-9]*[.])?[0-9]+') ]],
      description: [''],
      datePurchased: ['',[ Validators.required, Validators.pattern('(0[1-9]|1[0-9]|2[0-9]|3[01]).(0[1-9]|1[012]).[0-9]{4}')]],
      categoryId: ['',[ Validators.required ]],
      recurrenceType: [''],
      templateUpdateSelected: [false]
    });
  }

  get title() {return this.form.get('title');}
  get description() {return this.form.get('description');}
  get amount() {return this.form.get('amount');}
  get datePurchased() {return this.form.get('datePurchased');}
  get categoryId() {return this.form.get('category');}
  get recurrenceType() {return this.form.get('recurrenceType');}
  get templateUpdateSelected() {return this.form.get('templateUpdateSelected');}


  onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.isAddMode) {
      this.onCreateCart();
    } else {
      this.onUpdateCart();
    }
  }

  onCreateCart() {
    this.loadingCtrl.create({
      message: this.translate.instant("carts.loading_add")
    }).then(loadingEl => {
      const category = this.categories().find(cat => cat.id === this.form.value.categoryId)!;
      const categoryDto: CategoryDto = {
        id: category.id,
        name: category.name,
        groupId: category.groupId
      }

      const newCart: Cart = {
        id: null,
        title: this.form.value.title.trim(),
        description: this.form.value.description.trim(),
        amount: this.form.value.amount,
        datePurchased: this.getDateFromString(this.form.value.datePurchased),
        groupId: this.activeGroup.id,
        userDto: null!,
        categoryDto: categoryDto,
        recurrenceType: this.form.value.recurrenceType
      }
      this.cartService.addCart(newCart);
      loadingEl.dismiss();
      this.form.reset();
      this.router.navigate(["domains", "tabs", "cartlist"])
    })
  }

  onUpdateCart() {
    this.loadingCtrl.create({
      message: this.translate.instant("carts.loading_update")
    }).then(loadingEl => {
      const category = this.categories().find(cat => cat.id === this.form.value.categoryId)!;
      const categoryDto: CategoryDto = {
        id: category.id,
        name: category.name,
        groupId: category.groupId
      }
      const updatedCart: Cart = {
        id: this.form.value.id,
        title: this.form.value.title.trim(),
        description: this.form.value.description.trim(),
        amount: this.form.value.amount,
        datePurchased: this.getDateFromString(this.form.value.datePurchased),
        groupId: this.activeGroup.id,
        userDto: null!,
        categoryDto: categoryDto,
        recurrenceType: this.form.value.recurrenceType,
        hasActiveTemplate: this.hasActiveTemplate,
        templateUpdateSelected: this.form.value.templateUpdateSelected
      }
      this.cartService.updateCart(updatedCart);
      loadingEl.dismiss();
      this.form.reset();
      this.router.navigate(["domains", "tabs", "cartlist"])
    })
  }

  setInitialFormValues() {
    this.setToday();

    this.minDate = moment(this.activeGroup.dateCreated).format('YYYY-MM-DD') + 'T00:00:00';
    // maxDate is today because adding a cart in the future and leaving the group before the date leads to a problem
    this.maxDate = moment().format('YYYY-MM-DD') + 'T00:00:00';

    if (!this.isAddMode) {
      const cart: Cart = this.cartList().find(c => c.id === +this.cartId!)!;
      this.hasActiveTemplate = cart.hasActiveTemplate || false;
      if (cart.recurrenceType && cart.hasActiveTemplate && cart.recurrenceType !== RECURRENCE_TYPE.NONE) {
        this.nextExecutionDate = cart.nextExecutionDate!;
      }
      this.setDate(cart.datePurchased)
      this.form.patchValue({
        id: cart.id,
        title: cart.title,
        amount: cart.amount.toFixed(2),
        description: cart.description,
        datePurchased: this.formattedString,
        categoryId: cart.categoryDto.id,
        recurrenceType: cart.hasActiveTemplate ? cart.recurrenceType : RECURRENCE_TYPE.NONE
      })
    } else if (this.isAddMode) {
      this.form.patchValue({
        datePurchased: this.formattedString,
        recurrenceType: RECURRENCE_TYPE.NONE
      });
    }
  }

  formatToISODate(date: Date | string): string {
    return new Date(date).toISOString().split('T')[0] + 'T00:00:00';
  }

  setToday() {
    this.formattedString = moment().format('DD.MM.YYYY');
    this.dateValue = moment().startOf('day').format('YYYY-MM-DD') + 'T00:00:00';
  }
  
  setDate(date: Date) {
    this.formattedString = moment(date).format('DD.MM.YYYY');
    this.dateValue = moment(date).startOf('day').format('YYYY-MM-DD') + 'T00:00:00';
  }
  
  dateChanged(value: string | string[] | null | undefined) {
    if (Array.isArray(value)) {
      value = value[0];
    }

    if (value) {
      this.formattedString = moment(value).format('DD.MM.YYYY');
      this.dateValue = moment(value).startOf('day').format('YYYY-MM-DD') + 'T00:00:00';
      this.form.controls['datePurchased'].setValue(this.formattedString);
      this.showPicker = false;
    }

    this.updateNextExecutionDate();
  }

  updateNextExecutionDate() {
    const lastDate = this.getDateFromString(this.form.value.datePurchased);

    if (this.isAddMode && this.form.value.recurrenceType !== RECURRENCE_TYPE.NONE) {
      this.calculateNextDate(lastDate, this.form.value.recurrenceType);
      return;
    }

    // Edit-Mode
    const cart: Cart | undefined = this.cartList().find(c => c.id === +this.cartId!);
    if (!cart) return;

    const changedRecurrenceType = this.form.value.recurrenceType;
    const hasRecurrenceChanged = cart.recurrenceType !== changedRecurrenceType;
    const updateTemplate = this.form.value.templateUpdateSelected;

    if (hasRecurrenceChanged || updateTemplate) {
      this.calculateNextDate(lastDate, this.form.value.recurrenceType);
    } else {
      this.nextExecutionDate = cart.nextExecutionDate!;
    }
  }

  recurrenceTypeChanged(value: RECURRENCE_TYPE) {
    if (!value || value === RECURRENCE_TYPE.NONE) {
      return;
    }
    this.updateNextExecutionDate();
  }

  templateUpdateSelectedChange() {
    this.updateNextExecutionDate();
  }

  close() {
    this.datetime.cancel(true);
  }

  select() {
    this.datetime.confirm(true);
  }

  getDateFromString(formattedString: string) {
    return new Date(formattedString.replace(/(\d{2}).(\d{2}).(\d{4})/, "$3-$2-$1"));
  }


  isDateSelectable = (dateIsoString: string) => {
    const date = new Date(this.formatDateString(dateIsoString));
    return this.zeitraeume().some(zeitraum => {

      const startDate = new Date(this.formatDateString(zeitraum.startDate.toString()));
      const endDate = zeitraum.endDate ? new Date(this.formatDateString(zeitraum.endDate.toString())) : null;

      const result =
        date >= startDate &&
        (endDate === null || date <= endDate) &&
        zeitraum.userId === this.user.id &&
        zeitraum.groupId === this.activeGroup.id;

      return result;
    });
  };

  formatDateString(dateString: string) {
    const date = new Date(dateString);
    return date.toISOString().split('T')[0] + 'T00:00:00'
  }

  private calculateNextDate(lastExecutionDate: Date, recurrenceType: RECURRENCE_TYPE): void {
    let next = new Date(lastExecutionDate);
    const today = new Date();

    switch (recurrenceType) {
      case RECURRENCE_TYPE.DAILY:
        next.setDate(next.getDate() + 1);
        while (next <= today) {
          next.setDate(next.getDate() + 1);
        }
        break;

      case RECURRENCE_TYPE.WEEKLY:
        next.setDate(next.getDate() + 7);
        while (next <= today) {
          next.setDate(next.getDate() + 7);
        }
        break;

      case RECURRENCE_TYPE.MONTHLY:
        next.setMonth(next.getMonth() + 1);
        while (next <= today) {
          next.setMonth(next.getMonth() + 1);
        }
        break;

      case RECURRENCE_TYPE.YEARLY:
        next.setFullYear(next.getFullYear() + 1);
        while (next <= today) {
          next.setFullYear(next.getFullYear() + 1);
        }
        break;

      default:
        throw new Error("Unsupported recurrence type: " + recurrenceType);
    }
    this.nextExecutionDate = next;
  }

}
