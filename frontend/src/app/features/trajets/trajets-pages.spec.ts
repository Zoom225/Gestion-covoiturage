import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth/auth-service';
import { authInterceptor } from '../../core/interceptors/auth-interceptor';
import { TrajetResponse } from './models/trajet.model';
import { Role } from '../../shared/models/auth.models';
import { vi } from 'vitest';

const base = 'http://localhost:8081/api';
const zone = {
  zoneId: 7,
  nom: 'Bruxelles',
  actif: true,
  fuseauHoraire: 'Europe/Brussels',
  ouverteOccasionnels: true,
};
const trip: TrajetResponse = {
  trajetId: 23,
  zoneId: 7,
  vehiculeId: 5,
  organisateurId: 99,
  depart: '2099-11-03T09:00:00Z',
  arrivee: '2099-11-03T10:00:00Z',
  fuseauHoraire: 'Europe/Brussels',
  type: 'PUBLIC',
  statut: 'PLANIFIE',
  origine: 'Bruxelles-Midi',
  destination: 'Namur',
  traiteJ1Le: null,
  verificationJ1Requise: false,
};
const zoneUrl = `${base}/zones?page=0&size=100`;
describe('Recherche et détail des trajets', () => {
  let http: HttpTestingController;
  let role: Role;
  let signedIn: boolean;
  beforeEach(() => {
    role = 'CLIENT';
    signedIn = true;
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            hasSession: () => signedIn,
            authenticated: () => signedIn,
            user: () => ({ role }),
            accessToken: () => 'jeton-unitaire',
            expire: () => (signedIn = false),
            logout: () => (signedIn = false),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify({ ignoreCancelled: true }));
  function zones(content = [zone]) {
    http.expectOne(zoneUrl).flush({
      content,
      page: 0,
      size: 100,
      totalElements: content.length,
      totalPages: content.length ? 1 : 0,
    });
  }
  function result(
    content = [trip],
    page = 0,
    totalElements = content.length,
    totalPages = content.length ? 1 : 0,
  ) {
    http
      .expectOne(`${base}/trajets?page=${page}&size=12`)
      .flush({ content, page, size: 12, totalElements, totalPages });
  }
  function view(harness: RouterTestingHarness) {
    harness.detectChanges();
    return harness.routeNativeElement!;
  }
  it('affiche un chargement, puis les résultats réellement reçus avec JWT', async () => {
    const h = await RouterTestingHarness.create('/trajets');
    expect(view(h).textContent).toContain('Chargement de vos zones');
    zones();
    const request = http.expectOne(`${base}/trajets?page=0&size=12`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer jeton-unitaire');
    request.flush({ content: [trip], page: 0, size: 12, totalElements: 1, totalPages: 1 });
    expect(view(h).textContent).toContain('Bruxelles-Midi');
    expect(view(h).textContent).toContain('Places restantes non communiquées');
  });
  it('ne recherche aucun trajet sans zone autorisée', async () => {
    const h = await RouterTestingHarness.create('/trajets');
    zones([]);
    expect(view(h).textContent).toContain('Aucune zone disponible');
    http.expectNone((request) => request.url.endsWith('/trajets'));
  });
  it('permet de réessayer les zones après une panne, sans fausse liste', async () => {
    const h = await RouterTestingHarness.create('/trajets');
    http.expectOne(zoneUrl).flush({}, { status: 503, statusText: 'Unavailable' });
    expect(view(h).textContent).toContain('momentanément indisponible');
    view(h).querySelector<HTMLButtonElement>('.trip-search-panel button')!.click();
    zones();
    result([]);
    expect(view(h).textContent).toContain('Aucun trajet ne correspond');
  });
  it('affiche un état vide fidèle au serveur', async () => {
    const h = await RouterTestingHarness.create('/trajets');
    zones();
    result([]);
    expect(view(h).textContent).toContain('Aucun trajet ne correspond à votre recherche');
  });
  it('efface les résultats précédents et annule la requête lors d’un changement de zone', async () => {
    const h = await RouterTestingHarness.create('/trajets');
    zones();
    const old = http.expectOne(`${base}/trajets?page=0&size=12`);
    await TestBed.inject(Router).navigateByUrl('/trajets?zoneId=7&page=0');
    expect(old.cancelled).toBe(true);
    http
      .expectOne(`${base}/trajets?page=0&size=12&zoneId=7`)
      .flush({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
    expect(view(h).textContent).toContain('Aucun trajet');
  });
  it('refuse un critère invalide sans élargir silencieusement la recherche', async () => {
    const h = await RouterTestingHarness.create('/trajets?zoneId=abc');
    zones();
    expect(view(h).textContent).toContain('critères de recherche sont invalides');
    http.expectNone((request) => request.url.endsWith('/trajets'));
  });
  it('ne cherche pas une zone absente des zones autorisées', async () => {
    const h = await RouterTestingHarness.create('/trajets?zoneId=99');
    zones();
    expect(view(h).textContent).toContain('zone n’est pas disponible');
    http.expectNone((request) => request.url.endsWith('/trajets'));
  });
  it('redemande la même recherche après erreur lorsque le formulaire est renvoyé', async () => {
    const h = await RouterTestingHarness.create('/trajets');
    zones();
    http
      .expectOne(`${base}/trajets?page=0&size=12`)
      .flush({}, { status: 503, statusText: 'Unavailable' });
    view(h)
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    result();
    expect(view(h).textContent).toContain('Bruxelles-Midi');
  });
  it('présente les lieux historiques absents sans fabriquer d’itinéraire', async () => {
    const h = await RouterTestingHarness.create('/trajets');
    zones();
    result([{ ...trip, origine: null, destination: null }]);
    expect(view(h).textContent).toContain('Départ non renseigné');
    expect(view(h).textContent).toContain('Destination non renseignée');
  });
  it('conserve la pagination serveur et les liens de retour', async () => {
    const h = await RouterTestingHarness.create('/trajets?page=1');
    zones();
    result([trip], 1, 13, 2);
    const el = view(h);
    expect(el.textContent).toContain('Page 2 sur 2');
    expect(el.querySelector('.trip-card-footer a')?.getAttribute('href')).toContain('page=1');
  });
  it('affiche le détail sans donnée personnelle de l’organisateur ni réservation active', async () => {
    const h = await RouterTestingHarness.create('/trajets/23?zoneId=7&page=1');
    zones();
    http.expectOne(`${base}/trajets/23`).flush(trip);
    const el = view(h);
    expect(el.textContent).toContain('Véhicule n° 5');
    expect(el.textContent).toContain('Réservation bientôt disponible');
    expect(el.querySelector('.trip-back')?.getAttribute('href')).toContain(
      '/trajets?zoneId=7&page=1',
    );
    expect(el.querySelector('button[type=submit]')).toBeNull();
    expect(el.textContent).not.toContain('organisateurId');
  });
  it.each(['ANNULE', 'REALISE', 'COMPLET'] as const)(
    'explique le statut %s du trajet',
    async (statut) => {
      const h = await RouterTestingHarness.create('/trajets/23');
      zones();
      http.expectOne(`${base}/trajets/23`).flush({ ...trip, statut });
      const text = view(h).textContent;
      expect(text).toContain(
        statut === 'ANNULE' ? 'a été annulé' : statut === 'REALISE' ? 'est terminé' : 'est complet',
      );
    },
  );
  it('explique un départ passé sans calculer de disponibilité', async () => {
    const h = await RouterTestingHarness.create('/trajets/23');
    zones();
    http.expectOne(`${base}/trajets/23`).flush({ ...trip, depart: '2020-01-01T00:00:00Z' });
    expect(view(h).textContent).toContain('départ de ce trajet a déjà eu lieu');
  });
  it('masque un trajet absent ou invisible avec le même message', async () => {
    const h = await RouterTestingHarness.create('/trajets/23');
    http.expectOne(`${base}/trajets/23`).flush({}, { status: 404, statusText: 'Not Found' });
    expect(view(h).textContent).toContain('introuvable ou n’est plus accessible');
  });
  it('rejette un identifiant de détail invalide avant tout appel HTTP', async () => {
    const h = await RouterTestingHarness.create('/trajets/invalide');
    expect(view(h).textContent).toContain('introuvable');
    http.expectNone((request) => request.url.startsWith(base));
  });
  it('garde le détail autorisé si le libellé de zone est momentanément indisponible', async () => {
    const h = await RouterTestingHarness.create('/trajets/23');
    http.expectOne(zoneUrl).flush({}, { status: 503, statusText: 'Unavailable' });
    http.expectOne(`${base}/trajets/23`).flush(trip);
    expect(view(h).textContent).toContain('Zone n° 7');
    expect(view(h).textContent).toContain('Bruxelles-Midi');
  });
  it('réserve les trajets au rôle CLIENT', async () => {
    role = 'ADMIN';
    const h = await RouterTestingHarness.create('/trajets');
    expect(view(h).textContent).toContain('Vous n’avez pas accès');
    http.expectNone((request) => request.url.startsWith(base));
  });
  it('ramène une page disparue vers la dernière page encore disponible', async () => {
    const h = await RouterTestingHarness.create('/trajets?page=8');
    zones();
    result([], 8, 1, 1);
    const corrected = await vi.waitFor(() => http.expectOne(`${base}/trajets?page=0&size=12`));
    corrected.flush({ content: [trip], page: 0, size: 12, totalElements: 1, totalPages: 1 });
    expect(TestBed.inject(Router).url).toContain('page=0');
    expect(view(h).textContent).toContain('Bruxelles-Midi');
  });
  it('demande une connexion en conservant le détail demandé', async () => {
    signedIn = false;
    const h = await RouterTestingHarness.create('/trajets/23?zoneId=7');
    expect(view(h).textContent).toContain('Connexion');
    expect(TestBed.inject(Router).url).toContain('retour=');
    http.expectNone((request) => request.url.startsWith(base));
  });
});
