import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { App } from '@capacitor/app';
import { MenuController, ModalController } from '@ionic/angular';
import { openGroupFormSheet } from 'src/app/groupoverview/group-form-sheet/group-form-sheet-options';

@Component({
  selector: 'app-no-group',
  templateUrl: './no-group.page.html',
  styleUrls: ['./no-group.page.scss'],
  standalone: false
})
export class NoGroupPage implements OnInit {

  constructor(
    private menuCtrl: MenuController,
    private modalCtrl: ModalController,
    private router: Router,
  ) {}

  ngOnInit() {
    this.menuCtrl.enable(false);
  }

  ionViewWillLeave() {
    this.menuCtrl.enable(true);
  }

  ionViewDidEnter() {
    this.menuCtrl.enable(false);
  }

  exitApp() {
    App.exitApp();
  }

  /**
   * „Gruppe erstellen“: dasselbe Bottom Sheet wie in der Gruppenübersicht, mit Währungsauswahl (DIV-77). Die aufrufende
   * Seite geht mit, damit GroupService.addGroup nach Erfolg zur Übersicht navigiert.
   */
  onCreateGroup() {
    return openGroupFormSheet(this.modalCtrl, { mode: 'create', saving: signal(false), callingPage: this.router.url });
  }
  
}
