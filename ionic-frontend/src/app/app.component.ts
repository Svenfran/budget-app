import { Component, OnDestroy, OnInit, Renderer2, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { StatusBar, Style } from '@capacitor/status-bar';
import { NavigationBar } from '@hugotomazi/capacitor-navigation-bar';
import { AlertController, IonRouterOutlet, isPlatform, LoadingController, NavController, Platform } from '@ionic/angular';
import { CategoryDto } from './models/category';
import { Group } from './models/group';
import { GroupSideNav } from './models/group-side-nav';
import { CategoryService } from './services/category.service';
import { GroupService } from './services/group.service';
import { StorageService } from './services/storage.service';
import { AlertService } from './services/alert.service';
import { NavigationService } from './services/navigation.service';
import { AuthService } from './auth/auth.service';
import { Subscription } from 'rxjs';
import { UserDto } from './models/user';


@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent implements OnInit, OnDestroy {
  @ViewChild (IonRouterOutlet, {static: true}) routerOutlet: IonRouterOutlet;

  userName: string;
  grouplistSideNav: GroupSideNav[];
  isOpen = false;
  activeGroup: Group;
  groupModified: boolean;
  loadedActiveGroup: Promise<boolean>;
  loadedUser: Promise<boolean>;
  darkMode: boolean = false;
  GROUP_ID: string = "groupId";
  history: string[] = [];
  authSub: Subscription;
  previousAuthState = false;
  userDto: UserDto;
  hasGroupOwner: boolean;
  hasGroupMember: boolean;
  numberGroupOwner: number;
  numberGroupMember: number;


  constructor(
    private renderer: Renderer2,
    private platform: Platform,
    private router: Router,
    private navCtrl: NavController,
    private groupService: GroupService,
    private categoryService: CategoryService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private storageService: StorageService,
    private alertService: AlertService,
    private navigationService: NavigationService,
    private authService: AuthService) {
      this.initializeApp();
      this.getGroupsForSideNav();
      this.navigationService.startSaveHistory();
    }
    
    
    async initializeApp() {
      this.platform.ready().then(() => {
        this.storageService.getDarkMode().then(dMode => {
          if (dMode !== undefined || dMode !== null) {
            this.darkMode = dMode
          }
          
          if (this.darkMode) {
            this.renderer.setAttribute(document.body, 'color-theme', 'dark');
            this.darkColorTheme()
          } else {
            this.renderer.setAttribute(document.body, 'color-theme', 'light');
            this.lightColorTheme();
          }
        })
        this.initBackButton();
      })
    }

    
  ngOnInit() {
    this.authSub = this.authService.userIsAuthenticated.subscribe(isAuth => {
      if (!isAuth && this.previousAuthState !== isAuth) {
        this.router.navigateByUrl('/auth', { replaceUrl: true });
      }
      this.previousAuthState = isAuth;
      this.getCurrentUser();
      this.groupService.setGroupModified(true);

      this.groupService.getGroupsForSideNav().subscribe(groups => {
        this.loadedActiveGroup = Promise.resolve(true);

        this.storageService.getActiveGroup().then(group => {
          let hasGroup = false;
          if ((group !== null || group !== undefined) && groups.length > 0) {
            hasGroup = groups.some(gr => gr.id === group.id);
          }
          // console.log("hasGroup: " + hasGroup);
          // console.log("group from storage: " + group.name);
          // console.log("number of groups: " + groups.length);

          if (hasGroup) {
            this.activeGroup = group;
            this.storageService.setActiveGroup(this.activeGroup);
            this.groupService.setActiveGroup(this.activeGroup);
          } else if (groups.length > 0) {
            this.activeGroup = groups[0];
            this.storageService.setActiveGroup(this.activeGroup);
            this.groupService.setActiveGroup(this.activeGroup);
          } else {
            this.activeGroup = null;
            this.storageService.setActiveGroup(this.activeGroup);
            this.groupService.setActiveGroup(null);
          }
        })
      }, () => { return; });
      this.checkIfGroupCountHasChanged();
    });
  }

  ngOnDestroy(): void {
      this.authSub.unsubscribe();
  }

  getGroupsForSideNav() {
    this.groupService.groupModified.subscribe(() => {
      this.groupService.getGroupsForSideNav().subscribe(groups => {
        this.grouplistSideNav = groups;
        this.hasGroupOwner = this.grouplistSideNav.some(group => group.userDto.userName == this.userName);
        this.hasGroupMember = this.grouplistSideNav.some(group => group.userDto.userName != this.userName);
        this.numberGroupOwner = this.grouplistSideNav.filter(item => item.userDto.userName == this.userName).length;
        this.numberGroupMember = this.grouplistSideNav.filter(item => item.userDto.userName != this.userName).length;
      }, errRes => {
        // console.log(errRes.error);
        return;
      });
      this.groupService.activeGroup.subscribe(group => {
        this.activeGroup = group;
      })
    });
    this.groupService.setGroupModified(true);
    // this.groupService.loadGroups();
  }

  checkIfGroupCountHasChanged() {
    let hasError: number;
    setInterval(() => {
      if (hasError !== 403) {
        // console.log("checking group count...")
        this.groupService.getGroupsForSideNav().subscribe(groups => {
          if (this.grouplistSideNav.length !== groups.length) {
            let diffGroup = [
              ...this.getDifferenceGroup(this.grouplistSideNav, groups),
              ...this.getDifferenceGroup(groups, this.grouplistSideNav)
            ];
            // console.log("DIFF_GROUP: ");
            // console.log(diffGroup[0]);
            this.handleGroupChange(diffGroup, groups);
            this.groupService.setGroupModified(true);
          }  
        }, errRes => {
          hasError = errRes.error.status;
        });
      }
    }, 10000);
  }

  getDifferenceGroup(groupObj1: Group[], groupObj2: Group[]): Group[] {
    return groupObj1.filter(el1 => groupObj2.every(el2 => el2.id !== el1.id));
  }

  handleGroupChange(diffGroup: Group[], groupList: Group[]) {
    // console.log("ACTIVE_GROUP: ");
    // console.log(this.activeGroup);
    // console.log("NUMBER_OF_GROUPS: ");
    // console.log(groupList.length);

    if (this.activeGroup.id === diffGroup[0].id) {
      if (this.grouplistSideNav.length > 0) {
        this.groupService.setActiveGroup(groupList[0]);
        this.storageService.setActiveGroup(groupList[0]);
      } else {
        this.activeGroup = null;
        this.groupService.setActiveGroup(null);
        this.storageService.setActiveGroup(this.activeGroup);
      }
      if (this.router.url.includes("shoppinglist")) {
        this.router.navigate(['/domains/tabs/overview']);
      }
    } else if (this.activeGroup.id === null) {
      this.groupService.setActiveGroup(groupList[0]);
      this.storageService.setActiveGroup(groupList[0]);
      if (this.router.url.includes("shoppinglist")) {
        this.router.navigate(['/domains/tabs/overview']);
      }
    }
  }


  initBackButton() {
    this.platform.backButton.subscribeWithPriority(10, () => {
      const currentUrl = this.router.url;
      if (currentUrl === "/auth" || currentUrl === "/domains/tabs/overview") {
        navigator['app'].exitApp();
      } else {
        this.navCtrl.back();
      }
    })
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
 
    this.storageService.setDarkMode(this.darkMode);
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

  getActiveGroup(id: number) {
    this.activeGroup = this.grouplistSideNav.filter(group => group.id == id)[0];
    this.groupService.setActiveGroup(this.activeGroup);
    this.storageService.setActiveGroup(this.activeGroup);
  }

  onCreateGroup() {
    this.alertService.createGroup();
  }

  onCreateCategory() {
    this.alertCtrl.create({
      header: "Neue Kategorie:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Erstelle Kategorie..."
          }).then(loadingEl => {
            let newCategory = new CategoryDto(null, data.categoryName, this.activeGroup.id);
            this.categoryService.addCategory(newCategory).subscribe(() => {
              loadingEl.dismiss();
              this.groupService.setGroupModified(true);
            })
          })
        }
      }],
      inputs: [
        {
          name: "categoryName",
          placeholder: "Name der Kategorie"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  getCurrentUser() {
    this.authService.userName.subscribe(name => {
      this.loadedUser = Promise.resolve(true);
      this.userName = name;
    })
    return this.userName;
  }
}
