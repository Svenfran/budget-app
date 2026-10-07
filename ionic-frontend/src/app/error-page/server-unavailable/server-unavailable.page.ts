import { Component, OnInit } from '@angular/core';
import { App } from '@capacitor/app';
import { MenuController } from '@ionic/angular';
import { HealthCheckService } from 'src/app/service/healthcheck.service';

@Component({
  selector: 'app-server-unavailable',
  templateUrl: './server-unavailable.page.html',
  styleUrls: ['./server-unavailable.page.scss'],
  standalone: false
})
export class ServerUnavailablePage implements OnInit {

  public isLoading: boolean = false;

  constructor(
    private menuCtrl: MenuController,
    private healthService: HealthCheckService
  ) { }

  ngOnInit() {
    this.menuCtrl.enable(false);
  }

  ionViewWillLeave() {
    this.menuCtrl.enable(true);
  }

  exitApp() {
    App.exitApp();
  }

  reloadApp() {
    this.isLoading = true;
    this.healthService.getHealthStatus(() => {
      this.isLoading = false;
    });
  }

}
