import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SiteHeader } from '../../shared/components/site-header';
import { Icon } from '../../shared/components/icon';
import { AuthService } from '../../core/auth/auth-service';
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, SiteHeader, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-site-header />
    <div class="workspace">
      <aside class="workspace-nav" aria-label="Navigation de votre espace">
        <p class="eyebrow">MON ESPACE</p>
        <a routerLink="/tableau-de-bord" routerLinkActive="current" ariaCurrentWhenActive="page"
          ><app-icon name="home" />Vue d’ensemble</a
        >
        @if (auth.user()?.role === 'CLIENT') {
          <a routerLink="/trajets" routerLinkActive="current" ariaCurrentWhenActive="page"
            ><app-icon name="search" />Rechercher un trajet</a
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
