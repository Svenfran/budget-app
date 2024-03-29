import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class NavigationService {

  history: string[] = [];

  constructor(
    private router: Router
  ) { }

  startSaveHistory() {
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.history.push(event.urlAfterRedirects)
      }
    })
  }

  getPreviousUrl() {
    if (this.history.length > 0) {
      return this.history[this.history.length - 2];
    }
    return '';
  }
}
