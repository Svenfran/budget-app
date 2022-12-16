import { Component, OnInit } from '@angular/core';
import { Group } from 'src/app/models/group';
import { SpendingsOverviewDto } from 'src/app/models/spendings-overview-dto';
import { SpendingsOverviewPerMonthDto } from 'src/app/models/spendings-overview-per-month-dto';
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
  spendingsTotalYear: SpendingsOverviewTotalYearDto;
  year: number;

  constructor(
    private groupService: GroupService,
    private spendingsService: SpendingsOverviewService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.getCurrentYear();
    this.groupService.activeGroup.subscribe(group => {
      if (group) {
        this.activeGroup = group;
        this.getSpendingsOverview(this.currentYear);
      }
    })
  }

  ionViewWillEnter() {
    this.getSpendingsOverview(this.currentYear);
  }

  getSpendingsOverview(year: number) {
    this.isLoading = true;
    this.spendingsService.spendingsOverviewModified.subscribe(() => {
      this.spendingsService.getSpendingsOverview(year, this.activeGroup.id).subscribe(res => {
        this.availableYears = res.availableYears;
        this.spendingsPerMonth = res.spendingsPerMonth;
        this.spendingsTotalYear = res.spendingsTotalYear;
        this.year = res.year;
        this.isLoading = false;
        // console.log(res);
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

}
