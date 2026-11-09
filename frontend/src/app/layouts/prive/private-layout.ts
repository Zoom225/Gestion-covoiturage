import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth-service';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SiteHeader } from '../../shared/components/site-header';
import { Icon } from '../../shared/components/icon';
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, SiteHeader, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-site-header />
    <div class="workspace">
      <aside class="workspace-nav" aria-label="Navigation de votre espace">
        <p class="eyebrow">MON ESPACE</p>
        <a
          routerLink="/tableau-de-bord"
          routerLinkActive="current"
          [routerLinkActiveOptions]="{ exact: true }"
          ariaCurrentWhenActive="page"
          ><app-icon name="home" />Vue d’ensemble</a
        >
        @if (auth.user()?.role === 'CLIENT') {
          <a
            routerLink="/tableau-de-bord/trajets"
            routerLinkActive="current"
            ariaCurrentWhenActive="page"
            >Trajets</a
          >
          <a
            routerLink="/tableau-de-bord/reservations"
            routerLinkActive="current"
            ariaCurrentWhenActive="page"
            >Mes réservations</a
          >
          <a
            routerLink="/tableau-de-bord/paiements"
            routerLinkActive="current"
            ariaCurrentWhenActive="page"
            >Paiements et dettes</a
          >
        } @else if (auth.user()?.role === 'ADMIN') {
          <a
            routerLink="/tableau-de-bord/administration"
            routerLinkActive="current"
            ariaCurrentWhenActive="page"
            >Administration</a
          >
        }
        <div class="workspace-help">
          <app-icon name="shield" />
          <p>Votre espace personnel.<br />Vos informations, en toute simplicité.</p>
        </div>
      </aside>
      <main id="contenu" tabindex="-1" class="workspace-main"><router-outlet /></main>
    </div>`,
})
export class PrivateLayout {
  protected readonly auth = inject(AuthService);
}
