import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CategoryDto } from 'src/app/model/category-dto';
import { environment } from 'src/environments/environment';
import { aCategory, aGroup } from 'src/testing/test-data';
import { alertServiceSpy, provideServiceTesting } from 'src/testing/test-providers';
import { AlertService } from './alert.service';
import { CategoryService } from './category.service';

describe('CategoryService', () => {
  let service: CategoryService;
  let http: HttpTestingController;
  let alerts: jasmine.SpyObj<AlertService>;
  const api = `${environment.apiBaseUrl}/api/groups`;
  const names = (categories: CategoryDto[]) => categories.map(c => c.name);

  beforeEach(() => {
    alerts = alertServiceSpy();
    TestBed.configureTestingModule({
      providers: [...provideServiceTesting(), { provide: AlertService, useValue: alerts }],
    });
    service = TestBed.inject(CategoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('Laden', () => {
    it('lädt die Kategorien der Gruppe', () => {
      const loaded = [aCategory({ name: 'Drogerie' }), aCategory({ name: 'Lebensmittel' })];

      service.getCategoriesByGroup(aGroup({ id: 7 }));
      http.expectOne(`${api}/categories/7`).flush(loaded);

      expect(service.categories()).toEqual(loaded);
    });

    it('leert die Kategorien bei einem Ladefehler', () => {
      service.categories.set([aCategory()]);

      service.getCategoriesByGroup(aGroup({ id: 7 }));
      http.expectOne(`${api}/categories/7`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.categories()).toEqual([]);
    });
  });

  describe('addCategory', () => {
    it('fügt die neue Kategorie nach dem Speichern alphabetisch ein', () => {
      service.categories.set([aCategory({ id: 1, name: 'Drogerie' }), aCategory({ id: 2, name: 'Lebensmittel' })]);
      const newCategory = aCategory({ id: undefined, name: 'Freizeit' });

      service.addCategory(newCategory);
      const req = http.expectOne(`${api}/category/add`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newCategory);
      req.flush({ ...newCategory, id: 3 });

      expect(names(service.categories())).toEqual(['Drogerie', 'Freizeit', 'Lebensmittel']);
      expect(service.categories()[1].id).toBe(3);
    });

    it('lässt die Kategorien bei einem Fehler unverändert', () => {
      const existing = [aCategory({ id: 1, name: 'Drogerie' })];
      service.categories.set(existing);

      service.addCategory(aCategory({ id: undefined, name: 'Freizeit' }));
      http.expectOne(`${api}/category/add`).flush('User with ID 5 is either a member nor the owner of the group',
        { status: 403, statusText: 'Forbidden' });

      expect(service.categories()).toEqual(existing);
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });

  describe('updateCategory', () => {
    it('übernimmt den neuen Namen nach dem Speichern, sortiert neu und meldet die Änderung', () => {
      service.categories.set([aCategory({ id: 1, name: 'Drogerie' }), aCategory({ id: 2, name: 'Lebensmittel' })]);
      const updatesBefore = service.categoryUpdate();
      const changed = aCategory({ id: 1, name: 'Zoo' });

      service.updateCategory(changed);
      const req = http.expectOne(`${api}/category/update`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(changed);
      req.flush(changed);

      expect(names(service.categories())).toEqual(['Lebensmittel', 'Zoo']);
      expect(service.categories()[1].id).toBe(1);
      expect(service.categoryUpdate()).toBe(updatesBefore + 1);
    });

    it('lässt die Kategorien bei einem Fehler unverändert und meldet keine Änderung', () => {
      const existing = [aCategory({ id: 1, name: 'Drogerie' }), aCategory({ id: 2, name: 'Lebensmittel' })];
      service.categories.set(existing);
      const updatesBefore = service.categoryUpdate();

      service.updateCategory(aCategory({ id: 1, name: 'Zoo' }));
      http.expectOne(`${api}/category/update`).flush('User with ID 5 is either a member nor the owner of the group',
        { status: 403, statusText: 'Forbidden' });

      expect(service.categories()).toEqual(existing);
      expect(service.categoryUpdate()).toBe(updatesBefore);
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });

  describe('deleteCategory', () => {
    it('entfernt die Kategorie nach dem Löschen', () => {
      const toDelete = aCategory({ id: 1, name: 'Drogerie' });
      service.categories.set([toDelete, aCategory({ id: 2, name: 'Lebensmittel' })]);

      service.deleteCategory(toDelete);
      const req = http.expectOne(`${api}/category/delete`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(toDelete);
      req.flush(null);

      expect(names(service.categories())).toEqual(['Lebensmittel']);
    });

    it('lässt die Kategorien unverändert und zeigt einen Hinweis, wenn die Kategorie noch verwendet wird', () => {
      const toDelete = aCategory({ id: 1, name: 'Drogerie' });
      const existing = [toDelete, aCategory({ id: 2, name: 'Lebensmittel' })];
      service.categories.set(existing);
      const instant = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();

      service.deleteCategory(toDelete);
      http.expectOne(`${api}/category/delete`).flush('Category with Id 1 is used by carts',
        { status: 404, statusText: 'Not Found' });

      expect(service.categories()).toEqual(existing);
      expect(alerts.showErrorAlert).toHaveBeenCalledWith(
        'alerts.category.delete.error_message_service.header', 'alerts.category.delete.error_message_service.message');
      expect(instant).toHaveBeenCalledWith(
        'alerts.category.delete.error_message_service.message', { categoryName: 'Drogerie' });
    });

    it('lässt die Kategorien bei einem anderen Fehler unverändert und zeigt keinen Hinweis', () => {
      const toDelete = aCategory({ id: 1, name: 'Drogerie' });
      const existing = [toDelete, aCategory({ id: 2, name: 'Lebensmittel' })];
      service.categories.set(existing);

      service.deleteCategory(toDelete);
      http.expectOne(`${api}/category/delete`).flush('User with ID 5 is either a member nor the owner of the group',
        { status: 403, statusText: 'Forbidden' });

      expect(service.categories()).toEqual(existing);
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });
});
