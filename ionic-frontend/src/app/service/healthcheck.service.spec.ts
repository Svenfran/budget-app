import { HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { environment } from 'src/environments/environment';
import { provideServiceTesting } from 'src/testing/test-providers';
import { INIT_NUMBERS, INIT_VALUES } from '../constants/default-values';
import { HealthCheckService } from './healthcheck.service';

describe('HealthCheckService', () => {
  let service: HealthCheckService;
  let http: HttpTestingController;
  let onComplete: jasmine.Spy;
  const healthUrl = `${environment.apiBaseUrl}/actuator/health`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideServiceTesting() });
    service = TestBed.inject(HealthCheckService);
    http = TestBed.inject(HttpTestingController);
    onComplete = jasmine.createSpy('onComplete');
    spyOn(console, 'error');
  });

  afterEach(() => http.verify());

  it('übernimmt den gemeldeten Status', () => {
    service.getHealthStatus(onComplete);
    http.expectOne(healthUrl).flush({ status: INIT_VALUES.HEALTH_UP });

    expect(service.backendStatus()).toBe(INIT_VALUES.HEALTH_UP);
    expect(onComplete).toHaveBeenCalledTimes(1);
  });

  it('setzt DOWN bei einer Fehlerantwort (z. B. 503 bei nicht erreichbarer DB)', () => {
    service.backendStatus.set(INIT_VALUES.HEALTH_UP);

    service.getHealthStatus(onComplete);
    http.expectOne(healthUrl).flush({ status: 'DOWN' }, { status: 503, statusText: 'Service Unavailable' });

    expect(service.backendStatus()).toBe(INIT_VALUES.HEALTH_DOWN);
    expect(onComplete).toHaveBeenCalledTimes(1);
  });

  it('setzt DOWN und bricht ab, wenn der Server nicht innerhalb des Timeouts antwortet', fakeAsync(() => {
    service.backendStatus.set(INIT_VALUES.HEALTH_UP);

    service.getHealthStatus(onComplete);
    const request = http.expectOne(healthUrl);
    tick(INIT_NUMBERS.HEALTH_CHECK_TIMEOUT_MS - 1);
    expect(onComplete).not.toHaveBeenCalled();

    tick(1);

    expect(request.cancelled).toBeTrue();
    expect(service.backendStatus()).toBe(INIT_VALUES.HEALTH_DOWN);
    expect(onComplete).toHaveBeenCalledTimes(1);
  }));
});
