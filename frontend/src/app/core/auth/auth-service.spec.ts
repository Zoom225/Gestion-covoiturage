import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth-service';
import { AuthApi } from './auth-api';
import { authInterceptor } from '../interceptors/auth-interceptor';
import { API_URL } from '../services/api-url';
import { ApiFailure } from '../services/api-error';
import { ProfileResponse } from '../../shared/models/auth.models';
import { vi } from 'vitest';

const base = 'http://localhost:8081/api';
const profile: ProfileResponse = {
  compteId: 1,
  identifiant: 'L001',
  role: 'CLIENT',
  clientId: 2,
  categorie: 'LOCAL',
  zoneClientId: 3,
  perimetreAdmin: null,
  zoneAdminId: null,
};
describe('Session et interceptor réels, transport HTTP contrôlé', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: base },
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => {
    auth.logout();
    http.verify();
    vi.useRealTimers();
  });
  function login() {
    auth.login({ identifiant: 'L001', password: 'mot-de-passe-de-test' }).subscribe();
    const request = http.expectOne(`${base}/auth/login`);
    expect(request.request.body).toEqual({ identifiant: 'L001', password: 'mot-de-passe-de-test' });
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ accessToken: 'jeton-factice-de-test', tokenType: 'Bearer', expiresIn: 900 });
    const me = http.expectOne(`${base}/auth/me`);
    expect(me.request.headers.get('Authorization')).toBe('Bearer jeton-factice-de-test');
    me.flush(profile);
  }
  it('charge le profil vérifié avant de déclarer la connexion réussie', () => {
    login();
    expect(auth.user()).toEqual(profile);
    expect(auth.hasSession()).toBe(true);
  });
  it('ne persiste ni le JWT ni le mot de passe', () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    login();
    expect(local).not.toHaveBeenCalled();
    local.mockRestore();
  });
  it('refuse une connexion sans exposer le message brut du serveur', () => {
    let failure: ApiFailure | undefined;
    auth
      .login({ identifiant: 'L001', password: 'incorrect' })
      .subscribe({ error: (e: ApiFailure) => (failure = e) });
    http
      .expectOne(`${base}/auth/login`)
      .flush(
        { code: 'UNAUTHORIZED', message: 'exception SQL confidentielle' },
        { status: 401, statusText: 'Unauthorized' },
      );
    expect(failure?.message).toContain('Identifiants incorrects');
    expect(failure?.message).not.toContain('SQL');
    expect(auth.hasSession()).toBe(false);
    http.expectNone(`${base}/auth/me`);
  });
  it('efface le jeton si le chargement du profil échoue', () => {
    const failed = vi.fn();
    auth.login({ identifiant: 'L001', password: 'valide' }).subscribe({ error: failed });
    http
      .expectOne(`${base}/auth/login`)
      .flush({ accessToken: 'test', tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${base}/auth/me`).flush({}, { status: 403, statusText: 'Forbidden' });
    expect(failed).toHaveBeenCalledOnce();
    expect(auth.accessToken()).toBeNull();
    expect(auth.user()).toBeNull();
  });
  it('ignore un jeton de réponse invalide', () => {
    const failed = vi.fn();
    auth.login({ identifiant: 'L001', password: 'valide' }).subscribe({ error: failed });
    http
      .expectOne(`${base}/auth/login`)
      .flush({ accessToken: '', tokenType: 'Bearer', expiresIn: -1 });
    expect(failed).toHaveBeenCalled();
    http.expectNone(`${base}/auth/me`);
    expect(auth.hasSession()).toBe(false);
  });
  it('ne transmet jamais le JWT à un autre domaine ou un chemin ressemblant', () => {
    login();
    const client = TestBed.inject(HttpClient);
    for (const url of [
      'https://tiers.example/api/data',
      'http://localhost:8081/api-piege/data',
      'http://localhost:8081.evil.test/api/data',
    ]) {
      client.get(url).subscribe();
      const request = http.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({});
    }
  });
  it('n’ajoute pas de JWT à l’activation et conserve la session après son erreur 400', () => {
    login();
    const failed = vi.fn();
    TestBed.inject(AuthApi)
      .activate({ jeton: 'a'.repeat(43), password: 'mot-de-passe-de-test' })
      .subscribe({ error: failed });
    const request = http.expectOne(`${base}/auth/invitations/accept`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({}, { status: 400, statusText: 'Bad Request' });
    expect(auth.hasSession()).toBe(true);
    expect(failed).toHaveBeenCalled();
  });
  it('active uniquement avec le corps exact attendu et sans connexion automatique', () => {
    const complete = vi.fn();
    TestBed.inject(AuthApi)
      .activate({ jeton: 'a'.repeat(43), password: 'mot-de-passe-de-test' })
      .subscribe(complete);
    const request = http.expectOne(`${base}/auth/invitations/accept`);
    expect(request.request.method).toBe('POST');
    expect(Object.keys(request.request.body).sort()).toEqual(['jeton', 'password']);
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(complete).toHaveBeenCalled();
    expect(auth.hasSession()).toBe(false);
  });
  it('efface la session et redirige lors d’un 401 protégé', () => {
    login();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    TestBed.inject(HttpClient)
      .get(`${base}/zones`)
      .subscribe({ error: () => undefined });
    http.expectOne(`${base}/zones`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.hasSession()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/connexion'], {
      queryParams: { raison: 'session-expiree' },
    });
  });
  it('expire automatiquement selon la durée fournie par le serveur', () => {
    vi.useFakeTimers();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    login();
    vi.advanceTimersByTime(900_000);
    expect(auth.user()).toBeNull();
    expect(auth.accessToken()).toBeNull();
  });
  it('déconnecte et annule un chargement de profil encore en cours', () => {
    const next = vi.fn();
    auth.login({ identifiant: 'L001', password: 'valide' }).subscribe(next);
    http
      .expectOne(`${base}/auth/login`)
      .flush({ accessToken: 'test', tokenType: 'Bearer', expiresIn: 900 });
    const me = http.expectOne(`${base}/auth/me`);
    auth.logout();
    expect(me.cancelled).toBe(true);
    expect(next).not.toHaveBeenCalled();
    expect(auth.user()).toBeNull();
  });
  it('une nouvelle instance démarre sans session persistée', () => {
    expect(auth.user()).toBeNull();
    expect(auth.accessToken()).toBeNull();
  });
  it('un ancien 401 ne détruit pas une nouvelle session', () => {
    login();
    TestBed.inject(HttpClient)
      .get(`${base}/zones`)
      .subscribe({ error: () => undefined });
    const old = http.expectOne(`${base}/zones`);
    auth.login({ identifiant: 'L001', password: 'valide' }).subscribe();
    http
      .expectOne(`${base}/auth/login`)
      .flush({ accessToken: 'nouveau-jeton-de-test', tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${base}/auth/me`).flush(profile);
    old.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.accessToken()).toBe('nouveau-jeton-de-test');
    expect(auth.hasSession()).toBe(true);
  });
  it('n’affiche pas un profil arrivé après expiration du jeton', () => {
    vi.useFakeTimers();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const failed = vi.fn();
    auth.login({ identifiant: 'L001', password: 'valide' }).subscribe({ error: failed });
    http
      .expectOne(`${base}/auth/login`)
      .flush({ accessToken: 'test', tokenType: 'Bearer', expiresIn: 1 });
    const me = http.expectOne(`${base}/auth/me`);
    vi.advanceTimersByTime(1000);
    me.flush(profile);
    expect(auth.user()).toBeNull();
    expect(failed).toHaveBeenCalledOnce();
  });
});
