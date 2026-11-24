import { Component, computed, DestroyRef, effect, ElementRef, Input, OnDestroy, OnInit, signal, ViewChild, WritableSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, Validators } from '@angular/forms';
import { AlertController, ModalController } from '@ionic/angular';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from 'src/app/auth/auth.service';
import { INIT_NUMBERS } from 'src/app/constants/default-values';
import { UserDto } from 'src/app/model/user-dto';
import { GroupService } from 'src/app/service/group.service';
import { EmailValidator } from 'src/app/Validator/email-validator';
import { memberActions, MemberActions } from '../group-permissions';
import { GROUP_GONE_ROLE } from '../group-sheet-roles';
import { OverviewService } from 'src/app/domains/overview/service/overview.service';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { DEFAULT_CURRENCY } from 'src/app/constants/currency';

export type MembersSheetView = 'list' | 'add';
export type MembersLoadState = 'loading' | 'loaded' | 'error';

/** Rolle, mit der das Sheet nach dem eigenen Austritt schließt. */
export const MEMBERS_SHEET_LEFT_ROLE = 'left';

/**
 * „Mitglieder verwalten“ als Bottom Sheet (DIV-80, ersetzt GroupmembersPage und den Alert „Mitglied hinzufügen“).
 * Ein Sheet mit internem View-State (`view`, geteilt mit membersSheetModalOptions): Mitgliederliste oder „Mitglied
 * hinzufügen“ – nie ein zweites Sheet.
 *
 * - Laden (Codex F-003, F-005): `groupMembers` ist ein globaler State und kann noch eine andere Gruppe enthalten. Liste
 *   und Aktionen erscheinen nur bei `loadState === 'loaded'` und passender Gruppen-ID; Antworten nach dem Schließen
 *   verwirft `isCurrent`. Ein Ladefehler zeigt eine Meldung mit „Erneut versuchen“.
 * - Aktionen (Rechte: memberActions): „Zum Admin machen“ (Bestätigungs-Alert, E1), „Mitglied entfernen“ (bisheriger
 *   Bestätigungs-Alert). Den State ändert GroupService erst nach Erfolg; Live-Events halten die Liste aktuell (E5).
 * - Hinzufügen: Feldfehler und Server-Fehler stehen unter dem E-Mail-Feld (A4, E6); nach Erfolg zurück zur Liste.
 * - Verschwindet die Gruppe (gelöscht, Nutzer entfernt), schließt das Sheet mit GROUP_GONE_ROLE; spätere Ergebnisse
 *   bewirken nichts mehr (Codex F-002).
 */
@Component({
  selector: 'app-members-sheet',
  templateUrl: './members-sheet.component.html',
  styleUrls: ['./members-sheet.component.scss'],
  standalone: false,
})
export class MembersSheetComponent implements OnInit, OnDestroy {

  /** ID des eigenen Modals (membersSheetModalOptions): Schließen trifft nur dieses Sheet. */
  @Input() modalId!: string;
  @Input() groupId!: number;
  @Input() groupName = '';
  @Input() saving!: WritableSignal<boolean>;
  @Input() view!: WritableSignal<MembersSheetView>;

