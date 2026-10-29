import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { Brand } from './brand';
import { Icon } from './icon';
@Component({
  selector: 'app-site-header',
  imports: [Brand, Icon, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<header class="site-header">
    <div class="container header-inner">
      <app-brand />
      <nav aria-label="Navigation principale">
        <a
          routerLink="/"
          routerLinkActive="nav-active"
          [routerLinkActiveOptions]="{ exact: true }"
          ariaCurrentWhenActive="page"
          class="nav-link"
          >Accueil</a
        >
        @if (auth.authenticated()) {
          <a
            routerLink="/tableau-de-bord"
            routerLinkActive="nav-active"
            ariaCurrentWhenActive="page"
            class="nav-link"
            >Mon espace</a
          ><button
            class="btn btn-quiet logout-button"
            aria-label="Se déconnecter"
            (click)="auth.logout()"
          >
            <app-icon name="logout" /><span>Déconnexion</span>
          </button>
        } @else {
          <a
            routerLink="/connexion"
            routerLinkActive="nav-active"
            ariaCurrentWhenActive="page"
            class="btn btn-small btn-primary"
            >Se connecter <app-icon name="arrow"
          /></a>
        }
      </nav>
    </div>
  </header>`,
})
export class SiteHeader {
  protected readonly auth = inject(AuthService);
}
