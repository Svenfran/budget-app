import { TestBed } from '@angular/core/testing';

import { ShoppingitemService } from './shoppingitem.service';

describe('ShoppingitemService', () => {
  let service: ShoppingitemService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ShoppingitemService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
