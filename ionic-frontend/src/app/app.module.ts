import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouteReuseStrategy } from '@angular/router';

import { IonicModule, IonicRouteStrategy } from '@ionic/angular';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { FileOpener } from '@ionic-native/file-opener/ngx';
import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { AsyncPipe, CommonModule, CurrencyPipe, DatePipe, registerLocaleData } from '@angular/common';
import localDe from '@angular/common/locales/de';
import { ReactiveFormsModule } from '@angular/forms';
import { AuthHttpInterceptorService } from './auth/auth-http-interceptor.service';
import { SettlementPaymentPage } from './settlement-payment/settlement-payment.page';
import { GroupMembersPage } from './group-members/group-members.page';
registerLocaleData(localDe, 'de');

@NgModule({
  declarations: [AppComponent, SettlementPaymentPage, GroupMembersPage],
  imports: [
    BrowserModule,
    CommonModule,
    IonicModule.forRoot(), 
    AppRoutingModule,
    HttpClientModule,
    ReactiveFormsModule,
  ],
  providers: [
    DatePipe,
    CurrencyPipe,
    AsyncPipe,
    FileOpener,
    { provide: HTTP_INTERCEPTORS, useClass: AuthHttpInterceptorService, multi: true },
    { provide: RouteReuseStrategy, useClass: IonicRouteStrategy }
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
