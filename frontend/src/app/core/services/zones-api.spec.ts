import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ZonesApi } from './zones-api';
import { ZoneResponse } from '../../shared/models/zone.model';

describe('Zones autorisées', () => {
  let api: ZonesApi;
  let http: HttpTestingController;
  const zone: ZoneResponse = {
    zoneId: 1,
    nom: 'Bruxelles',
    actif: true,
    fuseauHoraire: 'Europe/Brussels',
    ouverteOccasionnels: true,
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ZonesApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('charge toutes les pages, sans limiter le sélecteur aux cent premières zones', () => {
    let zones: ZoneResponse[] = [];
    api.all().subscribe((value) => (zones = value));
    const first = http.expectOne('http://localhost:8081/api/zones?page=0&size=100');
    expect(first.request.method).toBe('GET');
    first.flush({ content: [zone], page: 0, size: 100, totalElements: 2, totalPages: 2 });
    expect(zones).toEqual([]);
    http
      .expectOne('http://localhost:8081/api/zones?page=1&size=100')
      .flush({
        content: [{ ...zone, zoneId: 2 }],
        page: 1,
        size: 100,
        totalElements: 2,
        totalPages: 2,
      });
    expect(zones.map((z) => z.zoneId)).toEqual([1, 2]);
  });
  it('retourne un état vide réel sans zone ajoutée artificiellement', () => {
    let zones: ZoneResponse[] | undefined;
    api.all().subscribe((value) => (zones = value));
    http
      .expectOne('http://localhost:8081/api/zones?page=0&size=100')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    expect(zones).toEqual([]);
  });
  it('ne publie pas une liste partielle si une page échoue', () => {
    const values: ZoneResponse[][] = [];
    let failure = false;
    api.all().subscribe({ next: (value) => values.push(value), error: () => (failure = true) });
    http
      .expectOne('http://localhost:8081/api/zones?page=0&size=100')
      .flush({ content: [zone], page: 0, size: 100, totalElements: 2, totalPages: 2 });
    http
      .expectOne('http://localhost:8081/api/zones?page=1&size=100')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(failure).toBe(true);
    expect(values).toEqual([]);
  });
});
