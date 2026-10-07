import { Component, OnInit, ViewChild } from '@angular/core';
import { FormGroup, UntypedFormBuilder, Validators } from '@angular/forms';
import { IonDatetime, ModalController } from '@ionic/angular';
import { AuthService } from '../auth/auth.service';
import { GroupService } from '../service/group.service';
import { User } from '../auth/user';
import moment from 'moment';
import { SettlementPaymentDto } from './model/settlement-payment-dto';
import { LanguageService } from '../service/language.service';

@Component({
  selector: 'app-settlement-payment',
  templateUrl: './settlement-payment.page.html',
  styleUrls: ['./settlement-payment.page.scss'],
  standalone: false
})
export class SettlementPaymentPage implements OnInit {
  @ViewChild(IonDatetime) datetime!: IonDatetime;

  public form!: FormGroup;
  public user!: User;
  public activeGroup = this.groupService.activeGroup;
  public groupMembers = this.groupService.groupMembers;
  public formattedString = moment().format("DD.MM.YYYY");
  public minDate = moment(this.activeGroup().dateCreated).format('YYYY-MM-DD') + 'T00:00:00';
  public maxDate = moment().format('YYYY-MM-DD') + 'T00:00:00';
  public showPicker = false;
  public dateValue = "";
  public zeitraeume = this.groupService.groupMembershipHistory;
  public currentLang = this.langService.currentLang

  constructor(
    private fb: UntypedFormBuilder,
    private groupService: GroupService,
    private modalCtrl: ModalController,
    private authService: AuthService,
    private langService: LanguageService
  ) { }

  ngOnInit() {
    this.authService.user.pipe().subscribe(user => {
      if (user) this.user = user;
      this.groupService.getGroupMembers(this.activeGroup().id);
      this.groupService.getGroupMembershipHistoryForGroupAndUser(this.activeGroup());
    });

    this.form = this.fb.group({
      amount: ['',[ Validators.required, Validators.pattern('[+-]?([0-9]*[.])?[0-9]+')]],
      memberId: ['',[ Validators.required ]],
      datePurchased: ['',[ Validators.required, Validators.pattern('(0[1-9]|1[0-9]|2[0-9]|3[01]).(0[1-9]|1[012]).[0-9]{4}')]]
    });

    this.form.patchValue({
      datePurchased: this.formattedString
    })
  }

  get amount() {return this.form.get('amount');}
  get memberId() {return this.form.get('memberId');}
  get datePurchased() {return this.form.get('datePurchased');}


  onDismiss() {
    if (this.form.invalid) {
      return;
    } else {
      this.onSubmit();
    }
  }

  close() {
    this.modalCtrl.dismiss();
  }

  onSubmit() {
    let memberName: string;
    const memberId = this.form.value.memberId;

    if (this.groupMembers().ownerId === memberId) {
      memberName = this.groupMembers().ownerName;
    } else {
      memberName = this.groupMembers().members.find(member => member.id === memberId)?.userName!;
    }

    const settlementPayment: SettlementPaymentDto = {
      amount: this.form.value.amount,
      groupId: this.activeGroup().id,
      member: { id: memberId, userName: memberName },
      datePurchased: this.getDateFromString(this.form.value.datePurchased)
    }
    
    this.modalCtrl.dismiss(settlementPayment);
  }

  // DATE-PICKER
  
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


  isDateSelectable = (dateIsoString: string) => {
    const date = new Date(this.formatDateString(dateIsoString));
    return this.zeitraeume().some(zeitraum => {

      const startDate = new Date(this.formatDateString(zeitraum.startDate.toString()));
      const endDate = zeitraum.endDate ? new Date(this.formatDateString(zeitraum.endDate.toString())) : null;

      const result =
        date >= startDate &&
        (endDate === null || date <= endDate) &&
        zeitraum.userId === this.user.id &&
        zeitraum.groupId === this.activeGroup().id;

      return result;
    });
  };

  formatDateString(dateString: string) {
    const date = new Date(dateString);
    return date.toISOString().split('T')[0] + 'T00:00:00'
  }

}
