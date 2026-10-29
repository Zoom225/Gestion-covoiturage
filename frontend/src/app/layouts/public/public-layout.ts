import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SiteHeader } from '../../shared/components/site-header';
import { SiteFooter } from '../../shared/components/site-footer';
@Component({
  imports: [RouterOutlet, SiteHeader, SiteFooter],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template:
    '<app-site-header/><main id="contenu" tabindex="-1"><router-outlet/></main><app-site-footer/>',
})
export class PublicLayout {}
