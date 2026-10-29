import { Routes } from '@angular/router';
import { PublicLayout } from './layouts/public/public-layout';
import { authGuard } from './core/guards/auth-guard';
import { roleGuard } from './core/guards/role-guard';

export const routes: Routes = [
  {
    path: '',
    component: PublicLayout,
    children: [
      {
        path: '',
        title: 'DriveConnect — La mobilité, ensemble',
        loadComponent: () => import('./features/accueil/home-page').then((m) => m.HomePage),
      },
      {
        path: 'connexion',
        title: 'Connexion · DriveConnect',
        loadComponent: () => import('./features/connexion/login-page').then((m) => m.LoginPage),
      },
      {
        path: 'activation',
        title: 'Activer mon compte · DriveConnect',
        loadComponent: () =>
          import('./features/activation/activation-page').then((m) => m.ActivationPage),
      },
      {
        path: 'acces-refuse',
        title: 'Accès refusé · DriveConnect',
        data: { kind: 'forbidden' },
        loadComponent: () => import('./features/erreurs/status-page').then((m) => m.StatusPage),
      },
      {
        path: 'connexion-api',
        title: 'Connexion au service · DriveConnect',
        data: { kind: 'unavailable' },
        loadComponent: () => import('./features/erreurs/status-page').then((m) => m.StatusPage),
      },
    ],
  },
  {
    path: 'tableau-de-bord',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT', 'ADMIN'] },
    loadComponent: () => import('./layouts/prive/private-layout').then((m) => m.PrivateLayout),
    children: [
      {
        path: '',
        title: 'Mon espace · DriveConnect',
        loadComponent: () =>
          import('./features/tableau-de-bord/dashboard-page').then((m) => m.DashboardPage),
      },
    ],
  },
  {
    path: '**',
    component: PublicLayout,
    children: [
      {
        path: '',
        title: 'Page introuvable · DriveConnect',
        data: { kind: '404' },
        loadComponent: () => import('./features/erreurs/status-page').then((m) => m.StatusPage),
      },
    ],
  },
];
