import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { routes } from '../app.routes';
import { authInterceptor } from '../core/interceptors/auth-interceptor';

describe('Pages et formulaires raccordés aux routes', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('affiche l’accueil français sans statistiques fictives', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(harness.routeNativeElement?.textContent).toContain('Moins de détours.');
  });
  it('affiche les erreurs de champs sans envoyer de requête', async () => {
    const harness = await RouterTestingHarness.create('/connexion');
    harness.routeNativeElement
      ?.querySelector('form')
      ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Saisissez votre identifiant');
    http.expectNone('http://localhost:8081/api/auth/login');
  });
  it('lit le fragment exact du courrier d’invitation', async () => {
    const harness = await RouterTestingHarness.create(`/activation#invitation=${'a'.repeat(43)}`);
    expect(harness.routeNativeElement?.querySelector<HTMLInputElement>('#invitation')?.value).toBe(
      'a'.repeat(43),
    );
  });
  it('redirige un accès direct au tableau de bord vers la connexion', async () => {
    const harness = await RouterTestingHarness.create('/tableau-de-bord');
    expect(harness.routeNativeElement?.textContent).toContain('Connexion');
  });
  it.each(['/route-inconnue', '/acces-refuse', '/connexion-api'])(
    'affiche une page technique française pour %s',
    async (path) => {
      const harness = await RouterTestingHarness.create(path);
      expect(harness.routeNativeElement?.querySelector('h1')).toBeTruthy();
      expect(harness.routeNativeElement?.querySelector('.btn-primary')).toBeTruthy();
    },
  );
});
