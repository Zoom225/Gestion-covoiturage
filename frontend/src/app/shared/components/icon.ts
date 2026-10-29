import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName =
  | 'arrow'
  | 'route'
  | 'search'
  | 'ticket'
  | 'calendar'
  | 'wallet'
  | 'shield'
  | 'check'
  | 'user'
  | 'logout'
  | 'eye'
  | 'eye-off'
  | 'mail'
  | 'chevron'
  | 'alert'
  | 'home';
const paths: Record<IconName, string> = {
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  route: 'M6 6h9a4 4 0 0 1 0 8H9a4 4 0 0 0 0 8m-3-19a3 3 0 1 0 0 6 3 3 0 0 0 0-6M18 18l3 3-3 3',
  search: 'm21 21-4.5-4.5M19 10.5a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0',
  ticket: 'M3 5h18v5a2 2 0 0 0 0 4v5H3v-5a2 2 0 0 0 0-4V5m12 0v3m0 3v2m0 3v3',
  calendar: 'M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2m2 10h3m4 0h3',
  wallet: 'M20 7V4H5a3 3 0 0 0 0 6h16v10H5a3 3 0 0 1-3-3V7m19 6h-5v4h5',
  shield: 'm12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6l9-4m-4 10 3 3 5-6',
  check: 'm5 12 4 4L19 6',
  user: 'M20 21v-2a7 7 0 0 0-14 0v2m11-14a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  logout: 'M9 4H3v16h6m5-13 5 5-5 5m-7-5h12',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12m13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  'eye-off':
    'm3 3 18 18M10.5 5.1C17 4 22 12 22 12a20 20 0 0 1-4 4M6 6a20 20 0 0 0-4 6s4 7 10 7c1.5 0 3-.4 4.3-1.1',
  mail: 'M3 5h18v14H3V5m0 0 9 8 9-8',
  chevron: 'm9 5 7 7-7 7',
  alert: 'm12 3 10 18H2L12 3m0 6v5m0 3v1',
  home: 'm3 10 9-8 9 8v11h-7v-7h-4v7H3V10',
};
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', '[style.display]': '"inline-flex"' },
  template:
    '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" focusable="false"><path [attr.d]="paths[name()]" /></svg>',
})
export class Icon {
  readonly name = input.required<IconName>();
  protected readonly paths = paths;
}
