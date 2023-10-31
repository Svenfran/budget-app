import { Injectable } from "@angular/core";
import { CanLoad, Router, Route, UrlSegment, UrlTree } from "@angular/router";
import { Observable, of } from "rxjs";
import { take, switchMap, tap, filter, map } from "rxjs/operators";
import { AuthService } from "./auth.service";


@Injectable({
    providedIn: 'root'
  })
  export class AuthGuard implements CanLoad {
  
    constructor(private authService: AuthService, private router: Router) {}
  
    // CanLoad runs before lazy-loading, other than CanActivate
    canLoad(route: Route, segments: UrlSegment[]): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
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