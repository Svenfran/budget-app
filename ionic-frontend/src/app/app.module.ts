import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouteReuseStrategy } from '@angular/router';
import { ScrollingModule } from '@angular/cdk/scrolling';

import { IonicModule, IonicRouteStrategy } from '@ionic/angular';

import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';
import { AuthHttpInterceptorService } from './auth/auth-http-interceptor.service';
import { CommonModule } from '@angular/common';
import { ScientificCurrencyPipe } from './pipe/scientific.pipe';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { CustomTranslateLoader } from './custom-translate-loader';

export function createTranslateLoader(http: HttpClient) {
  return new CustomTranslateLoader(http);
}

@NgModule({
  declarations: [AppComponent],
  imports: [ 
    BrowserModule,
    CommonModule,
    IonicModule.forRoot(),
    AppRoutingModule,
    ReactiveFormsModule,
    ScrollingModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: createTranslateLoader,
        deps: [HttpClient]
      }
    }),
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthHttpInterceptorService, multi: true },
    { provide: RouteReuseStrategy, useClass: IonicRouteStrategy },
    provideHttpClient(withInterceptorsFromDi()),
    ScientificCurrencyPipe
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
