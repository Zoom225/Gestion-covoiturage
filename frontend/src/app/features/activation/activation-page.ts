import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Location } from '@angular/common';
import { finalize } from 'rxjs';
import { AuthApi } from '../../core/auth/auth-api';
import {
  invitationPassword,
  notBlank,
  passwordBytes,
  passwordsMatch,
} from '../../core/auth/auth-validators';
import { ApiFailure } from '../../core/services/api-error';
import { Notice } from '../../shared/components/notice';
import { PasswordField } from '../../shared/components/password-field';
import { SubmitButton } from '../../shared/components/submit-button';
import { Icon } from '../../shared/components/icon';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Notice, PasswordField, SubmitButton, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './activation-page.html',
})
export class ActivationPage {
  private readonly api = inject(AuthApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly loading = signal(false);
  protected readonly success = signal(false);
  protected readonly error = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      jeton: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]{43}$/)]],
      password: ['', [notBlank, invitationPassword, passwordBytes]],
      confirmation: ['', [notBlank]],
    },
    { validators: passwordsMatch },
  );

  constructor() {
    // Le courrier Spring Boot utilise #invitation=... ; retirer la preuve de l'URL courante.
    const route = inject(ActivatedRoute);
    const fragment = route.snapshot.fragment ?? '';
    const token = new URLSearchParams(fragment).get('invitation');
    if (token) this.form.controls.jeton.setValue(token);
    if (fragment) inject(Location).replaceState('/activation');
  }

  protected submit(): void {
    if (this.loading()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error.set('');
    this.loading.set(true);
    const { jeton, password } = this.form.getRawValue();
    this.api
      .activate({ jeton, password })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: () => {
          this.form.reset();
          this.success.set(true);
        },
        error: (error: unknown) => {
          this.error.set(
            error instanceof ApiFailure
              ? error.message
              : 'L’activation n’a pas pu aboutir. Réessayez.',
          );
          this.form.controls.password.reset();
          this.form.controls.confirmation.reset();
        },
      });
  }
}
