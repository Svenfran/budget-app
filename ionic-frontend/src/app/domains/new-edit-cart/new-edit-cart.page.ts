import { Component, OnInit, ViewChild } from '@angular/core';
import { FormGroup, UntypedFormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { IonDatetime, LoadingController, MenuController } from '@ionic/angular';
import { CategoryDto } from 'src/app/models/category';
import { first } from 'rxjs/operators';
import { CartService } from 'src/app/services/cart.service';
import { CategoryService } from 'src/app/services/category.service';
import { Cart } from 'src/app/models/cart';
import { format, parseISO } from 'date-fns'
import { GroupService } from 'src/app/services/group.service';
import { Group } from 'src/app/models/group';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { Zeitraum } from 'src/app/models/zeitraum';
import { AlertService } from 'src/app/services/alert.service';


@Component({
  selector: 'app-new-edit-cart',
  templateUrl: './new-edit-cart.page.html',
  styleUrls: ['./new-edit-cart.page.scss'],
})
export class NewEditCartPage implements OnInit {

  form: FormGroup;
  categories: CategoryDto[] = [];
  today = new Date();
  isAddMode: boolean;
  cartId: String;
  showPicker = false;
  dateValue = "";
  formattedString = "";
  minDate = "";
  maxDate = "";
  activeGroupId: number;
  activeGroup: Group;
  selectedDate: string;
  user: User;
  zeitraeume: Zeitraum[] = [];

  @ViewChild(IonDatetime) datetime: IonDatetime;
  constructor(
    private categoryService: CategoryService,
    private fb: UntypedFormBuilder,
    private cartService: CartService,
    private router: Router,
    private loadingCtrl: LoadingController,
    private route: ActivatedRoute,
    private groupService: GroupService,
    private menuCtrl: MenuController,
    private authService: AuthService,
    private alertService: AlertService
  ) { }

  ngOnInit() {
    this.form = this.fb.group({
      id:[''],
      title: ['',[ Validators.required ]],
      amount: ['',[ Validators.required, Validators.pattern('[+-]?([0-9]*[.])?[0-9]+')]],
      description: [''],
      datePurchased: ['',[ Validators.required, Validators.pattern('(0[1-9]|1[0-9]|2[0-9]|3[01]).(0[1-9]|1[012]).[0-9]{4}')]],
      category: ['',[ Validators.required ]]
    });

    this.cartId = this.route.snapshot.paramMap.get('id');
    this.isAddMode = !this.cartId;
 
    this.setToday();
    this.setInitialFormValues();
    this.getActiveGroupId();
    this.currentUser();
  }

  ionViewWillLeave() {
    this.menuCtrl.enable(true, 'm1');
  }

  ionViewWillEnter() {
    this.menuCtrl.enable(false, 'm1');
  }

  getActiveGroupId() {
    this.groupService.activeGroup.subscribe(group => {
      this.categoryService.getCategoriesByGroup(group.id).subscribe((categories) => {
        this.activeGroupId = group.id;
        this.activeGroup = group;
        this.categories = categories;
        this.minDate = format(new Date(group.dateCreated), 'yyyy-MM-dd') + 'T00:00:00';
        this.maxDate = format(new Date().setFullYear(new Date().getFullYear() + 1), 'yyyy-MM-dd') + 'T00:00:00';
        this.groupService.getGroupMembershipHistoryForGroupAndUser(group.id).subscribe(gmh => {
          this.zeitraeume = gmh.map(item => ({ 
            startDate: new Date(this.removeTimeFromDate(item.startDate)),
            endDate: item.endDate ? new Date(this.removeTimeFromDate(item.endDate)) : null, 
            groupId: item.groupId, 
            userId: item.userId
          }));
        });
      });
    });
  }

  removeTimeFromDate(date: Date): string {
    return date.toString().split('T')[0] + 'T00:00:00';
  }

  setInitialFormValues() {
    if (!this.isAddMode) {
      this.cartService.getCartById(+this.cartId)
      .pipe(first())
      .subscribe(data => {
        this.setDate(data.datePurchased);
        this.form.patchValue({
          id: data.id,
          title: data.title,
          amount: data.amount.toFixed(2),
          description: data.description,
          datePurchased: this.formattedString,
          category: data.categoryDto.name
        })
      });
    } else if (this.isAddMode) {
      this.form.patchValue({
        datePurchased: this.formattedString
      });
    }
  }

  setToday() {
    this.formattedString = format(parseISO(format(this.today, 'yyyy-MM-dd') + 'T00:00:00.000Z'), 'dd.MM.yyyy');
    this.dateValue = format(this.today, 'yyyy-MM-dd') + 'T00:00:00';
  }

  setDate(date: Date) {
    this.formattedString = format(parseISO(format(new Date(date), 'yyyy-MM-dd') + 'T00:00:00.000Z'), 'dd.MM.yyyy');
    this.dateValue = format(new Date(date), 'yyyy-MM-dd') + 'T00:00:00';
  }

  dateChanged(value: string) {
    this.formattedString = format(parseISO(value), 'dd.MM.yyyy');
    this.dateValue = value;
    this.showPicker = false;
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


  // Diese Methode prüft, ob ein Datum innerhalb der erlaubten Zeiträume liegt
  isDateSelectable = (dateIsoString: string) => {
    const date = new Date(this.formatDateString(dateIsoString));
    // Durchlaufe alle Zeiträume und prüfe, ob das Datum in einem der Zeiträume liegt
    return this.zeitraeume.some(zeitraum => {
      return date >= zeitraum.startDate && (date <= zeitraum.endDate || this.dateIsNull(zeitraum.endDate)) && zeitraum.userId == this.user.id && zeitraum.groupId == this.activeGroupId;
    });
  };

  formatDateString(dateString: string) {
    const [day, month, year] = dateString.split('.');
    return `${year}-${month}-${day}`
  }

  dateIsNull(date: Date) {
    if (date == null) {
      return true
    };
  }

  onCreateCart() {
    this.loadingCtrl.create({
      message: 'Füge Einkauf hinzu...'
    }).then(loadingEl => {
      loadingEl.present();
      let newCategory = new CategoryDto(
        this.categories.filter(cat => cat.name === this.form.value.category)[0].id,
        this.form.value.category,
        this.categories.filter(cat => cat.name === this.form.value.category)[0].groupId
      );
      let newCart = new Cart(
        null,
        this.form.value.title,
        this.form.value.description,
        this.form.value.amount,
        this.getDateFromString(this.form.value.datePurchased),
        this.activeGroupId,
        null,
        newCategory
      );
      this.cartService.addCart(newCart).subscribe(() => {
          loadingEl.dismiss();
          this.form.reset();
          this.router.navigate(['domains/tabs/cartlist']);
        }, errRes => {
          if (errRes.error.includes('not within membership period')) {
            this.alertService.showAlert(
              'Datum nicht im Zeitraum der Mitgliedschaft',
              'Du warst zu dem gewählten Zeitpunkt kein Mitglied der Gruppe. Bitte wähle ein anderes Datum.'
            )
          }
        }
      )
    })
  }

  onUpdateCart() {
    this.loadingCtrl.create({
      message: 'Bearbeite Einkauf...'
    }).then(loadingEl => {
      let updateCategory = new CategoryDto(
        this.categories.filter(cat => cat.name === this.form.value.category)[0].id,
        this.form.value.category,
        this.categories.filter(cat => cat.name === this.form.value.category)[0].groupId
      );
      let updateCart = new Cart(
        +this.cartId,
        this.form.value.title,
        this.form.value.description,
        this.form.value.amount,
        this.getDateFromString(this.form.value.datePurchased),
        this.activeGroupId,
        null,
        updateCategory
      );
      this.cartService.updateCart(updateCart).subscribe(() => {
        loadingEl.dismiss();
        this.form.reset();
        this.router.navigate(['domains/tabs/cartlist']);
      }, errRes => {
        if (errRes.error.includes('not within membership period')) {
          this.alertService.showAlert(
            'Datum nicht im Zeitraum der Mitgliedschaft',
            'Du warst zu dem gewählten Zeitpunkt kein Mitglied der Gruppe. Bitte wähle ein anderes Datum.'
          )
        }
      })
    })
  }

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

  currentUser() {
    this.authService.user.subscribe(user => {
      this.user = user;
    })
  }

  get title() {return this.form.get('title');}
  get description() {return this.form.get('description');}
  get amount() {return this.form.get('amount');}
  get datePurchased() {return this.form.get('datePurchased');}
  get category() {return this.form.get('category');}

}
