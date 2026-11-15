import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { LanguageService } from '../../../core/i18n/language.service';

@Component({
  selector: 'app-admin-login-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="login-shell">
      <div class="login-hero">
        <div class="login-hero-content">
          <div class="login-hero-icon"></div>
          <h1 class="login-hero-title">{{ language.t('admin.login.heroTitle') }}</h1>
          <p class="login-hero-sub">{{ language.t('admin.login.heroSubtitle') }}</p>
          <div class="login-hero-badges">
            <span class="login-badge login-badge-global">{{
              language.t('admin.login.globalBadge')
            }}</span>
            <span class="login-badge login-badge-agence">{{
              language.t('admin.login.agenceBadge')
            }}</span>
          </div>
        </div>
      </div>

      <div class="login-form-panel">
        <div class="login-form-card">
          <div class="login-form-header">
            <span class="login-form-icon" aria-hidden="true"
              ><svg class="ui-icon"><use href="images/ui-icons.svg#lock" /></svg
            ></span>
            <h2 class="login-form-title">{{ language.t('admin.login.formTitle') }}</h2>
            <p class="login-form-sub">{{ language.t('admin.login.formSubtitle') }}</p>
          </div>

          <form [formGroup]="form" class="login-form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>{{ language.t('admin.login.email') }}</mat-label>
              <input matInput type="email" formControlName="email" />
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>{{ language.t('admin.login.password') }}</mat-label>
              <input
                matInput
                [type]="showAdminPassword ? 'text' : 'password'"
                formControlName="password"
              />
              <button
                mat-icon-button
                matSuffix
                type="button"
                class="password-toggle-button"
                [attr.aria-label]="
                  showAdminPassword
                    ? language.t('admin.login.hidePassword')
                    : language.t('admin.login.showPassword')
                "
                (click)="toggleAdminPassword()"
              >
                <svg class="ui-icon" aria-hidden="true" width="24" height="24">
                  <use
                    [attr.href]="
                      showAdminPassword ? 'images/ui-icons.svg#eye-off' : 'images/ui-icons.svg#eye'
                    "
                  />
                </svg>
              </button>
            </mat-form-field>

            @if (errorMessage()) {
              <div role="alert" class="login-error">{{ errorMessage() }}</div>
            }

            <div class="login-actions">
              <button
                class="login-btn-primary"
                type="submit"
                [disabled]="loading() || form.invalid"
              >
                @if (loading()) {
                  <mat-spinner
                    [attr.aria-label]="language.t('transport.searching')"
                    diameter="20"
                    style="display:inline-block;margin-right:8px;"
                  ></mat-spinner>
                }
                {{ language.t('admin.login.submit') }}
              </button>
              <a routerLink="/" class="login-btn-secondary">{{ language.t('admin.login.back') }}</a>
            </div>
          </form>
        </div>
      </div>
    </div>

    <style>
      .login-shell {
        display: flex;
        min-height: min(680px, calc(100dvh - 64px));
      }
      .login-hero {
        flex: 1;
        display: none;
        background: linear-gradient(120deg, var(--ui-navy), var(--ui-primary-strong));
        align-items: center;
        justify-content: center;
        padding: 3rem 2rem;
        position: relative;
        overflow: hidden;
      }
      @media (min-width: 768px) {
        .login-hero {
          display: flex;
        }
      }
      .login-hero::before {
        content: '';
        position: absolute;
        inset: 0;
        background: repeating-linear-gradient(
          45deg,
          transparent,
          transparent 30px,
          color-mix(in srgb, var(--ui-surface) 3%, transparent) 30px,
          color-mix(in srgb, var(--ui-surface) 3%, transparent) 60px
        );
      }
      .login-hero-content {
        position: relative;
        z-index: 1;
        text-align: center;
      }
      .login-hero-icon {
        font-size: 5rem;
        margin-bottom: 1rem;
      }
      .login-hero-title {
        font-size: 2.2rem;
        font-weight: 800;
        color: var(--ui-surface);
        margin: 0 0 0.75rem;
      }
      .login-hero-sub {
        color: color-mix(in srgb, var(--ui-surface) 82%, transparent);
        font-size: 1rem;
        line-height: 1.6;
        margin: 0 0 1.5rem;
      }
      .login-hero-badges {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        justify-content: center;
      }
      .login-badge {
        padding: 0.4rem 1rem;
        border-radius: 9999px;
        font-weight: 700;
        font-size: 0.8rem;
        border: 2px solid color-mix(in srgb, var(--ui-surface) 40%, transparent);
      }
      .login-badge-global {
        background: color-mix(in srgb, var(--ui-surface) 15%, transparent);
        color: var(--ui-surface);
      }
      .login-badge-agence {
        background: color-mix(in srgb, var(--ui-surface) 10%, transparent);
        color: color-mix(in srgb, var(--ui-surface) 90%, transparent);
      }

      .login-form-panel {
        flex: 1;
        min-width: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 2rem 1rem;
        background: var(--ui-surface);
      }
      .login-form-card {
        width: 100%;
        max-width: 400px;
        background: var(--ui-surface);
        border-radius: 1.5rem;
        box-shadow: var(--ui-shadow);
        padding: clamp(1.25rem, 3vw, 2.5rem) clamp(1rem, 3vw, 2rem);
        min-width: 0;
        border-top: 5px solid var(--ui-primary-strong);
      }
      .login-form-header {
        text-align: center;
        margin-bottom: 1.5rem;
      }
      @media(max-width: 767px) { .login-shell { min-height: auto; } }
      .login-form-icon {
        font-size: 2.5rem;
      }
      .login-form-title {
        font-size: 1.5rem;
        font-weight: 800;
        color: var(--ui-navy);
        margin: 0.5rem 0 0.25rem;
      }
      .login-form-sub {
        color: var(--ui-text-secondary);
        font-size: 0.88rem;
        margin: 0;
      }

      .login-form {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .login-error {
        background: var(--ui-surface);
        border: 1px solid var(--ui-danger);
        border-radius: 0.75rem;
        padding: 0.75rem 1rem;
        color: var(--ui-danger);
        font-size: 0.875rem;
      }
      .login-actions {
        display: flex;
        gap: 0.75rem;
        align-items: center;
        padding-top: 0.5rem;
      }
      .login-btn-primary {
        flex: 1;
        background: var(--ui-primary);
        color: var(--ui-surface);
        border: none;
        border-radius: 0.75rem;
        padding: 0.8rem 1.5rem;
        font-weight: 700;
        font-size: 1rem;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        box-shadow: var(--ui-shadow);
        transition: transform 0.15s;
      }
      .login-btn-primary:hover:not(:disabled) {
        transform: translateY(-2px);
      }
      .login-btn-primary:disabled {
        background: var(--ui-disabled);
        color: var(--ui-text-secondary);
        cursor: not-allowed;
      }
      .login-btn-secondary {
        padding: 0.8rem 1.25rem;
        border-radius: 0.75rem;
        border: 1.5px solid var(--ui-surface-subtle);
        color: var(--ui-text-secondary);
        font-weight: 600;
        font-size: 0.9rem;
        text-decoration: none;
        background: var(--ui-surface);
        transition: background 0.15s;
        white-space: nowrap;
      }
      .login-btn-secondary:hover {
        background: var(--ui-surface-subtle);
      }

      .password-toggle-button {
        width: 40px;
        height: 40px;
        line-height: 40px;
        color: var(--ui-text-secondary);
      }
    </style>
  `,
})
export class AdminLoginPage {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly language = inject(LanguageService);

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  showAdminPassword = false;

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    this.authService.loginAdmin(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/admin');
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set(this.language.t('admin.login.error'));
      },
    });
  }

  toggleAdminPassword(): void {
    this.showAdminPassword = !this.showAdminPassword;
  }
}
