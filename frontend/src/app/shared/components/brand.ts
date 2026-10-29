import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-brand',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<a routerLink="/" class="brand" aria-label="DriveConnect, accueil"
    ><span class="brand-symbol" aria-hidden="true"
      ><svg viewBox="0 0 32 32" fill="none">
        <path d="M8 7h8a9 9 0 0 1 0 18H8V7Z" stroke="currentColor" stroke-width="3.5" />
        <path
          d="m13 17 4-4m-4 4h6"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
        /></svg></span
    ><span>Drive<span class="brand-connect">Connect</span></span></a
  >`,
})
export class Brand {}
