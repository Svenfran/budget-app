import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { AlertService } from 'src/app/service/alert.service';

/**
 * Standard-Provider für Service-Tests: HTTP über HttpTestingController gemockt, leerer Router,
 * TranslateService ohne Loader – `instant()` liefert den Key zurück, Alerts sind so per Key prüfbar.
 */
export function provideServiceTesting(): (Provider | EnvironmentProviders)[] {
  return [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService()];
}

export function alertServiceSpy(): jasmine.SpyObj<AlertService> {
  return jasmine.createSpyObj<AlertService>('AlertService', ['showErrorAlert']);
}
