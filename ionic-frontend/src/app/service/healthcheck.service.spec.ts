import { TestBed } from '@angular/core/testing';
import { describe, beforeEach, it } from 'node:test';
import { HealthCheckService } from './healthcheck.service';

describe('WebsocketService', () => {
  let service: HealthCheckService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(HealthCheckService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
