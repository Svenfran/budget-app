import { HttpRequest, HttpHandler } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { take, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthHttpInterceptorService {

  constructor(private authService: AuthService) { }

  intercept(request: HttpRequest<any>, next: HttpHandler) {
    
    return this.authService.token.pipe(
      take(1),
      switchMap(token => {
        let cloneReq = this.addToken(request, token);
        return next.handle(cloneReq);
      })
    )
  }


  private addToken(request: HttpRequest<any>, token: any) {
    if (token) {
      let clone: HttpRequest<any>;
      clone = request.clone({
        setHeaders: {
          Authorization: token
        }
      });
      return clone;
    }
    return request;
  }
}
