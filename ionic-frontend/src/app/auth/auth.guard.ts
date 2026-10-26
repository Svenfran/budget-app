import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, CanLoad, Router, Route, RouterStateSnapshot, UrlSegment, UrlTree } from "@angular/router";
import { Observable, of } from "rxjs";
import { take, switchMap, tap, filter, map } from "rxjs/operators";
import { AuthService } from "./auth.service";


@Injectable({
    providedIn: 'root'
  })
  export class AuthGuard implements CanLoad, CanActivate {

    constructor(private authService: AuthService, private router: Router) {}

    // CanLoad runs before lazy-loading, other than CanActivate
    canLoad(route: Route, segments: UrlSegment[]): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
      return this.checkAuthenticated();
    }

    // canLoad greift nur beim ersten Laden des Moduls; canActivate prüft auch die Rückkehr zu bereits geladenen
    // Seiten, z. B. per Browser-Zurück nach einem Logout (DIV-57)
    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
      return this.checkAuthenticated();
    }

    private checkAuthenticated(): Observable<boolean> {
      return this.authService.userIsAuthenticated.pipe(
        filter((val) => val !== null),
        take(1),
        switchMap(isAuthenticated => {
          if (!isAuthenticated) {
            return this.authService.autoLogin();
          } else {
            return of(isAuthenticated);
          }
        }),
        tap(isAuthenticated => {
          // console.log("isAuthenticated: " + isAuthenticated);
          if (!isAuthenticated) {
            this.router.navigateByUrl('/auth', {replaceUrl: true});
          }
        })
      );
    }
    
  }