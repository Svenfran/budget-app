import { Component, effect, OnInit, untracked } from '@angular/core';
import { GroupService } from 'src/app/service/group.service';
import { OverviewService } from './service/overview.service';
import { CartService } from '../cartlist/service/cart.service';
import { User } from 'src/app/auth/user';
import { AuthService } from 'src/app/auth/auth.service';
import { INIT_VALUES } from 'src/app/constants/default-values';
import { SpendingsOverviewUserDto } from './model/spendings-overview-user-dto';
import { SpendingsOverviewTotalYearDto } from './model/spendings-overview-total-year-dto';
import { TranslateService } from '@ngx-translate/core';
import { isDeletedMarker, isInactiveMember, userDisplayName, userInitials } from './overview-display';

/** Zeitraum der Gesamt- und Personenkarte als Rohwerte; Monatsnamen formatiert das Template (monthName-Pipe). */
export interface PeriodRange {
  from: number;
  to: number;
  year?: number;
}

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
    // Anzeige: hält den Gruppennamen aktuell (auch bei Umbenennung), ohne neu zu laden
    effect(() => {
      this.activeGroup = this.groupService.activeGroup();
    });
    // Laden: nur bei echtem Gruppenwechsel (activeGroupId), neuer Anmelde-Sitzung (session, auch bei gleicher Gruppe
    // nach Logout/Login) und den übrigen Triggern, nicht bei Umbenennung
    effect(() => {
      this.groupService.activeGroupId();
      this.groupService.session();
      this.cartService.cartUpdated();
      this.groupService.memberUpdated();
      const activeGroup = untracked(() => this.groupService.activeGroup());
      this.isLoading = true;
      if (!activeGroup.flag?.includes(INIT_VALUES.DEFAULT)) {
        this.spendingsOverviewMonthly.set(null);
        this.spendingsOverviewYearly.set(null);
        this.overviewService.getSpendingsOverviewYearly(activeGroup, false, () => {
          this.isLoading = false;
        });
        this.overviewService.getSpendingsOverview(this.currentYear, activeGroup);
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

  /** Gesamtwerte (Summe + Mitglieder) des aktiven Segments. */
  get spendingsTotal(): SpendingsOverviewTotalYearDto {
    return this.segment === 'year' ? this.spendingsTotalYearYearly() : this.spendingsTotalYearMonthly();
  }

  get spendingsUsers(): SpendingsOverviewUserDto[] {
    return this.spendingsTotal.spendingsTotalUser;
  }

  
  private loadTranslations() {
    this.translate.get(['global.user.removed']).subscribe(translations => {
      this.translatedRemoved = translations['global.user.removed'];
    });
  }

  /** Zeitraum (ältester – neuester Eintrag) des aktiven Segments; null ohne Daten. */
  get periodRange(): PeriodRange | null {
    if (this.segment === 'year') {
      const years = this.spendingsPerYear();
      return years?.length ? { from: years[years.length - 1].year, to: years[0].year } : null;
    }
    const months = this.spendingsPerMonth();
    return months?.length ? { from: months[months.length - 1].month, to: months[0].month, year: this.year() } : null;
  }

  isDeletedUser(userName: string): boolean {
    return isInactiveMember(userName);
  }

  isDeletedMarker(userName: string): boolean {
    return isDeletedMarker(userName);
  }

  getUserDisplayName(userName: string): string {
    return userDisplayName(userName, this.translate.instant('global.user.deleted'));
  }

  getInitials(userName: string): string {
    return userInitials(this.getUserDisplayName(userName));
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
