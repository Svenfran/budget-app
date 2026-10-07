import { ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { IonDatetime, ModalController } from '@ionic/angular';
import { AuthService } from '../auth/auth.service';
import { CategoryService } from '../service/category.service';
import { GroupService } from '../service/group.service';
import { User } from '../auth/user';
import { CartFilter } from './model/CartFilter';
import { LanguageService } from '../service/language.service';

@Component({
  selector: 'app-filter-modal',
  templateUrl: './filter-modal.page.html',
  styleUrls: ['./filter-modal.page.scss'],
  standalone: false
})
export class FilterModalPage implements OnInit {
  @ViewChild(IonDatetime) datetimeStart!: IonDatetime;
  @ViewChild(IonDatetime) datetimeEnd!: IonDatetime;

  public activeGroup = this.groupService.activeGroup;
  public categoryList = this.categoryService.categories;
  public groupMembersWithOwner = this.groupService.groupMembersWithOwner;
  public form!: FormGroup;
  public user!: User;
  public cartFilter: CartFilter = {};
  public formattedDateFrom: string = '';
  public formattedDateTo: string = '';
  public today = new Date();
  public showPicker = false;
  public dateValue: string = '';
  public currentLang = this.langService.currentLang

  constructor(
    private modalCtrl: ModalController,
    private authService: AuthService,
    private fb: FormBuilder,
    private categoryService: CategoryService,
    private groupService: GroupService,
    private cdRef: ChangeDetectorRef,
    private langService: LanguageService
  ) { }

  ngOnInit() {
    this.authService.user.subscribe(user => {
      if (user) {
        this.user = user;
        this.groupService.getGroupMembers(this.activeGroup().id);
        this.categoryService.getCategoriesByGroup(this.activeGroup());
      }
    });

    this.form = this.fb.group({
      title: [null],
      description: [null],
      categories: [null],
      members: [null],
      startDate: [null],
      endDate: [null]
    });

    if (Object.keys(this.cartFilter).length > 0) {
      this.form.patchValue({
        title: this.cartFilter.title,
        description: this.cartFilter.description,
        categories: this.cartFilter.category,
        members: this.cartFilter.userName,
        startDate: this.cartFilter.startDate ? this.formatDate(this.cartFilter.startDate) : this.cartFilter.startDate,
        endDate: this.cartFilter.endDate ? this.formatDate(this.cartFilter.endDate) : this.cartFilter.endDate
      })
    }
  }

  get title() {return this.form.get('title');}
  get description() {return this.form.get('description');}
  get categories() {return this.form.get('categories');}
  get members() {return this.form.get('members');}
  get startDate() {return this.form.get('startDate');}
  get endDate() {return this.form.get('endDate');}

  onDismiss() {
    if (this.form.invalid) {
      return;
    } else {
      this.onSubmit();
    }
  }

  onSubmit() {
    this.cartFilter = {
      title: this.form.value.title ? this.form.value.title.trim() : null,
      description: this.form.value.description ? this.form.value.description.trim() : null,
      category: this.form.value.categories,
      startDate: this.convertStringToDate(this.form.value.startDate)!,
      endDate: this.convertStringToDate(this.form.value.endDate)!,
      userName: this.form.value.members,
    }
    this.modalCtrl.dismiss(this.cartFilter);
  }

  formatDate(date: Date): string {
    const day = ('0' + date.getDate()).slice(-2);
    const month = ('0' + (date.getMonth() + 1)).slice(-2); // Monate sind 0-basiert
    const year = date.getFullYear();
    return `${day}.${month}.${year}`;
  }

  close() {
    this.modalCtrl.dismiss();
  }

  reset() {
    this.form.reset();
    this.cartFilter = {};
  }

  clearInput(controlName: string) {
    this.form.get(controlName)?.setValue(null);
    this.cdRef.detectChanges();
    if (controlName === 'startDate') {
      this.formattedDateFrom = '';
      this.closeDatePicker(controlName);
    }
    if (controlName === 'endDate') {
      this.formattedDateTo = '';
      this.closeDatePicker(controlName);
    }
  }

  closeDatePicker(controlName: string) {
    if (controlName === 'startDate') {
      this.datetimeStart.cancel(true);
    } else if (controlName === 'endDate') {
      this.datetimeEnd.cancel(true);
    }
  }

  onDateChange(event: any, controlName: string) {
    const date = new Date(event.detail.value); // Datum im ISO-Format
    const formattedDate = this.formatDate(date); // Datum ins gewünschte Format umwandeln

    this.form.get(controlName)?.setValue(formattedDate);

    if (controlName === 'startDate') {
      this.formattedDateFrom = formattedDate;
    } else if (controlName === 'endDate') {
      this.formattedDateTo = formattedDate;
    }
  }

  select(controlName: string) {
    if (controlName === 'startDate') {
      // Manuelles Setzen des aktuellen Datums, falls der Wert noch nicht gesetzt wurde
      if (!this.form.get('startDate')?.value) {
        const currentDate = new Date(this.getDateValue('startDate'));
        this.onDateChange({ detail: { value: currentDate.toISOString() } }, 'startDate');
      }
      this.datetimeStart.confirm(true);
    } else if (controlName === 'endDate') {
      if (!this.form.get('endDate')?.value) {
        const currentDate = new Date(this.getDateValue('endDate'));
        this.onDateChange({ detail: { value: currentDate.toISOString() } }, 'endDate');
      }
      this.datetimeEnd.confirm(true);
    }
  }
  
  getDateValue(controlName: string): string {
    const dateValue = this.form.get(controlName)?.value;
    if (dateValue) {
      // Wenn ein Datum im Feld steht, verwenden wir es
      const parts = dateValue.split('.');
      // Konvertiere in yyyy-MM-dd (ISO-Format)
      return `${parts[2]}-${parts[1]}-${parts[0]}`;
    } else {
      // Wenn kein Datum vorhanden ist, das aktuelle Datum verwenden
      return new Date().toISOString().split('T')[0]; // Nur das Datum im ISO-Format zurückgeben
    }
  }

  convertStringToDate(dateString: string): Date | null {
    // Prüfen, ob der String dem Muster dd.MM.yyyy entspricht
    if (dateString == null) {
      return null
    }
    const dateParts = dateString.split('.'); // Datum anhand des Punktes aufteilen

    // Prüfen, ob das Datum drei Teile enthält (Tag, Monat, Jahr)
    if (dateParts.length !== 3) {
      return null; // Ungültiges Datum
    }

    const day = parseInt(dateParts[0], 10);   // Tag extrahieren
    const month = parseInt(dateParts[1], 10) - 1; // Monat extrahieren (Monate sind 0-basiert)
    const year = parseInt(dateParts[2], 10);  // Jahr extrahieren

    // Prüfen, ob die Teile des Datums gültige Zahlen sind
    if (isNaN(day) || isNaN(month) || isNaN(year)) {
      return null; // Ungültiges Datum
    }

    // Neues Date-Objekt mit den extrahierten Werten erstellen
    const date = new Date(year, month, day);

    // Prüfen, ob das Datum tatsächlich existiert (z.B. 30. Februar vermeiden)
    if (date.getDate() !== day || date.getMonth() !== month || date.getFullYear() !== year) {
      return null; // Ungültiges Datum
    }

    return date;
  }
}
