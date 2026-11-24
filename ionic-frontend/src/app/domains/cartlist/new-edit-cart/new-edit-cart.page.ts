import { Component, computed, effect, OnInit, signal, ViewChild } from '@angular/core';
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
import { calculateNextExecutionDate } from './next-execution-date';
import { duplicateFormValues, duplicateSource } from './duplicate-cart';
import { amountFromInput, amountInputText, formattedAmountInput } from 'src/app/service/amount';
import { currencySymbol } from 'src/app/pipe/scientific.pipe';


@Component({
  selector: 'app-new-edit-cart',
  templateUrl: './new-edit-cart.page.html',
  styleUrls: ['./new-edit-cart.page.scss'],
  standalone: false
})
export class NewEditCartPage implements OnInit {
  @ViewChild(IonDatetime) datetime!: IonDatetime;

  public isAddMode: boolean = true;
  /** Neuanlage mit Werten einer eigenen Ausgabe vorbelegt (DIV-73, Query-Parameter `duplicateOf`). */
  public isDuplicate: boolean = false;
  /** Gesetzt ab dem Absenden bis zum Verlassen der Seite: verhindert doppeltes Speichern. */
  public saving: boolean = false;
  public cartId: string | null = null;
  public form!: FormGroup;
  public showPicker = false;
  public dateValue = "";
  public formattedString = "";
  public minDate = "";
  public maxDate = "";
  public today = new Date();
  public user!: User;
  /**
   * Kategorien aus genau der eigenen Anfrage dieser Seite (`null` = noch nicht erfolgreich geladen). Bewusst nicht das
   * gemeinsame `categoryService.categories`: Das können ältere Anfragen anderer Seiten später noch überschreiben.
   */
  public formCategories = signal<CategoryDto[] | null>(null);
  public categoriesLoaded = computed(() => this.formCategories() !== null);
  /**
   * Auswählbare Kategorien: immer ohne die Settlement-Kategorie (DIV-74, per Kennzeichen). Ausgleichszahlungen entstehen
   * und ändern sich nur über das Settlement-Sheet; das Backend lehnt die Kategorie für normale Einkäufe ab.
   */
  public selectableCategories = computed(() => (this.formCategories() ?? []).filter(cat => !cat.settlement));
  public activeGroup = this.groupService.activeGroup();
  public cartList = this.cartService.cartList;
  public zeitraeume = this.groupService.groupMembershipHistory;
  /** Währungssymbol im Betrags-Label (DIV-77) */
  public currencySymbol = computed(() => currencySymbol(this.groupService.activeCurrency()));
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

    this.cartId = this.route.snapshot.paramMap.get('id');
    this.isAddMode = !this.cartId;

    // Ausgleichszahlungen nicht über das normale Formular bearbeiten (DIV-74), z. B. bei direktem Aufruf der URL
    if (!this.isAddMode && this.cartList().find(c => c.id === +this.cartId!)?.settlementId) {
      this.router.navigate(['domains', 'tabs', 'cartlist']);
      return;
    }

    this.authService.user.subscribe(user => {
      if (user) this.user = user;
    })

    this.setInitialFormValues();

