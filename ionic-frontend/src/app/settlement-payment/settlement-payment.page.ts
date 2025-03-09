import { Component, OnInit, ViewChild } from '@angular/core';
import { FormGroup, UntypedFormBuilder, Validators } from '@angular/forms';
import { IonDatetime, ModalController } from '@ionic/angular';
import { GroupMembers } from '../models/group-members';
import { GroupSideNav } from '../models/group-side-nav';
import { SettlementPaymentDto } from '../models/settlement-payment-dto';
import { UserDto } from '../models/user';
import { CartService } from '../services/cart.service';
import { GroupService } from '../services/group.service';
import { AuthService } from '../auth/auth.service';
import { format, parseISO } from 'date-fns';
import { Zeitraum } from '../models/zeitraum';

@Component({
  selector: 'app-settlement-payment',
  templateUrl: './settlement-payment.page.html',
  styleUrls: ['./settlement-payment.page.scss'],
})
export class SettlementPaymentPage implements OnInit {
  form: FormGroup;
  members: GroupMembers;
  userName: string;
  currentUser: UserDto;
  activeGroup: GroupSideNav;
  loadedMembers: Promise<boolean>;
  today = new Date();
  showPicker = false;
  dateValue = "";
  formattedString = "";
  minDate = "";
  maxDate = "";
  zeitraeume: Zeitraum[] = [];

  @ViewChild(IonDatetime) datetime: IonDatetime;
  constructor(
    private fb: UntypedFormBuilder,
    private groupService: GroupService,
    private modalCtrl: ModalController,
    private authService: AuthService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.getGroupMembers();
    this.setToday();

    this.form = this.fb.group({
      amount: ['',[ Validators.required, Validators.pattern('[+-]?([0-9]*[.])?[0-9]+')]],
      memberId: ['',[ Validators.required ]],
      datePurchased: ['',[ Validators.required, Validators.pattern('(0[1-9]|1[0-9]|2[0-9]|3[01]).(0[1-9]|1[012]).[0-9]{4}')]]
    });

    this.form.patchValue({
      datePurchased: this.formattedString
    })
  }

  getGroupMembers() {
    this.groupService.activeGroup.subscribe(group => {
      this.groupService.getGroupMembers(group.id).subscribe(res => {
        this.activeGroup = group;
        this.minDate = format(new Date(group.dateCreated), 'yyyy-MM-dd') + 'T00:00:00';
        this.maxDate = format(new Date().setFullYear(new Date().getFullYear() + 1), 'yyyy-MM-dd') + 'T00:00:00';
        this.members = res;
        this.loadedMembers = Promise.resolve(true);
        this.groupService.getGroupMembershipHistoryForGroupAndUser(group.id).subscribe(gmh => {
          this.zeitraeume = gmh.map(item => ({ 
            startDate: new Date(this.removeTimeFromDate(item.startDate)),
            endDate: item.endDate ? new Date(this.removeTimeFromDate(item.endDate)) : null, 
            groupId: item.groupId, 
            userId: item.userId
          }));
        });
      })
    });
  }

  removeTimeFromDate(date: Date): string {
    return date.toString().split('T')[0] + 'T00:00:00';
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

  closeDatePicker() {
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
      return date >= zeitraum.startDate && (date <= zeitraum.endDate || this.dateIsNull(zeitraum.endDate)) && zeitraum.userId == this.currentUser.id && zeitraum.groupId == this.activeGroup.id;
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

  onSubmit() {
    let memberName: string;
    if (this.members.ownerId === this.form.value.memberId) {
      memberName = this.members.ownerName;
    } else {
      memberName = this.members.members.filter(member => member.id == this.form.value.memberId)[0].userName;
    }

    let member = new UserDto(
      this.form.value.memberId,
      memberName
    );
    let settlementPayment = new SettlementPaymentDto(
      this.form.value.amount,
      this.activeGroup.id,
      member,
      this.getDateFromString(this.form.value.datePurchased)
    );
    
    this.modalCtrl.dismiss(settlementPayment);
  }

  onDismiss() {
    if (this.form.invalid) {
      // this.form.markAllAsTouched();
      return;
    } else {
      this.onSubmit();
    }
  }

  close() {
    this.modalCtrl.dismiss();
  }

  get amount() {return this.form.get('amount');}
  get memberId() {return this.form.get('memberId');}
  get datePurchased() {return this.form.get('datePurchased');}

  getCurrentUser() {
    this.authService.user.subscribe(user => {
      this.userName = user.name;
      this.currentUser = new UserDto(user.id, user.name);
    })
    return this.currentUser;
  }
}
