import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { GROUP_SELECTION_FALLBACK_URL, returnAfterGroupSelection } from './group-navigation';

@Component({ template: '', standalone: true })
class BlankComponent {}

/** Echter Router mit Location-Mock, damit die Annahme über `lastSuccessfulNavigation` mitgeprüft wird. */
describe('returnAfterGroupSelection (Gruppenauswahl, Codex-Review DIV-72 F-001)', () => {
  let router: Router;
  let location: Location;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'domains/tabs/statistics', component: BlankComponent },
          { path: 'groupoverview', component: BlankComponent },
          { path: 'domains/tabs/overview', component: BlankComponent },
        ]),
        provideLocationMocks(),
      ],
    });
    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
  });

  it('mit App-interner Vorgängerseite geht es dorthin zurück', async () => {
    await router.navigateByUrl('/domains/tabs/statistics');
    await router.navigateByUrl('/groupoverview');
    const back = spyOn(location, 'back');
    const navigate = spyOn(router, 'navigateByUrl').and.callThrough();

    returnAfterGroupSelection(router, location);

    expect(back).toHaveBeenCalledTimes(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('beim Direkteinstieg ohne Vorgängerseite zur Übersicht, ohne neuen History-Eintrag', async () => {
    await router.navigateByUrl('/groupoverview');
    const back = spyOn(location, 'back');
    const navigate = spyOn(router, 'navigateByUrl').and.callThrough();

    returnAfterGroupSelection(router, location);
    await navigate.calls.mostRecent().returnValue;

    expect(back).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledOnceWith(GROUP_SELECTION_FALLBACK_URL, { replaceUrl: true });
    expect(location.path()).toBe(GROUP_SELECTION_FALLBACK_URL);
  });
});
