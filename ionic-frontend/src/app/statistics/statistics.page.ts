import { Component, computed, effect, signal, untracked } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { GroupService } from 'src/app/service/group.service';
import { LanguageService } from 'src/app/service/language.service';
import { StatisticsPeriod } from './model/statistics-dto';
import { ChartThemeService } from './service/chart-theme.service';
import { StatisticsService } from './service/statistics.service';
import { categoryRows, donutSegments, hasPersonalSpendings, timelineLabels, yearOptions } from './statistics-calc';

/** Wert der Gruppenauswahl für „Alle Gruppen“ (ion-select kommt mit null als Wert nicht zurecht). */
const ALL_GROUPS = 'all';
/** wie scientificCurrency fest im deutschen Format */
const PERCENT_FORMAT = new Intl.NumberFormat('de', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 });

@Component({
  selector: 'app-statistics',
  templateUrl: './statistics.page.html',
  styleUrls: ['./statistics.page.scss'],
  standalone: false
})
export class StatisticsPage {

  readonly allGroups = ALL_GROUPS;
  readonly months = Array.from({ length: 12 }, (_, i) => i + 1);

  public filter = this.statisticsService.filter;
  public statistics = this.statisticsService.statistics;
  public isLoading = this.statisticsService.isLoading;
  public hasError = this.statisticsService.hasError;
  public groups = this.groupService.groupsSideNav;

  public selectedGroup = computed(() => this.filter().groupId ?? ALL_GROUPS);
  public categoryRows = computed(() => categoryRows(this.statistics()?.categories ?? []));
  public hasPersonalSpendings = computed(() => hasPersonalSpendings(this.statistics()));
  public palette = computed(() => this.chartThemeService.theme().palette);
  public barLabels = computed(() => {
    const statistics = this.statistics();
    const lang = this.languageService.currentLang();
    if (!statistics) return [];
    return timelineLabels(statistics.period, statistics.timeline, lang,
      week => this.translate.instant('statistics.timeline.week', { week }));
  });
  public barValues = computed(() => this.statistics()?.timeline.map(entry => entry.amount) ?? []);
  public donutSegments = computed(() => donutSegments(this.statistics()?.categories ?? []));
  public donutLabels = computed(() => {
    this.languageService.currentLang();
    return this.donutSegments().map(segment => segment.name ?? this.translate.instant('statistics.categories.other'));
  });
  /** Jahre der letzten Antwort, damit die Auswahl während des Ladens nicht schrumpft */
  private knownYears = signal<number[]>([]);
  public years = computed(() => yearOptions(this.knownYears(), new Date().getFullYear(), this.filter().year));

  constructor(
    private statisticsService: StatisticsService,
    private groupService: GroupService,
    private chartThemeService: ChartThemeService,
    private translate: TranslateService,
    private languageService: LanguageService
  ) {
    effect(() => {
      const statistics = this.statistics();
      if (statistics) untracked(() => this.knownYears.set(statistics.availableYears));
    });
    effect(() => {
      const groupIds = this.groupService.groupsSideNav().map(group => group.id);
      untracked(() => this.statisticsService.groupsChanged(groupIds));
    });
  }

  ionViewWillEnter() {
    this.statisticsService.load();
  }

  onGroupChange(value: number | string) {
    this.statisticsService.setFilter({ ...this.filter(), groupId: value === ALL_GROUPS ? null : Number(value) });
  }

  onPeriodChange(value: StatisticsPeriod) {
    this.statisticsService.setFilter({ ...this.filter(), period: value });
  }

  onYearChange(value: number) {
    this.statisticsService.setFilter({ ...this.filter(), year: Number(value) });
  }

  onMonthChange(value: number) {
    this.statisticsService.setFilter({ ...this.filter(), month: Number(value) });
  }

  formatPercent(percent: number): string {
    return PERCENT_FORMAT.format(percent / 100);
  }

  reload() {
    this.statisticsService.load();
  }

  refresh(event: CustomEvent) {
    this.statisticsService.load(() => (event.target as HTMLIonRefresherElement).complete());
  }
}
