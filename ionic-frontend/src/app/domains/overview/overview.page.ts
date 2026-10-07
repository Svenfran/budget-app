import { Component, effect, OnInit } from '@angular/core';
import { GroupService } from 'src/app/service/group.service';
import { OverviewService } from './service/overview.service';
import { CartService } from '../cartlist/service/cart.service';
import { User } from 'src/app/auth/user';
import { AuthService } from 'src/app/auth/auth.service';
import { INIT_VALUES } from 'src/app/constants/default-values';
import { SpendingsOverviewUserDto } from './model/spendings-overview-user-dto';
import { TranslateService } from '@ngx-translate/core';

const DELETED = 'DELETED';
const REMOVED = 'REMOVED';

@Component({
  selector: 'app-overview',
  templateUrl: './overview.page.html',
  styleUrls: ['./overview.page.scss'],
  standalone: false
})
export class OverviewPage implements OnInit {

  public activeGroup = this.groupService.activeGroup();
  // Monthly
  public spendingsOverviewMonthly = this.overviewService.spendingsOverview;
  public spendingsPerMonth = this.overviewService.spendingsPerMonth;
  public spendingsTotalYearMonthly = this.overviewService.spendingsTotalYearMonthly;
  public year = this.overviewService.year;
  // Yearly
  public spendingsOverviewYearly = this.overviewService.spendingsOverviewYearly;
  public spendingsPerYear = this.overviewService.spendingsPerYear;
  public spendingsTotalYearYearly = this.overviewService.spendingsTotalYearYearly;
  public availableYears = this.overviewService.availableYears;

  public segment: 'year' | 'month' = 'year';
  public isLoading: boolean = true;
  public currentYear = new Date().getFullYear();
  public hidden: boolean = true;
  public user: User | undefined;
  public translatedRemoved!: string;

  constructor(
    private groupService: GroupService,
    private overviewService: OverviewService,
    private cartService: CartService,
    private authService: AuthService,
    private translate: TranslateService
  ) { 
    effect(() => {
      this.activeGroup = this.groupService.activeGroup();
      this.cartService.cartUpdated();
      this.groupService.memberUpdated();
      this.isLoading = true;
      if (!this.activeGroup.flag?.includes(INIT_VALUES.DEFAULT)) {
        this.spendingsOverviewMonthly.set(null);
        this.spendingsOverviewYearly.set(null);
        this.overviewService.getSpendingsOverviewYearly(this.activeGroup, false, () => {
          this.isLoading = false;
        });
        this.overviewService.getSpendingsOverview(this.currentYear, this.activeGroup);
      } else {
        this.isLoading = false;
      }
    });
  }

  ngOnInit() {
    this.authService.user.subscribe(user => {
      if (user) {
        this.user = user;
      }
    });

    this.loadTranslations();
    // Sprache wechseln -> Werte aktualisieren
    this.translate.onLangChange.subscribe(() => this.loadTranslations());
  }

  get spendingsUsers(): SpendingsOverviewUserDto[] {
    return this.segment === 'year' ? this.spendingsTotalYearYearly().spendingsTotalUser : this.spendingsTotalYearMonthly().spendingsTotalUser;
  }

  
  private loadTranslations() {
    this.translate.get(['global.user.removed']).subscribe(translations => {
      this.translatedRemoved = translations['global.user.removed'];
    });
  }

  isDeletedUser(userName: string): boolean {
    return userName.includes(DELETED) || userName.includes(REMOVED);
  }

  getShortUserName(userName: string): string {
    if (userName.includes(DELETED)) {
        const userDeleted = this.translate.instant('global.user.deleted');
        return userDeleted.length > 10 ? `${userDeleted.slice(0, 10)}...` : userDeleted;
    } else if (userName.includes(REMOVED)) {
        const userRemoved = userName.split(' ')[0]
        return userRemoved.length > 10 ? `${userRemoved.slice(0, 10)}...` : userRemoved;
    } else {
        return userName.length > 10 ? `${userName.slice(0, 10)}...` : userName;
    }
  }

  getShortGroupName(groupName: string): string {
    return groupName.length > 20 ? `${groupName.slice(0, 20)}...` : groupName;
  }

  getSpendingsOverview(year: number) {
    this.segment = 'month';
    this.isLoading = true;

    this.spendingsOverviewMonthly.set(null);
    this.spendingsOverviewYearly.set(null);

    this.overviewService.getSpendingsOverviewYearly(this.activeGroup, false, () => {
      this.isLoading = false;
    });
    this.overviewService.getSpendingsOverview(year, this.activeGroup);

  }

  ionViewWillEnter() {
    this.segment = 'year';
  }

  hide() {
    this.hidden = !this.hidden;
  }

  refreshSpendings(event: CustomEvent) {
    setTimeout(() => {
      this.overviewService.getSpendingsOverviewYearly(this.activeGroup, true);
      this.overviewService.getSpendingsOverview(this.currentYear, this.activeGroup, true);
      (event.target as HTMLIonRefresherElement).complete();
    }, 2000);
  }
}