    // Nach der Vorbelegung laden: Der Callback prüft die vorbelegte Kategorie gegen das Ergebnis dieser Anfrage.
    this.categoryService.getCategoriesByGroup(this.activeGroup, result => {
      if (result === null) return;
      this.formCategories.set(result);
      if (this.isDuplicate) this.clearUnknownCategory();
    });
  }

  ionViewWillLeave() {
    this.menuCtrl.enable(true, 'm1');
  }

  ionViewWillEnter() {
    this.menuCtrl.enable(false, 'm1');
    this.saving = false;
  }

  initializeForm() {
    this.form = this.fb.group({
      id:[''],
      title: ['',[ Validators.required ]],
      // Punkt oder Komma, Trennzeichen am Ende erlaubt (Textfeld, DIV-77); Vorzeichen für bestehende negative Einkäufe
      amount: ['',[ Validators.required, Validators.pattern('[+-]?(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)') ]],
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
  get categoryId() {return this.form.get('categoryId');}
  get recurrenceType() {return this.form.get('recurrenceType');}
  get templateUpdateSelected() {return this.form.get('templateUpdateSelected');}


  /**
   * Betrag auf Cent runden und mit zwei Nachkommastellen sichtbar ins Feld schreiben (DIV-77, D9): beim Verlassen des
   * Feldes und vor dem Absenden, einheitlich wie beim Vorbelegen im Bearbeiten-Modus und in der Ausgleichszahlung
   * („45.“/„45,“ → „45.00“, „45,5“ → „45.50“). Ungültige Eingaben bleiben unverändert (der Formatvalidator meldet sie).
   */
  roundAmount() {
    const formatted = formattedAmountInput(this.amount?.value);
    if (formatted !== null && formatted !== this.amount?.value) this.amount?.setValue(formatted);
  }

  /**
   * Reihenfolge (DIV-73): Betrag runden (DIV-77) → Kategorien der eigenen Anfrage geladen → Formular gültig → Kategorie vorhanden → nicht bereits
   * gesendet. Alle Abbrüche liegen vor dem Setzen von `saving`, die Sperre bleibt also nie nach einer Ablehnung stehen.
   */
  onSubmit() {
    this.roundAmount();
    if (!this.categoriesLoaded()) return;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const category = this.selectableCategories().find(cat => cat.id === this.form.value.categoryId);
    if (!category) {
      this.categoryId?.setValue('');
      this.form.markAllAsTouched();
      return;
    }

    if (this.saving) return;
    this.saving = true;

    const categoryDto: CategoryDto = {
      id: category.id,
      name: category.name,
      groupId: category.groupId
    }

    if (this.isAddMode) {
      this.onCreateCart(categoryDto);
    } else {
      this.onUpdateCart(categoryDto);
    }
  }

  onCreateCart(categoryDto: CategoryDto) {
    this.loadingCtrl.create({
      message: this.translate.instant("carts.loading_add")
    }).then(loadingEl => {
      const newCart: Cart = {
        id: null,
        title: this.form.value.title.trim(),
        description: this.form.value.description.trim(),
        // Feld zeigt „42,45“ (Komma), der Request braucht die Zahl
        amount: amountFromInput(this.form.value.amount)!,
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

  onUpdateCart(categoryDto: CategoryDto) {
    this.loadingCtrl.create({
      message: this.translate.instant("carts.loading_update")
    }).then(loadingEl => {
      const updatedCart: Cart = {
        id: this.form.value.id,
        title: this.form.value.title.trim(),
        description: this.form.value.description.trim(),
        amount: amountFromInput(this.form.value.amount)!,
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
        amount: amountInputText(cart.amount),
        description: cart.description,
        datePurchased: this.formattedString,
        categoryId: cart.categoryDto.id,
        recurrenceType: cart.hasActiveTemplate ? cart.recurrenceType : RECURRENCE_TYPE.NONE
      })
    } else if (this.isAddMode) {
      const template = this.findDuplicateSource();
      this.isDuplicate = !!template;
      this.form.patchValue({
        ...(template ? duplicateFormValues(template) : {}),
        datePurchased: this.formattedString,
        recurrenceType: RECURRENCE_TYPE.NONE
      });
    }
  }

  /** Vorlage aus `?duplicateOf=<id>` (DIV-73), nur eigene, reguläre Ausgaben der aktiven Gruppe. */
  private findDuplicateSource(): Cart | undefined {
    const duplicateOf = this.route.snapshot.queryParamMap.get('duplicateOf');
    if (!duplicateOf) return undefined;
    return duplicateSource(this.cartList(), +duplicateOf, this.activeGroup.id, this.user?.name);
  }

  /** Nach dem Laden: eine vorbelegte, in der Gruppe nicht (mehr) vorhandene Kategorie leeren, Pflichtmeldung zeigen. */
  private clearUnknownCategory() {
    const categoryId = this.categoryId;
    if (!categoryId?.value) return;
    if (!this.selectableCategories().some(cat => cat.id === categoryId.value)) {
      categoryId.setValue('');
      categoryId.markAsTouched();
    }
  }

  setToday() {
    this.formattedString = moment().format('DD.MM.YYYY');
    this.dateValue = moment().startOf('day').format('YYYY-MM-DD') + 'T00:00:00';
  }
  
  /** Kaufdatum eines Carts (ISO-Tag `YYYY-MM-DD`) ins Formular – explizit geparst, ohne Umweg über UTC (DIV-81). */
  setDate(date: string) {
    const day = moment(date, 'YYYY-MM-DD');
    this.formattedString = day.format('DD.MM.YYYY');
    this.dateValue = day.format('YYYY-MM-DD') + 'T00:00:00';
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
    // Lokale Mitternacht des gewählten Tages (DIV-81): new Date('YYYY-MM-DD') wäre Mitternacht UTC und westlich von UTC
    // der Vortag – die Vorschau zeigte dann einen Tag zu früh an.
    const lastDate = moment(this.form.value.datePurchased, 'DD.MM.YYYY').toDate();

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

  /** Formularwert `DD.MM.YYYY` → ISO-Tag `YYYY-MM-DD` für den Request (DIV-81). */
  getDateFromString(formattedString: string): string {
    return formattedString.replace(/(\d{2}).(\d{2}).(\d{4})/, "$3-$2-$1");
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
    this.nextExecutionDate = calculateNextExecutionDate(lastExecutionDate, recurrenceType, new Date());
  }

}
