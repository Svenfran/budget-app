import { Component, Renderer2 } from '@angular/core';
import { Router } from '@angular/router';
import { StatusBar, Style } from '@capacitor/status-bar';
import { NavigationBar } from '@hugotomazi/capacitor-navigation-bar';
import { isPlatform, NavController, Platform } from '@ionic/angular';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
})
export class AppComponent {
  constructor(
    private renderer: Renderer2,
    private platform: Platform,
    private router: Router,
    private navCtrl: NavController) {
      this.initializeApp();
    }


  initializeApp() {
    this.platform.ready().then(() => {
      this.lightColorTheme();
      this.initBackButton();
    })
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
}