  readonly maxLength = INIT_NUMBERS.MAX_LENGTH;
  readonly userId = signal<number | undefined>(undefined);
  readonly loadState = signal<MembersLoadState>('loading');
  /** i18n-Key der Meldung unter dem E-Mail-Feld (Validierung oder Server). */
  readonly addError = signal<string | null>(null);
  readonly email = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(INIT_NUMBERS.MAX_LENGTH)] });

  /** Geladene Mitgliederliste genau dieser Gruppe – sonst null (Ladezustand). */
  readonly members = computed(() => {
    const groupMembers = this.groupService.groupMembers();
    return this.loadState() === 'loaded' && groupMembers.id === this.groupId ? groupMembers : null;
  });
  /** Admin zuerst, dann die Mitglieder (wie GroupService.groupMembersWithOwner). */
  readonly rows = computed(() => this.members() ? this.groupService.groupMembersWithOwner() : []);
  readonly isAdmin = computed(() => {
    const members = this.members();
    return !!members && members.ownerId === this.userId();
  });

  /**
   * Fokus ins E-Mail-Feld, sobald die Ansicht „Mitglied hinzufügen“ erscheint (wie „Gruppe hinzufügen“): Das Feld ist
   * der einzige Zweck der Ansicht. Der Setter läuft bei jedem Wechsel in die Ansicht, da *ngIf das Feld neu erzeugt.
   * Erst nach dem ersten Rendern und einem Frame fokussieren: Direkt beim Anlegen hat ion-input noch kein natives
   * Eingabefeld, setFocus() bewirkt dann nichts.
   */
  @ViewChild('emailInput', { read: ElementRef }) set emailInput(ref: ElementRef<HTMLIonInputElement> | undefined) {
    if (ref) this.focusWhenReady(ref.nativeElement);
  }

  /** ⋮-Menü eines Mitglieds: ein Popover für alle Zeilen. */
  memberMenuOpen = false;
  memberMenuEvent: Event | undefined;
  private memberMenuId: number | null = null;

  /** Sheet ist geschlossen oder schließt gerade: keine weiteren Aktionen, späte Ergebnisse ohne Wirkung. */
  private closed = false;
  private loadGeneration = 0;
  /** Gerade offener Bestätigungs-Alert (schließt mit dem Sheet). */
  private openAlert: HTMLIonAlertElement | undefined;
  private readonly currencyPipe = new ScientificCurrencyPipe();

  constructor(
    private modalCtrl: ModalController,
    private alertCtrl: AlertController,
    private groupService: GroupService,
    private authService: AuthService,
    private translate: TranslateService,
    private destroyRef: DestroyRef,
    private overviewService: OverviewService,
  ) {
    // Gruppe für den Nutzer verschwunden (Löschung, eigene Entfernung – auch per Live-Event): Sheet schließen
    effect(() => {
      const list = this.groupService.groupOverviewList();
      if (this.closed || list.some(group => group.id === this.groupId)) return;
      this.closeWith(GROUP_GONE_ROLE);
    });
  }

  ngOnInit() {
    // Abo endet mit dem Sheet; bei Logout kein Request ohne Token (wie bisher GroupmembersPage, DIV-60)
    this.authService.user.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
      if (!user) return;
      this.userId.set(user.id);
      this.load();
    });
  }

  ngOnDestroy() {
    this.closed = true;
    this.dismissOpenAlert();
  }

  /** Lädt die Mitglieder; nur die jeweils letzte Antwort einer noch offenen Instanz zählt (F-003, F-005). */
  async load() {
    const generation = ++this.loadGeneration;
    const sameSession = this.groupService.sessionGuard();
    this.loadState.set('loading');
    const ok = await this.groupService.getGroupMembers(this.groupId, {
      isCurrent: () => sameSession() && !this.closed && generation === this.loadGeneration,
    });
    if (this.closed || generation !== this.loadGeneration) return;
    this.loadState.set(ok ? 'loaded' : 'error');
  }

  actionsFor(member: UserDto): MemberActions {
    const members = this.members();
    return members ? memberActions(members, this.userId(), member.id) : { makeAdmin: false, remove: false };
  }

  hasActions(member: UserDto): boolean {
    const actions = this.actionsFor(member);
    return actions.makeAdmin || actions.remove;
  }

  isOwner(member: UserDto): boolean {
    return this.members()?.ownerId === member.id;
  }

  openMemberMenu(event: Event, member: UserDto) {
    event.stopPropagation();
    this.memberMenuId = member.id;
    this.memberMenuEvent = event;
    this.memberMenuOpen = true;
  }

  closeMemberMenu() {
    this.memberMenuOpen = false;
  }

  /** Mitglied des offenen Menüs aus der aktuellen Liste (kann per Live-Event inzwischen verschwunden sein). */
  menuMember(): UserDto | undefined {
    return this.rows().find(member => member.id === this.memberMenuId);
  }

  /** „Zum Admin machen“ (E1): erst nach Bestätigung; der bisherige Admin verliert danach seine Admin-Rechte. */
  async makeAdmin(member: UserDto) {
    if (this.closed || this.saving()) return;
    const confirmed = await this.confirm('alerts.group.change_owner', { memberName: member.userName, group: this.groupName });
    if (!confirmed || this.closed || this.saving()) return;
    await this.run(() => this.groupService.changeGroupOwner({ newOwner: { id: member.id, userName: member.userName }, groupId: this.groupId }));
  }

  /**
   * „Mitglied entfernen“ bzw. eigener Austritt mit Bestätigungs-Alert; nach dem eigenen Austritt schließt das Sheet.
   * DIV-109: Hat das Mitglied einen offenen Saldo, nennt der Alert ihn (eigener Text für Austritt und Entfernen) – ohne
   * zu blockieren. Saldo 0, unbekannt oder nicht ladbar: der bisherige Alert.
   */
  async removeMember(member: UserDto) {
    if (this.closed || this.saving()) return;
    const balance = await this.openBalance(member.id);
    if (this.closed) return;
    const own = member.id === this.userId();
    const key = balance === null ? 'alerts.group.remove_member'
      : own ? 'alerts.group.leave_open_balance' : 'alerts.group.remove_member_open_balance';
    const params: Record<string, string> = { memberName: member.userName, group: this.groupName };
    if (balance !== null) params['balance'] = this.currencyPipe.transform(balance, this.currencyCode());
    const confirmed = await this.confirm(key, params);
    if (!confirmed || this.closed || this.saving()) return;
    const ok = await this.run(() => this.groupService.removeMemberFromGroup({ id: this.groupId, name: this.groupName, member }));
    if (ok && member.id === this.userId()) this.closeWith(MEMBERS_SHEET_LEFT_ROLE);
  }

  /** Button „Mitglied hinzufügen“: wechselt im selben Sheet zur Eingabe (FA4). */
  openAdd() {
    this.email.reset('');
    this.addError.set(null);
    this.view.set('add');
  }

  /** „Abbrechen“ in der Eingabe: zurück zur Liste, Eingabe verworfen. */
  cancelAdd() {
    if (this.saving()) return;
    this.view.set('list');
  }

  /** Eingabe geändert: alte Meldung ausblenden. */
  onEmailInput() {
    this.addError.set(null);
  }

  async submitAdd() {
    if (this.closed || this.saving()) return;
    const email = this.email.value.trim();
    if (EmailValidator.isNotValid(email) || this.email.invalid) {
      this.addError.set('groups.members_sheet.email_invalid');
      return;
    }
    this.saving.set(true);
    this.email.disable({ emitEvent: false });
    try {
      const result = await this.groupService.addMemberToGroup({ id: this.groupId, name: this.groupName, newMemberEmail: email });
      if (this.closed) return;
      if (result.ok) {
        this.view.set('list');
      } else {
        this.addError.set(result.errorKey);
      }
    } catch (err) {
      // Der Service weist laut Vertrag nie zurück; falls doch, bleibt die Eingabe mit der Standardmeldung stehen.
      console.error('Error adding member:', err);
      this.addError.set('alerts.group.add_member.error_message_service.message_default');
    } finally {
      this.saving.set(false);
      this.email.enable({ emitEvent: false });
    }
  }

  close() {
    this.modalCtrl.dismiss(undefined, 'close', this.modalId);
  }

  /**
   * Offener Gesamtsaldo des Mitglieds in dieser Gruppe (DIV-109), auf Cent gerundet ≠ 0; sonst `null` (auch wenn er nicht
   * ladbar ist oder das Mitglied in den Salden fehlt). Während der Abfrage ist das Sheet wie bei einem Request gesperrt,
   * ein zweites Auslösen wirkt also nicht.
   */
  private async openBalance(memberId: number): Promise<number | null> {
    this.saving.set(true);
    try {
      const balances = await this.overviewService.loadUserBalances(this.groupId);
      const diff = balances?.find(user => user.userId === memberId)?.diff;
      return diff !== undefined && Math.round(diff * 100) !== 0 ? diff : null;
    } finally {
      this.saving.set(false);
    }
  }

  /** Währung der Gruppe für den Saldo im Alert (wie die übrige Betragsanzeige, DIV-77). */
  private currencyCode(): string {
    return this.groupService.groupOverviewList().find(group => group.id === this.groupId)?.currencyCode ?? DEFAULT_CURRENCY;
  }

  /** Führt einen Request mit Schließ-Sperre aus; der Service weist nie zurück. */
  private async run(request: () => Promise<boolean>): Promise<boolean> {
    this.saving.set(true);
    try {
      return await request();
    } catch (err) {
      console.error('Error in members sheet:', err);
      return false;
    } finally {
      this.saving.set(false);
    }
  }

  /**
   * Bestätigungs-Alert (`<key>.header/message/cancel/ok`); true nur bei der bestätigenden Aktion. Der Alert gehört zum
   * Sheet (Codex-Implementierungsreview F-002): Ist das Sheet beim Fertigstellen schon zu, erscheint er nicht; schließt
   * das Sheet, während er offen ist (Gruppenverlust, Logout), schließt er mit.
   */
  private async confirm(key: string, params: Record<string, string>): Promise<boolean> {
    const alert = await this.alertCtrl.create({
      header: this.translate.instant(`${key}.header`),
      message: this.translate.instant(`${key}.message`, params),
      buttons: [
        { text: this.translate.instant(`${key}.cancel`), role: 'cancel' },
        { text: this.translate.instant(`${key}.ok`), role: 'confirm' },
      ],
    });
    if (this.closed) {
      // Ionic hängt den Alert schon beim Erzeugen an; nie präsentiert hilft nur remove() (Codex-Review 2 F-003)
      alert.remove();
      return false;
    }
    this.openAlert = alert;
    try {
      await alert.present();
      const { role } = await alert.onDidDismiss();
      return role === 'confirm' && !this.closed;
    } finally {
      if (this.openAlert === alert) this.openAlert = undefined;
    }
  }

  private async focusWhenReady(input: HTMLIonInputElement) {
    try {
      await input.componentOnReady();
      await new Promise(resolve => requestAnimationFrame(resolve));
      if (!this.closed && input.isConnected) await input.setFocus();
    } catch {
      // Fokus ist eine Komfortfunktion; ohne ihn bleibt die Eingabe bedienbar
    }
  }

  private closeWith(role: string) {
    if (this.closed) return;
    this.closed = true;
    this.memberMenuOpen = false;
    this.dismissOpenAlert();
    this.modalCtrl.dismiss(undefined, role, this.modalId);
  }

  private dismissOpenAlert() {
    this.openAlert?.dismiss().catch(() => undefined);
    this.openAlert = undefined;
  }
}
