import { Component, OnInit } from '@angular/core';
import { FormGroup, UntypedFormBuilder, Validators } from '@angular/forms';
import { ModalController } from '@ionic/angular';
import { GroupMembers } from '../models/group-members';
import { GroupSideNav } from '../models/group-side-nav';
import { SettlementPaymentDto } from '../models/settlement-payment-dto';
import { UserDto } from '../models/user';
import { CartService } from '../services/cart.service';
import { GroupService } from '../services/group.service';

@Component({
  selector: 'app-settlement-payment',
  templateUrl: './settlement-payment.page.html',
  styleUrls: ['./settlement-payment.page.scss'],
})
export class SettlementPaymentPage implements OnInit {

  form: FormGroup;
  members: GroupMembers;
  userName: string;
  activeGroup: GroupSideNav;
  loadedMembers: Promise<boolean>;

  constructor(
    private fb: UntypedFormBuilder,
    private groupService: GroupService,
    private modalCtrl: ModalController,
    private cartService: CartService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.getGroupMembers();

    this.form = this.fb.group({
      amount: ['',[ Validators.required, Validators.pattern('[+-]?([0-9]*[.])?[0-9]+')]],
      memberName: ['',[ Validators.required ]]
    });
  }

  getGroupMembers() {
    this.groupService.activeGroup.subscribe(group => {
      this.groupService.getGroupMembers(group.id).subscribe(res => {
        this.activeGroup = group;
        this.members = res;
        this.loadedMembers = Promise.resolve(true);
      })
    });
  }

  onSubmit() {
    let member = new UserDto(
      this.members.members.filter(member => member.userName = this.form.value.memberName)[0].id,
      this.form.value.memberName
    );
    let settlementPayment = new SettlementPaymentDto(
      this.form.value.amount,
      this.activeGroup.id,
      member
    );
    // console.log(settlementPayment);
    this.cartService.addSettlementPayment(settlementPayment).subscribe();
  }

  onDismiss() {
    this.modalCtrl.dismiss();
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
  get memberName() {return this.form.get('memberName');}

  getCurrentUser() {
    this.groupService.currentUser.subscribe(user => {
      this.userName = user.userName;
    })
  }
}