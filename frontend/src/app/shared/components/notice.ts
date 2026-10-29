import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon } from './icon';
@Component({
  selector: 'app-notice',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div
    class="notice"
    [class.notice-error]="tone() === 'error'"
    [class.notice-success]="tone() === 'success'"
    [attr.role]="tone() === 'error' ? 'alert' : 'status'"
  >
    <app-icon [name]="tone() === 'success' ? 'check' : tone() === 'error' ? 'alert' : 'shield'" />
    <p>{{ message() }}</p>
  </div>`,
})
export class Notice {
  readonly message = input.required<string>();
  readonly tone = input<'error' | 'info' | 'success'>('info');
}
