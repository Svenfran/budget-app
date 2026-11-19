import { Component, DestroyRef, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { GroupService } from '../service/group.service';
import { User } from '../auth/user';
import { AuthService } from '../auth/auth.service';
import { AlertController, ModalController } from '@ionic/angular';
import { Group } from '../model/group';
import { GroupOverview } from './model/group-overview';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { isGroupAdmin } from './group-permissions';
import { GroupFormSheetMode } from './group-form-sheet/group-form-sheet.component';
import { groupFormSheetModalOptions } from './group-form-sheet/group-form-sheet-options';
import { MembersSheetView } from './members-sheet/members-sheet.component';
import { membersSheetModalOptions } from './members-sheet/members-sheet-options';

@Component({
  selector: 'app-groupoverview',
  templateUrl: './groupoverview.page.html',
  styleUrls: ['./groupoverview.page.scss'],
  standalone: false
})
export class GroupoverviewPage implements OnInit {

  public isLoading: boolean = false;
  public groupOverviewList = this.groupService.groupOverviewList;
  /** markiert die aktive Gruppe in der Liste */
  public activeGroupId = this.groupService.activeGroupId;
  public user: User | undefined;
  /** ⋮-Menü (DIV-80): ein Popover für alle Gruppen. */
  public menuOpen = false;
  public menuEvent: Event | undefined;
  private menuGroupId: number | null = null;

  constructor(
    private groupService: GroupService,
    private authService: AuthService,
    private alertCtrl: AlertController,
    private modalCtrl: ModalController,
    private router: Router,
    private translate: TranslateService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit() {
    // Abo endet mit der Seite, sonst lädt jede frühere Instanz bei jeder Nutzer-Emission mit (DIV-60)
    this.authService.user.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
      // Logout: kein Request ohne Token, der Fehler würde sonst die Übersicht leeren (DIV-60)
      if (!user) return;
      this.user = user;
      this.groupService.getGroupsForOverview();
    })
  }

  refreshGroupList(event: CustomEvent) {
    setTimeout(() => {
      this.groupService.getGroupsForOverview();
      (event.target as HTMLIonRefresherElement).complete();
    }, 2000);
  }

  /**
   * Setzt die angeklickte Gruppe aktiv; die Liste markiert sie. Bewusst ohne Navigation (DIV-72) – zurück geht es über
   * den Zurück-Button der Seite.
   */
  onSelectGroup(group: GroupOverview) {
    if (this.activeGroupId() !== group.id) {
      this.groupService.setActiveGroup(group);
    }
  }

  /** Gruppenadmin (per User-ID, DIV-80): nur er sieht „Gruppe bearbeiten“ und „Gruppe löschen“. */
  isAdmin(group: GroupOverview): boolean {
    return isGroupAdmin(group, this.user);
  }

  /** ⋮ am Gruppeneintrag (DIV-80): öffnet das Aktionsmenü, ohne die Gruppe auszuwählen. */
  openGroupMenu(event: Event, group: GroupOverview) {
    event.stopPropagation();
    this.menuGroupId = group.id;
    this.menuEvent = event;
    this.menuOpen = true;
  }

  closeGroupMenu() {
    this.menuOpen = false;
  }

  /** Gruppe des offenen Menüs aus der aktuellen Liste (kann per Live-Event inzwischen verschwunden sein). */
  menuGroup(): GroupOverview | undefined {
    return this.groupOverviewList().find(group => group.id === this.menuGroupId);
  }

  /**
   * Menüpunkt „Mitglieder verwalten“: Bottom Sheet mit Mitgliederliste und „Mitglied hinzufügen“ in einem (DIV-80,
   * ersetzt GroupmembersPage). `saving` und `view` teilt das Sheet mit seiner Schließ-Sperre.
   */
  async onManageMembers(group: GroupOverview) {
    const modal = await this.modalCtrl.create(membersSheetModalOptions({
      groupId: group.id,
      groupName: group.name,
      saving: signal(false),
      view: signal<MembersSheetView>('list'),
    }));
    await modal.present();
  }

  /** `+` im Header: Bottom Sheet „Gruppe hinzufügen“ (DIV-80, ersetzt den Alert). */
  onCreateGroup() {
    return this.openGroupFormSheet('create');
  }

  /** Menüpunkt „Gruppe bearbeiten“: Bottom Sheet mit dem aktuellen Namen (DIV-80, ersetzt den Alert). */
  onUpdateGroup(group: GroupOverview) {
    return this.openGroupFormSheet('edit', group);
  }

  /**
   * Öffnet „Gruppe hinzufügen / bearbeiten“. Das Sheet speichert selbst und schließt erst nach Erfolg; `saving` sperrt das
   * Schließen, solange der Request läuft. Fokus wie bisher im Alert direkt ins Namensfeld.
   */
  async openGroupFormSheet(mode: GroupFormSheetMode, group?: GroupOverview) {
    const modal = await this.modalCtrl.create(groupFormSheetModalOptions({ mode, group, saving: signal(false) }));
    await modal.present();
    await (modal.querySelector('ion-input.group-name-input') as HTMLIonInputElement | null)?.setFocus();
  }

  /**
   * Menüpunkt „Gruppe löschen“: direkt die Sicherheitsabfrage (DIV-80, FA6) mit Gruppenname und Folgen; gelöscht wird
   * nur über den destruktiven Button.
   */
  onDeleteGroup(group: Group): Promise<void> {
    return this.alertCtrl.create({
      header: this.translate.instant("alerts.group.delete.header"),
      message: this.translate.instant("alerts.group.delete.message", {groupName: group.name}),
      buttons: [{
        text: this.translate.instant("alerts.group.delete.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.group.delete.ok"),
        role: "destructive",
        // Ionic färbt `destructive` nur im iOS-Stil; die Danger-Farbe kommt aus global.scss
        cssClass: "alert-button-danger",
        handler: () => {
          this.deleteGroup(group);
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  /**
   * Löscht die Gruppe nach der Bestätigung. War es die letzte, geht es erst nach erfolgreichem Löschen zu no-group
   * (DIV-80; vorher schon vor der Antwort, ein Fehler ließ den Nutzer dann ohne Gruppe dort stehen).
   */
  async deleteGroup(group: Group) {
    const deleted = await this.groupService.deleteGroup(group);
    if (deleted && this.groupOverviewList().length === 0) {
      this.groupService.hasNoGroups.set(true);
      this.router.navigate(['/no-group'], { replaceUrl: true });
    }
  }
}
