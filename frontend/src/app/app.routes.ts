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
        loadComponent: () => import('./features/accueil/pages/home.page').then((m) => m.HomePage),
      },
      {
        path: 'connexion',
        title: 'Connexion · DriveConnect',
        loadComponent: () =>
          import('./features/connexion/pages/login.page').then((m) => m.LoginPage),
      },
      {
        path: 'activation',
        title: 'Activer mon compte · DriveConnect',
        loadComponent: () =>
          import('./features/activation/pages/activation.page').then((m) => m.ActivationPage),
      },
      {
        path: 'acces-refuse',
        title: 'Accès refusé · DriveConnect',
        data: { kind: 'forbidden' },
        loadComponent: () =>
          import('./features/erreurs/pages/status.page').then((m) => m.StatusPage),
      },
      {
        path: 'connexion-api',
        title: 'Connexion au service · DriveConnect',
        data: { kind: 'unavailable' },
        loadComponent: () =>
          import('./features/erreurs/pages/status.page').then((m) => m.StatusPage),
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
          import('./features/tableau-de-bord/pages/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'trajets',
        canActivate: [roleGuard],
        data: { roles: ['CLIENT'] },
        title: 'Trajets · DriveConnect',
        loadComponent: () =>
          import('./features/trajets/pages/trajets.page').then((m) => m.TrajetsPage),
      },
      {
        path: 'trajets/nouveau',
        canActivate: [roleGuard],
        data: { roles: ['CLIENT'] },
        title: 'Créer un trajet · DriveConnect',
        loadComponent: () =>
          import('./features/trajets/pages/trajet-form.page').then((m) => m.TrajetFormPage),
      },
      {
        path: 'trajets/:id/modifier',
        canActivate: [roleGuard],
        data: { roles: ['CLIENT'] },
        title: 'Modifier mon trajet · DriveConnect',
        loadComponent: () =>
          import('./features/trajets/pages/trajet-form.page').then((m) => m.TrajetFormPage),
      },
      {
        path: 'trajets/:id',
        canActivate: [roleGuard],
        data: { roles: ['CLIENT'] },
        title: 'Détail du trajet · DriveConnect',
        loadComponent: () =>
          import('./features/trajets/pages/trajet-detail.page').then((m) => m.TrajetDetailPage),
      },
      {
        path: 'reservations',
        canActivate: [roleGuard],
        data: { roles: ['CLIENT'] },
        title: 'Mes réservations · DriveConnect',
        loadComponent: () =>
          import('./features/reservations/pages/reservations.page').then((m) => m.ReservationsPage),
      },
      {
        path: 'paiements',
        canActivate: [roleGuard],
        data: { roles: ['CLIENT'] },
        title: 'Paiements et dettes · DriveConnect',
        loadComponent: () =>
          import('./features/paiements/pages/paiements.page').then((m) => m.PaiementsPage),
      },
      {
        path: 'administration',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        title: 'Administration · DriveConnect',
        loadComponent: () =>
          import('./features/administration/pages/administration.page').then(
            (m) => m.AdministrationPage,
          ),
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
        loadComponent: () =>
          import('./features/erreurs/pages/status.page').then((m) => m.StatusPage),
      },
    ],
  },
];
