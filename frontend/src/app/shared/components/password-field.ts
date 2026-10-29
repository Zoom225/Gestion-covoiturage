import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Icon } from './icon';
@Component({
  selector: 'app-password-field',
  imports: [ReactiveFormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<label [for]="fieldId()">{{ label() }}</label>
    <div class="password-input">
      <input
        [id]="fieldId()"
        [type]="visible() ? 'text' : 'password'"
        [formControl]="control()"
        [autocomplete]="autocomplete()"
        [attr.aria-describedby]="describedBy()"
        [attr.aria-invalid]="invalid() || (control().touched && control().invalid) ? 'true' : null"
        required
        spellcheck="false"
      /><button
        type="button"
        (click)="visible.set(!visible())"
        [attr.aria-label]="(visible() ? 'Masquer' : 'Afficher') + ' le mot de passe'"
        [attr.aria-pressed]="visible()"
      >
        <app-icon [name]="visible() ? 'eye-off' : 'eye'" />
      </button>
    </div>`,
})
export class PasswordField {
  readonly control = input.required<FormControl<string>>();
  readonly fieldId = input.required<string>();
  readonly label = input('Mot de passe');
  readonly autocomplete = input('current-password');
  readonly describedBy = input<string>();
  readonly invalid = input(false);
  protected readonly visible = signal(false);
}
