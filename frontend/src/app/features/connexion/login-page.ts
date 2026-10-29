import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { notBlank, passwordBytes } from '../../core/auth/auth-validators';
import { safeReturnUrl } from '../../core/guards/safe-return-url';
import { ApiFailure } from '../../core/services/api-error';
import { Notice } from '../../shared/components/notice';
import { PasswordField } from '../../shared/components/password-field';
import { SubmitButton } from '../../shared/components/submit-button';
import { Icon } from '../../shared/components/icon';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Notice, PasswordField, SubmitButton, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login-page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    identifiant: ['', [notBlank, Validators.maxLength(100)]],
    password: ['', [notBlank, Validators.maxLength(72), passwordBytes]],
  });
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly unavailable = signal(false);
  protected readonly expired =
    this.route.snapshot.queryParamMap.get('raison') === 'session-expiree';

  protected submit(): void {
    if (this.loading()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error.set('');
    this.unavailable.set(false);
    this.loading.set(true);
    const value = this.form.getRawValue();
    this.auth
      .login({ identifiant: value.identifiant.trim(), password: value.password })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: () => {
          this.form.controls.password.reset();
          void this.router.navigateByUrl(
            safeReturnUrl(this.route.snapshot.queryParamMap.get('retour')),
          );
        },
        error: (error: unknown) => {
          this.error.set(
            error instanceof ApiFailure
              ? error.message
              : 'La connexion n’a pas pu aboutir. Réessayez.',
          );
          this.unavailable.set(error instanceof ApiFailure && error.unavailable);
          this.form.controls.password.reset();
        },
      });
  }
}
