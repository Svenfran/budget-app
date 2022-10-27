import { Component, Renderer2 } from '@angular/core';
import { Router } from '@angular/router';
import { StatusBar, Style } from '@capacitor/status-bar';
import { NavigationBar } from '@hugotomazi/capacitor-navigation-bar';
import { AlertController, isPlatform, LoadingController, NavController, Platform } from '@ionic/angular';
import { Group } from './models/group';
import { GroupSideNav } from './models/group-side-nav';
import { GroupService } from './services/group.service';
import { Storage } from '@ionic/storage';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent {

  userName: string;
  grouplistSideNav: GroupSideNav[];
  isOpen = false;
  activeGroup: GroupSideNav;
  groupModified: boolean;

  constructor(
    private renderer: Renderer2,
    private platform: Platform,
    private router: Router,
    private navCtrl: NavController,
    private groupService: GroupService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController) {
      this.initializeApp();
      this.getCurrentUser();
      this.getGroupsForSideNav();
    }


  initializeApp() {
    this.platform.ready().then(() => {
      this.lightColorTheme();
      this.initBackButton();
    })
  }

  ngOnInit() {
    this.groupService.getGroupsForSideNav().subscribe(groups => {
      this.activeGroup = groups[0];
      this.groupService.setActiveGroup(this.activeGroup);
    })
  }
  
  getGroupsForSideNav() {
    this.groupService.groupModified.subscribe(() => {
      this.groupService.getGroupsForSideNav().subscribe(groups => {
        this.grouplistSideNav = groups.sort((a, b) => a.id < b.id ? -1 : 1 );
      })
      this.groupService.activeGroup.subscribe(group => {
        this.activeGroup = group;
      })
    })
    this.groupService.setGroupModified(false);
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

  onToggleColorTheme(event) {
    if (event.detail.checked) {
      this.renderer.setAttribute(document.body, 'color-theme', 'dark');
      this.darkColorTheme();
    } else {
      this.renderer.setAttribute(document.body, 'color-theme', 'light');
      this.lightColorTheme();
    }
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
    console.log("Logging out...")
  }

  getCurrentUser() {
    this.userName = "sven";
  }

  getActiveGroup(id: number) {
    this.activeGroup = this.grouplistSideNav.filter(group => group.id == id)[0];
    this.groupService.setActiveGroup(this.activeGroup);
  }

  onCreateGroup() {
    this.alertCtrl.create({
      header: "Neue Gruppe:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Erstelle Gruppe..."
          }).then(loadingEl => {
            let newGroup = new Group(null, data.groupName, null);
            this.groupService.addGroup(newGroup).subscribe(() => {
              loadingEl.dismiss();
              this.groupService.setGroupModified(true);
            })
          })
        }
      }],
      inputs: [
        {
          name: "groupName",
          placeholder: "Gruppenname"
        }
      ]
    }).then(alertEl => alertEl.present());
  }
}
