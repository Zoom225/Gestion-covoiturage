import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { authGuard } from './auth-guard';
import { roleGuard } from './role-guard';
import { AuthService } from '../auth/auth-service';
import { signal } from '@angular/core';

describe('Guards de navigation', () => {
  let signedIn: boolean;
  beforeEach(() => {
    signedIn = false;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { hasSession: () => signedIn, user: signal({ role: 'CLIENT' }) },
        },
      ],
    });
  });
  const route = new ActivatedRouteSnapshot();
  it('redirige un visiteur et conserve sa destination', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard(route, { url: '/tableau-de-bord' } as RouterStateSnapshot),
    ) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toContain(
      '/connexion?retour=%2Ftableau-de-bord',
    );
  });
  it('accepte une session active', () => {
    signedIn = true;
    expect(TestBed.runInInjectionContext(() => authGuard(route, {} as RouterStateSnapshot))).toBe(
      true,
    );
  });
  it('refuse un rôle différent du rôle demandé', () => {
    signedIn = true;
    const protectedRoute = new ActivatedRouteSnapshot();
    protectedRoute.data = { roles: ['ADMIN'] };
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(protectedRoute, {} as RouterStateSnapshot),
    ) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/acces-refuse');
  });
  it('accepte le rôle CLIENT prévu sur le tableau de bord', () => {
    signedIn = true;
    const protectedRoute = new ActivatedRouteSnapshot();
    protectedRoute.data = { roles: ['CLIENT', 'ADMIN'] };
    expect(
      TestBed.runInInjectionContext(() => roleGuard(protectedRoute, {} as RouterStateSnapshot)),
    ).toBe(true);
  });
});
