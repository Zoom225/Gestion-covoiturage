import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon } from './icon';
@Component({
  selector: 'app-submit-button',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button
    type="submit"
    class="btn btn-primary w-full"
    [disabled]="loading()"
    [attr.aria-busy]="loading()"
  >
    @if (loading()) {
      <span class="spinner" aria-hidden="true"></span>{{ busyLabel() }}
    } @else {
      {{ label() }}<app-icon name="arrow" />
    }
  </button>`,
})
export class SubmitButton {
  readonly label = input.required<string>();
  readonly busyLabel = input.required<string>();
  readonly loading = input(false);
}
