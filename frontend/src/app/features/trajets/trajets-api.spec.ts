import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { convertToParamMap } from '@angular/router';
import { TrajetsApi } from './services/trajets-api';
import { TripDatePipe } from './components/trip-date.pipe';
import { positiveId, readTripCriteria } from './models/trip-criteria';
import { safeReturnUrl } from '../../core/guards/safe-return-url';

describe('Contrat et présentation des trajets', () => {
  let api: TrajetsApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TrajetsApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('transmet uniquement zoneId, page et size au serveur', () => {
    api.search({ zoneId: 7, page: 2 }).subscribe();
    const request = http.expectOne('http://localhost:8081/api/trajets?page=2&size=12&zoneId=7');
    expect(request.request.method).toBe('GET');
    expect(request.request.body).toBeNull();
    request.flush({ content: [], page: 2, size: 12, totalElements: 0, totalPages: 0 });
  });
  it('omet zoneId pour toutes les zones', () => {
    api.search({ zoneId: null, page: 0 }).subscribe();
    http
      .expectOne('http://localhost:8081/api/trajets?page=0&size=12')
      .flush({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
  });
  it('consulte le détail réel, sans autre endpoint inventé', () => {
    api.detail(23).subscribe();
    const request = http.expectOne('http://localhost:8081/api/trajets/23');
    expect(request.request.method).toBe('GET');
    request.flush({ trajetId: 23 });
  });
  it('lit les critères et conserve une pagination indexée à zéro', () => {
    expect(readTripCriteria(convertToParamMap({ zoneId: '7', page: '2' }))).toEqual({
      zoneId: 7,
      page: 2,
    });
    expect(readTripCriteria(convertToParamMap({}))).toEqual({ zoneId: null, page: 0 });
  });
  it.each([
    { zoneId: '0' },
    { zoneId: 'NaN' },
    { zoneId: '-1' },
    { zoneId: '9007199254740993' },
    { page: '-1' },
    { page: '1.2' },
    { page: '9999999999' },
  ])('refuse des critères invalides : %s', (params) => {
    expect(readTripCriteria(convertToParamMap(params))).toBeNull();
  });
  it('rejette un identifiant de trajet non numérique', () => {
    expect(positiveId('../admin')).toBeNull();
  });
  it('formate l’horaire du trajet plutôt que celui du navigateur', () => {
    const pipe = new TripDatePipe();
    expect(pipe.transform('2026-11-03T09:00:00Z', 'Europe/Brussels', 'time')).toBe('10:00');
    expect(pipe.transform('2026-07-03T09:00:00Z', 'Europe/Brussels', 'time')).toBe('11:00');
  });
  it('garde la bonne date lorsqu’une arrivée est le lendemain', () => {
    expect(
      new TripDatePipe().transform('2026-11-03T23:30:00Z', 'Europe/Brussels', 'date'),
    ).toContain('4 novembre 2026');
  });
  it('ne fait pas planter la page sur un fuseau invalide', () => {
    expect(new TripDatePipe().transform('2026-11-03T09:00:00Z', 'Invalide')).toBe(
      'Horaire non disponible',
    );
  });
  it('préserve un retour autorisé vers la recherche ou son détail, sans redirection externe', () => {
    expect(safeReturnUrl('/trajets?zoneId=7&page=2')).toBe('/trajets?zoneId=7&page=2');
    expect(safeReturnUrl('/trajets/23?zoneId=7')).toBe('/trajets/23?zoneId=7');
    expect(safeReturnUrl('//evil.test/trajets')).toBe('/tableau-de-bord');
  });
});
