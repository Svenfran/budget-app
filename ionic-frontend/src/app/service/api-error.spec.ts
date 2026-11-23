import { apiErrorCode, apiErrorUserId } from './api-error';

describe('api-error', () => {
  const withBody = (error: unknown) => ({ status: 400, error });

  describe('apiErrorCode', () => {
    it('liest den Code aus dem JSON-Fehlerbody', () => {
      expect(apiErrorCode(withBody({ code: 'INCORRECT_PASSWORD', message: 'Incorrect password' }))).toBe('INCORRECT_PASSWORD');
    });

    it('wertet nur den Code aus, nicht den Text', () => {
      expect(apiErrorCode(withBody({ code: 'INCORRECT_PASSWORD', message: 'geänderter Text' }))).toBe('INCORRECT_PASSWORD');
    });

    [
      { name: 'Text-Body (Legacy-Format)', body: 'Incorrect password' },
      { name: 'JSON ohne Code (Spring-Standardfehler)', body: { status: 500, error: 'Internal Server Error' } },
      { name: 'Code ist kein String', body: { code: 42 } },
      { name: 'null-Body', body: null },
      { name: 'ProgressEvent (Netzwerkfehler)', body: new ProgressEvent('error') },
      { name: 'Blob (Export)', body: new Blob(['{"code":"X"}']) },
    ].forEach(c => {
      it(`liefert null bei ${c.name}`, () => {
        expect(apiErrorCode(withBody(c.body))).toBeNull();
      });
    });

    it('liefert null ohne Fehlerobjekt', () => {
      expect(apiErrorCode(null)).toBeNull();
      expect(apiErrorCode(undefined)).toBeNull();
      expect(apiErrorCode('boom')).toBeNull();
    });
  });

  describe('apiErrorUserId', () => {
    it('liest details.userId', () => {
      expect(apiErrorUserId(withBody({ code: 'DATE_NOT_WITHIN_MEMBERSHIP', details: { userId: 42 } }))).toBe(42);
    });

    [
      { name: 'ohne details', body: { code: 'DATE_NOT_WITHIN_MEMBERSHIP' } },
      { name: 'userId kein Number', body: { code: 'DATE_NOT_WITHIN_MEMBERSHIP', details: { userId: '42' } } },
      { name: 'details kein Objekt', body: { code: 'DATE_NOT_WITHIN_MEMBERSHIP', details: 42 } },
      { name: 'Text-Body', body: 'Date purchased is not within membership period' },
      { name: 'null-Body', body: null },
    ].forEach(c => {
      it(`liefert null ${c.name}`, () => {
        expect(apiErrorUserId(withBody(c.body))).toBeNull();
      });
    });
  });
});
