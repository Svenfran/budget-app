import { Component, OnInit } from '@angular/core';
import { SegmentChangeEventDetail } from '@ionic/angular';
import { Group } from 'src/app/models/group';
import { SpendingsOverviewDto } from 'src/app/models/spendings-overview-dto';
import { SpendingsOverviewPerMonthDto } from 'src/app/models/spendings-overview-per-month-dto';
import { SpendingsOverviewPerYearDto } from 'src/app/models/spendings-overview-per-year-dto';
import { SpendingsOverviewTotalYearDto } from 'src/app/models/spendings-overview-total-year-dto';
import { GroupService } from 'src/app/services/group.service';
import { SpendingsOverviewService } from 'src/app/services/spendings-overview.service';

@Component({
  selector: 'app-overview',
  templateUrl: './overview.page.html',
  styleUrls: ['./overview.page.scss'],
})
export class OverviewPage implements OnInit {

  activeGroup: Group;
  userName: string;
  availableYears: number[] = [];
  currentYear: number;
  isLoading: boolean;
  hidden: boolean = true;
  spendigsOverview: SpendingsOverviewDto;
  spendingsPerMonth: SpendingsOverviewPerMonthDto[];
  spendingsPerYear: SpendingsOverviewPerYearDto[];
  spendingsTotalYear: SpendingsOverviewTotalYearDto;
  year: number;
  segment: string;

  constructor(
    private groupService: GroupService,
    private spendingsService: SpendingsOverviewService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.getCurrentYear();
    this.groupService.activeGroup.subscribe(group => {
      this.segment = "month";
      if (group) {
        this.activeGroup = group;
        // this.getAvailableYears(group.id);
        this.getSpendingsOverview(this.currentYear);
      }
    })
  }

  ionViewWillEnter() {
    this.segment = "month";
    // this.getAvailableYears(this.activeGroup.id);
    this.getSpendingsOverview(this.currentYear);
  }

  getAvailableYears(groupId: number) {
    this.spendingsService.getAvailableYears(groupId).subscribe(res => {
      this.availableYears = res;
      console.log(this.availableYears);
    })
  }

  getSpendingsOverview(year: number) {
    this.isLoading = true;
    this.spendingsService.spendingsOverviewModified.subscribe(() => {
      this.spendingsService.getSpendingsOverview(year, this.activeGroup.id).subscribe(res => {
        this.availableYears = res.availableYears;
        this.spendingsPerMonth = res.spendingsPerMonth;
        this.spendingsTotalYear = res.spendingsTotalYear;
        this.year = res.year;
        this.segment = "month";
        this.isLoading = false;
      })
    })
  }

  getSpendingsOverviewYearly() {
    this.isLoading = true;
    this.spendingsService.spendingsOverviewModified.subscribe(() => {
      this.spendingsService.getSpendingsOverviewYearly(this.activeGroup.id).subscribe(res => {
        this.spendingsPerYear = res.spendingsPerYear;
        this.spendingsTotalYear = res.spendingsTotalYear;
        this.isLoading = false;
      })
    })
  }

  hide() {
    this.hidden = !this.hidden;
  }

  getCurrentUser() {
    this.groupService.currentUser.subscribe(user => {
      this.userName = user.userName;
    })
  }

  getCurrentYear() {
    this.currentYear = new Date().getFullYear();
  }

  onFilterUpdate(event: CustomEvent<SegmentChangeEventDetail>) {
    if (event.detail.value === 'month') {
      this.getSpendingsOverview(this.year);
    } else {
      this.segment = "year";
      this.getSpendingsOverviewYearly();
    }
  }

}
