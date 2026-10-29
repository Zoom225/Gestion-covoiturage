import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Brand } from './brand';
@Component({
  selector: 'app-site-footer',
  imports: [Brand, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<footer class="site-footer">
    <div class="container footer-inner">
      <div>
        <app-brand />
        <p>Un réseau de covoiturage.<br />Des déplacements mieux organisés.</p>
      </div>
      <nav aria-label="Navigation de pied de page">
        <a routerLink="/">Accueil</a><a routerLink="/connexion">Connexion</a>
      </nav>
      <p class="footer-note">DriveConnect<br />La mobilité, ensemble.</p>
    </div>
  </footer>`,
})
export class SiteFooter {}
