import { HttpClient, HttpParams } from '@angular/common/http';
import { effect, Injectable, signal, untracked } from '@angular/core';
import { GroupService } from 'src/app/service/group.service';
import { environment } from 'src/environments/environment';
import { StatisticsDto } from '../model/statistics-dto';
import { defaultStatisticsFilter, StatisticsFilter } from '../model/statistics-filter';

/**
 * Zustand der Statistikseite (DIV-63): Filter (bleibt je Anmelde-Sitzung erhalten), Daten zum zuletzt angeforderten
 * Filter, Lade- und Fehlerzustand. Reiner Lesezustand: Bei einem neuen Request werden die alten Daten sofort verworfen,
 * damit nie Werte eines anderen Filters sichtbar sind; Antworten älterer Requests oder einer beendeten Sitzung werden
 * ignoriert (Generationszähler).
 */
@Injectable({
  providedIn: 'root'
})
export class StatisticsService {

  private statisticsUrl = `${environment.apiBaseUrl}/api/statistics`;

  public filter = signal<StatisticsFilter>(defaultStatisticsFilter(new Date()));
  public statistics = signal<StatisticsDto | null>(null);
  public isLoading = signal(false);
  public hasError = signal(false);

  private generation = 0;
  private session: number;
  /** zuletzt gemeldete Gruppen-IDs; null = noch kein Stand in dieser Sitzung */
  private knownGroupIds: Set<number> | null = null;

  constructor(
    private http: HttpClient,
    private groupService: GroupService
  ) {
    this.session = this.groupService.session();
    effect(() => {
      this.groupService.session();
      untracked(() => this.syncSession());
    });
  }

  /** Neuen Filter übernehmen und laden. */
  setFilter(filter: StatisticsFilter): void {
    this.syncSession();
    this.filter.set(filter);
    this.load();
  }

  /** Daten zum aktuellen Filter laden; `onComplete` läuft immer (auch bei verworfener Antwort), z. B. für den Refresher. */
  load(onComplete?: () => void): void {
    this.syncSession();
    const generation = ++this.generation;
    const session = this.session;
    const isCurrent = () => generation === this.generation && session === this.groupService.session();

    this.statistics.set(null);
    this.hasError.set(false);
    this.isLoading.set(true);

    this.http
      .get<StatisticsDto>(this.statisticsUrl, { params: this.toParams(this.filter()) })
      .subscribe({
        next: (result) => {
          if (isCurrent()) {
            this.statistics.set(result);
            this.isLoading.set(false);
          }
          onComplete?.();
        },
        error: (err) => {
          if (isCurrent()) {
            console.log('Error fetching statistics:', err);
            this.hasError.set(true);
            this.isLoading.set(false);
          }
          onComplete?.();
        }
      });
  }

  /**
   * Gruppen des Nutzers haben sich geändert (z. B. Gruppe gelöscht oder Nutzer entfernt, Codex F-003). Der erste Aufruf
   * merkt sich nur den Stand. Bei geänderter ID-Menge: laufende Antworten verwerfen, auf „Alle Gruppen“ zurückfallen,
   * falls die gewählte Gruppe fehlt, und neu laden. Reihenfolge und Namen spielen keine Rolle.
   */
  groupsChanged(groupIds: number[]): void {
    this.syncSession();
    const next = new Set(groupIds);
    const known = this.knownGroupIds;
    this.knownGroupIds = next;
    if (known === null || (known.size === next.size && [...next].every(id => known.has(id)))) return;

    const filter = this.filter();
    if (filter.groupId !== null && !next.has(filter.groupId)) {
      this.filter.set({ ...filter, groupId: null });
    }
    this.load();
  }

  /** Neue Anmelde-Sitzung: Filter auf Standard, Daten und laufende Requests verwerfen. */
  private syncSession(): void {
    const session = this.groupService.session();
    if (session === this.session) return;
    this.session = session;
    this.generation++;
    this.knownGroupIds = null;
    this.filter.set(defaultStatisticsFilter(new Date()));
    this.statistics.set(null);
    this.isLoading.set(false);
    this.hasError.set(false);
  }

  private toParams(filter: StatisticsFilter): HttpParams {
    let params = new HttpParams().set('period', filter.period);
    if (filter.groupId !== null) params = params.set('groupId', filter.groupId);
    if (filter.period !== 'ALL') params = params.set('year', filter.year);
    if (filter.period === 'MONTH') params = params.set('month', filter.month);
    return params;
  }
}
