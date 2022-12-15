import { TestBed } from '@angular/core/testing';

import { SpendingsOverviewService } from './spendings-overview.service';

describe('SpendingsOverviewService', () => {
  let service: SpendingsOverviewService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SpendingsOverviewService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
