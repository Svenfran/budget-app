import { Component, effect, NgZone, Renderer2, signal } from '@angular/core';
import {Subscription } from 'rxjs';
import { AuthService } from './auth/auth.service';
import { Router } from '@angular/router';
import { User } from './auth/user';
import { WebSocketService } from './service/websocket.service';
import { GroupService } from './service/group.service';
import { Group } from './model/group';
import { AlertController, isPlatform, LoadingController, ModalController, NavController, Platform } from '@ionic/angular';
import { App } from '@capacitor/app';
import { Keyboard } from '@capacitor/keyboard';
import { PluginListenerHandle } from '@capacitor/core';
import { StatusBar, Style } from '@capacitor/status-bar';
import { NavigationBar } from '@hugotomazi/capacitor-navigation-bar';
import { StorageService } from './service/storage.service';
import { ChangeGroupOwnerDto } from './groupmembers/model/change-group-owner-dto';
import {
  applyMemberAddedToOverview, applyMemberRemovedFromOverview, applyMembersChange, applyOwnerChange,
  applyOwnerChangeToOverview, GroupMembersEvent, applyGroupUpdate, GroupUpdateEvent,
} from './service/group-events';
import { CategoryDto } from './model/category-dto';
import { CategoryService } from './service/category.service';
import { INIT_NUMBERS, INIT_VALUES } from './constants/default-values';
import { HealthCheckService } from './service/healthcheck.service';
import { TranslateService } from '@ngx-translate/core';
import { LanguageService } from './service/language.service';
import { ShoppinglistService } from './domains/shoppinglist/service/shoppinglist.service';
import { isLongBackground, ResyncThrottle } from './service/resync-throttle';
import { KeyboardOffset, offsetMinusViewportShift } from './service/keyboard-offset';
import { openGroupFormSheet } from './groupoverview/group-form-sheet/group-form-sheet-options';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent {
  private userSub!: Subscription;
  public shoppingLists: Group[] = [];
  public sideNavGroups = this.groupService.groupsSideNav;
  public groupOverviewList = this.groupService.groupOverviewList;
  public groupMembers = this.groupService.groupMembers;
  public user!: User;
  public isOpen: boolean = false;
  public activeGroup = this.groupService.activeGroup();
  public darkMode: boolean = true;
  private connectedUserId: number | null = null;
  // Stiller Resync der live-gesyncten Daten nach Reconnect / längerem Hintergrund (DIV-36)
  private resyncThrottle = new ResyncThrottle(
    () => this.resyncLiveData(),
    () => this.webSocketService.isConnected(),
    INIT_NUMBERS.RESYNC_MIN_INTERVAL_MS,
  );
  private reconnectedSub?: Subscription;
  private appStateListeners: Promise<PluginListenerHandle>[] = [];
  private keyboardListeners: Promise<PluginListenerHandle>[] = [];
  private removeResizeListener?: () => void;
  private pausedAt: number | null = null;
  private backButtonSubscription: any;
  public backendStatus = this.healthService.backendStatus;

  constructor(
    private authService: AuthService,
    private router: Router,
    private webSocketService: WebSocketService,
    private groupService: GroupService,
    private loadingCtrl: LoadingController,
    private alertCtrl: AlertController,
    private modalCtrl: ModalController,
    private platform: Platform,
    private navCtrl: NavController,
    private renderer: Renderer2,
    private storageService: StorageService,
    private categoryService: CategoryService,
    private healthService: HealthCheckService,
    private translate: TranslateService,
    private langService: LanguageService,
    private shoppinglistService: ShoppinglistService,
    private ngZone: NgZone
  ) {
    this.initializeApp();

    effect(() => {
      this.activeGroup = this.groupService.activeGroup();
      if (this.healthService.backendStatus() === INIT_VALUES.HEALTH_UP && this.router.url.includes(INIT_VALUES.SERVER_UNAVAILABLE)) {
        this.router.navigateByUrl('/domains/tabs/overview', { replaceUrl: true });
      }
    });
  }

  setActiveGroup(group: Group) {
    this.groupService.setActiveGroup(group);
  }

  groupHasMembers(group: Group): boolean {
    const foundGroup = this.groupOverviewList().find(gr => gr.id === group.id);
    return foundGroup?.memberCount! > 0;
  }

  /** Seitenmenü „Gruppe hinzufügen“: dasselbe Bottom Sheet wie in der Gruppenübersicht, mit Währungsauswahl (DIV-77). */
  onCreateGroup() {
    return openGroupFormSheet(this.modalCtrl, { mode: 'create', saving: signal(false) });
  }

  onCreateCategory() {
    this.alertCtrl.create({
      header: this.translate.instant('alerts.category.new.header'),
      buttons: [{
        text: this.translate.instant('alerts.category.new.cancel'),
        role: "cancel"
      }, {
        text: this.translate.instant('alerts.category.new.ok'),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant('alerts.category.new.loading')
          }).then(loadingEl => {
            if (!data) return;
            const trimmedCategoryName = data.categoryName.trim();
            if (trimmedCategoryName === "") return;

            const newCategory: CategoryDto = {
              name: trimmedCategoryName,
              groupId: this.activeGroup.id
            };
            this.categoryService.addCategory(newCategory);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "categoryName",
          placeholder: this.translate.instant('alerts.category.new.placeholder'),
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector('ion-alert input') as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }));
  }

  async initializeApp() {
    this.platform.ready().then(() => {
      this.storageService.getItem<boolean>('darkMode').then(dm => {
        if (dm == null) {
          this.storageService.setItem('darkMode', this.darkMode);
        } else {
          this.darkMode = dm;
        }

        this.renderer.setAttribute(
          document.body,
          'color-theme',
          this.darkMode ? 'dark' : 'light'
        );

        this.darkMode ? this.darkColorTheme() : this.lightColorTheme();
      });
    });
    this.initBackButton();
    this.initUser();
    this.initResync();
    this.initKeyboardOffset();
  }

  switchLanguage(lang: string) {
    this.langService.setLanguage(lang);
  }

  get currentLang() {
    return this.langService.currentLang()
  }

  initUser() {
    if (this.userSub) {
      this.userSub.unsubscribe();
    }

    this.userSub = this.authService.user.subscribe(user => {
      if (!user) {
        // Logout (manuell, Auto-Logout, Konto löschen …): Verbindung trennen
        if (this.connectedUserId !== null) {
          this.connectedUserId = null;
          this.resyncThrottle.reset();
          this.webSocketService.disconnect();
        }
        return;
      }

      this.user = user;
      if (user.id !== this.connectedUserId) {
        if (this.connectedUserId !== null) {
          this.resyncThrottle.reset();
          this.webSocketService.disconnect();
        }
        this.connectedUserId = user.id;
        this.subscribeToTopics();
        // vor jedem (Re-)Connect einen ausreichend lange gültigen Access Token holen (notfalls per Refresh, DIV-65)
        this.webSocketService.connect(() => this.authService.webSocketAuthorization());
      }
      this.groupService.getGroupsForSideNav();
      this.groupService.getGroupsForOverview();
      this.healthService.getHealthStatus();
    });
  }

  /** Auslöser für den stillen Resync: jedes Wiederverbinden und ein Resume nach längerem Hintergrund. */
  private initResync() {
    this.reconnectedSub = this.webSocketService.reconnected$.subscribe(() => this.resyncThrottle.request());
    this.appStateListeners = [
      App.addListener('pause', () => this.pausedAt = Date.now()),
      App.addListener('resume', () => this.ngZone.run(() => this.onResume())),
    ];
  }

  private onResume() {
    const longBackground = isLongBackground(this.pausedAt, Date.now(), INIT_NUMBERS.RESYNC_AFTER_BACKGROUND_MS);
    this.pausedAt = null;
    if (this.connectedUserId === null) return;
    // einzelne unsichtbare Abfrage, unabhängig von Verbindung und Drossel; aktualisiert nur backendStatus
    this.healthService.getHealthStatus();
    if (longBackground) {
      this.resyncThrottle.request();
    }
  }

  /**
   * Lädt die live-gesyncten Daten still neu (kein Leeren, kein Spinner, Fehler behalten den alten Stand): Gruppen,
   * Übersicht, Einkaufslisten der aktiven Gruppe und – falls geöffnet – die Mitglieder. Carts, Kategorien und
   * Statistiken nicht (nicht live-gesynct). Antworten aus einer inzwischen beendeten Anmelde-Sitzung (Logout/Login,
   * auch desselben Nutzers) bzw. für eine andere aktive Gruppe werden verworfen.
   */
  private resyncLiveData() {
    if (this.connectedUserId === null) return;
    const sameSession = this.groupService.sessionGuard();

    this.groupService.getGroupsForSideNav({ silent: true, isCurrent: sameSession });
    this.groupService.getGroupsForOverview({ silent: true, isCurrent: sameSession });

    const group = this.groupService.activeGroup();
    if (!group.flag?.includes(INIT_VALUES.DEFAULT)) {
      this.shoppinglistService.getShoppingListsWithItems(group, undefined, {
        silent: true,
        isCurrent: () => sameSession() && this.groupService.activeGroup().id === group.id,
      });
    }

    const members = this.groupMembers();
    if (!members.flag?.includes(INIT_VALUES.DEFAULT)) {
      this.groupService.getGroupMembers(members.id, { silent: true, isCurrent: sameSession });
    }
  }

  /**
   * Setzt `--keyboard-offset` (global.scss) auf den von der Tastatur verdeckten Teil der WebView, damit Alerts mit
   * Eingabefeld im freien Bereich zentriert werden – ab dem zweiten Öffnen synchron mit der Tastatur-Animation,
   * beim ersten Öffnen nach dem Einblenden (Geräteverhalten wird dabei gemessen, siehe KeyboardOffset).
   * Hat die WebView den sichtbaren Bereich zum fokussierten Feld hochgeschoben (`visualViewport.offsetTop`), wird diese
   * Verschiebung abgezogen, sonst stünden die Bottom Sheets zu hoch (DIV-70, offsetMinusViewportShift).
   */
  private initKeyboardOffset() {
    if (!isPlatform('capacitor')) return;
    const root = document.documentElement;
    const keyboardOffset = new KeyboardOffset(window.innerHeight, window.innerWidth);
    // zuletzt von KeyboardOffset gelieferter Offset (null: Tastatur zu oder Verhalten noch unbekannt)
    let baseOffset: number | null = null;
    const applyOffset = () => {
      if (baseOffset === null) return;
      const shifted = offsetMinusViewportShift(baseOffset, window.visualViewport?.offsetTop ?? 0);
      root.style.setProperty('--keyboard-offset', `${shifted}px`);
    };
    const setOffset = (px: number | null) => {
      if (px === null) return;
      baseOffset = px;
      applyOffset();
    };
    this.keyboardListeners = [
      Keyboard.addListener('keyboardWillShow', info => setOffset(keyboardOffset.willShow(info.keyboardHeight))),
      Keyboard.addListener('keyboardDidShow', info => setOffset(keyboardOffset.didShow(info.keyboardHeight, window.innerHeight))),
      Keyboard.addListener('keyboardWillHide', () => {
        keyboardOffset.willHide();
        baseOffset = null;
        root.style.removeProperty('--keyboard-offset');
      }),
      Keyboard.addListener('keyboardDidHide', () => keyboardOffset.didHide(window.innerHeight)),
    ];
    // Größenänderung: bei offener Tastatur neu messen (die WebView wird ggf. erst nach keyboardDidShow verkleinert), bei
    // geschlossener Tastatur nach einer Drehung die Höhe ohne Tastatur aktualisieren (KeyboardOffset.viewportResized)
    const onResize = () => setOffset(keyboardOffset.viewportResized(window.innerWidth, window.innerHeight));
    // Verschiebung des sichtbaren Bereichs (hoch beim Fokussieren, zurück per Wischen): Offset nachführen
    const onViewportScroll = () => applyOffset();
    window.addEventListener('resize', onResize);
    window.visualViewport?.addEventListener('scroll', onViewportScroll);
    this.removeResizeListener = () => {
      window.removeEventListener('resize', onResize);
      window.visualViewport?.removeEventListener('scroll', onViewportScroll);
    };
  }

  initBackButton() {
    if (this.backButtonSubscription) {
      this.backButtonSubscription.unsubscribe();
    }

    this.backButtonSubscription = this.platform.backButton.subscribeWithPriority(10, () => {
      const currentUrl = this.router.url;
      const pattern = new RegExp('auth|domains/tabs/overview|no-group');

      if (pattern.test(currentUrl)) {
        App.exitApp();
      } 
      
      this.navCtrl.back();
    });

  }

  onToggleColorTheme(event: any) {
    this.darkMode = event.detail.checked;
    if (this.darkMode) {
      this.renderer.setAttribute(document.body, 'color-theme', 'dark');
      this.darkColorTheme();
    } else {
      this.renderer.setAttribute(document.body, 'color-theme', 'light');
      this.lightColorTheme();
    }
    this.storageService.setItem('darkMode', this.darkMode);
  }

  darkColorTheme() {
    if (isPlatform('mobile')) {
      StatusBar.setStyle({ style: Style.Dark });
      StatusBar.setBackgroundColor({ color: '#000000'});
      NavigationBar.setColor({ color: '#000000', darkButtons: false });
    }
  }

  lightColorTheme() {
    if (isPlatform('mobile')) {
      StatusBar.setStyle({ style: Style.Light });
      StatusBar.setBackgroundColor({ color: '#FFFFFF'});
      NavigationBar.setColor({ color: '#FFFFFF', darkButtons: true });
    }
  }

  onLogout() {
    this.authService.logout();
    this.router.navigateByUrl("/auth", { replaceUrl: true });
  }

  ngOnDestroy() {
    this.userSub.unsubscribe();
    this.reconnectedSub?.unsubscribe();
    this.resyncThrottle.reset();
    this.appStateListeners.forEach(listener => listener.then(handle => handle.remove()));
    this.keyboardListeners.forEach(listener => listener.then(handle => handle.remove()));
    this.removeResizeListener?.();
  }

  subscribeToTopics() {
    this.webSocketService.subscribe<GroupUpdateEvent>(
      `/user/notification/update-group`,
      data => {
        // Name und Währung (DIV-77) in Seitenleiste, Übersicht und aktiver Gruppe
        this.sideNavGroups.update(groups => applyGroupUpdate(groups, data));
        this.groupOverviewList.update(groups => applyGroupUpdate(groups, data));

        if (this.activeGroup.id === data.id) {
          this.groupService.setActiveGroup(applyGroupUpdate([this.activeGroup], data)[0]);
        }
      },
      { owner: 'app' }
    );

    this.webSocketService.subscribe<Group>(
      `/user/notification/delete-group`,
      data => {
        this.sideNavGroups.update(groups => 
          groups.filter(group => group.id !== data.id)
        );

        this.groupOverviewList.update(groups => 
          groups.filter(group => group.id !== data.id)
        );
        
        if (this.activeGroup.id === data.id) {
          this.groupService.updateActiveGroup(this.sideNavGroups(), this.activeGroup);
        }

        if (this.sideNavGroups().length === 0) {
          this.groupService.hasNoGroups.set(true);
          this.router.navigateByUrl("/no-group", { replaceUrl: true });
        }
      },
      { owner: 'app' }
    );

    this.webSocketService.subscribe<GroupMembersEvent>(
      `/user/notification/remove-group-member`,
      parsedData => {
        console.log(parsedData);
        // Übersicht und offene Mitgliederliste (DIV-80) über pure Funktionen (group-events.ts)
        this.groupOverviewList.update(groups => applyMemberRemovedFromOverview(groups, parsedData, this.user?.id));
        this.groupMembers.update(groupMembers => applyMembersChange(groupMembers, parsedData));

        if (parsedData.updatedMember.id === this.user?.id) {
          this.sideNavGroups.update(
            groups => groups.filter(group => group.id !== parsedData.id)
          );
          this.groupService.updateActiveGroup(this.sideNavGroups(), this.activeGroup);
        }

        if (this.sideNavGroups().length === 0) {
          this.groupService.hasNoGroups.set(true);
          this.router.navigateByUrl("/no-group", { replaceUrl: true });
        }
      
      },
      { owner: 'app' }
    );
    
    this.webSocketService.subscribe<GroupMembersEvent>(
      `/user/notification/add-group-member`,
      parsedData => {
        console.log(parsedData);
        const group: Group = {
          id: parsedData.id, name: parsedData.name, dateCreated: parsedData.dateCreated, currencyCode: parsedData.currencyCode,
        };
        // Übersicht (inkl. ownerId) und offene Mitgliederliste (DIV-80) über pure Funktionen (group-events.ts)
        this.groupOverviewList.update(groups => applyMemberAddedToOverview(groups, parsedData, this.user?.id));
        this.groupMembers.update(groupMembers => applyMembersChange(groupMembers, parsedData));

        // wie in der Übersicht nicht doppelt aufnehmen (z. B. nach einem Resync vor dem Event)
        if (parsedData.updatedMember.id === this.user?.id && !this.sideNavGroups().some(g => g.id === group.id)) {
          this.sideNavGroups.update(groups => [...groups, group]);
        }

        if (this.router.url.includes(INIT_VALUES.NO_GROUP)) {
          this.groupService.setActiveGroup(group);
          this.router.navigateByUrl("/domains/tabs/overview", { replaceUrl: true });
        }
        // Eigene Aufnahme: still neu laden, falls die Währung inzwischen gewechselt wurde (DIV-77, D8)
        this.groupService.refreshAfterMembershipEvent(parsedData, this.user);
      
      },
      { owner: 'app' }
    );

    this.webSocketService.subscribe<ChangeGroupOwnerDto>(
      `/user/notification/change-group-owner`,
      data => {
        this.groupMembers.update(groupMembers => applyOwnerChange(groupMembers, data));
        this.groupOverviewList.update(groups => applyOwnerChangeToOverview(groups, data));
      },
      { owner: 'app' }
    );

    this.webSocketService.subscribe(
      `/notification/health`,
      (parsedData: any) => {
        this.backendStatus.set(parsedData.status);
        // this.backendStatus.set(INIT_VALUES.HEALTH_DOWN);

        if (this.backendStatus() === INIT_VALUES.HEALTH_DOWN) {
          this.router.navigateByUrl("/server-unavailable", { replaceUrl: true });
        } else if (this.backendStatus() === INIT_VALUES.HEALTH_UP && this.router.url.includes(INIT_VALUES.SERVER_UNAVAILABLE)) {
          this.router.navigateByUrl("/domains/tabs/overview", { replaceUrl: true });
        }

        console.log(parsedData);
      },
      { owner: 'app' }
    );

  }
}
