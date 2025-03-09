import { Component, OnInit } from '@angular/core';
import { SegmentChangeEventDetail } from '@ionic/angular';
import { AuthService } from 'src/app/auth/auth.service';
import { Group } from 'src/app/models/group';
import { SpendingsOverviewDto } from 'src/app/models/spendings-overview-dto';
import { SpendingsOverviewPerMonthDto } from 'src/app/models/spendings-overview-per-month-dto';
import { SpendingsOverviewPerYearDto } from 'src/app/models/spendings-overview-per-year-dto';
import { SpendingsOverviewTotalYearDto } from 'src/app/models/spendings-overview-total-year-dto';
import { AlertService } from 'src/app/services/alert.service';
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
  loadedActiveGroup: Promise<boolean>;
  loadedSpendings: Promise<boolean>;

  constructor(
    private groupService: GroupService,
    private spendingsService: SpendingsOverviewService,
    private alertService: AlertService,
    private authService: AuthService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.getCurrentYear();
    this.groupService.activeGroup.subscribe(group => {
      if (group) {
        // console.log(group);
        this.activeGroup = group;
        this.getSpendingsOverviewYearly();
        this.loadedActiveGroup = Promise.resolve(true);
      } else {
        this.groupService.setActiveGroup(null);
      }
    })
  }
  
  ionViewWillEnter() {
    this.getSpendingsOverviewYearly();
  }
  


  getSpendingsOverview(year: number) {
    if (!year) {
      year = this.currentYear;
    };

    this.isLoading = true;
    if (this.activeGroup.id !== null) {
      this.spendingsService.spendingsOverviewModified.subscribe(() => {
        this.spendingsService.getSpendingsOverview(year, this.activeGroup.id).subscribe(res => {
          this.spendingsPerMonth = res.spendingsPerMonth;
          this.spendingsTotalYear = res.spendingsTotalYear;
          this.segment = 'month';
          this.year = res.year;
          this.isLoading = false;
        })
      })
    }
  }

  getSpendingsOverviewYearly() {
    this.isLoading = true;
    if (this.activeGroup.id !== null) {
      this.spendingsService.spendingsOverviewModified.subscribe(() => {
        this.spendingsService.getSpendingsOverviewYearly(this.activeGroup.id).subscribe(res => {
          this.spendingsPerYear = res.spendingsPerYear;
          this.spendingsTotalYear = res.spendingsTotalYear;
          this.availableYears = res.availableYears;
          this.segment = 'year';
          this.loadedSpendings = Promise.resolve(true);
          this.isLoading = false;
        })
      })
    }
  }

  hide() {
    this.hidden = !this.hidden;
  }

  getCurrentUser() {
    // this.groupService.currentUser.subscribe(user => {
    //   this.userName = user.userName;
    // })
    this.authService.userName.subscribe(name => {
      this.userName = name;
    })
    return this.userName;
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

  onCreateGroup() {
    this.alertService.createGroup();
  }

}
