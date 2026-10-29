import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, defer, finalize, Subject, switchMap, takeUntil, tap, throwError } from 'rxjs';
import { LoginRequest, ProfileResponse } from '../../shared/models/auth.models';
import { AuthApi } from './auth-api';
import { ApiFailure } from '../services/api-error';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly currentUser = signal<ProfileResponse | null>(null);
  readonly user = this.currentUser.asReadonly();
  readonly authenticated = computed(() => this.user() !== null);
  private token: string | null = null;
  private expiresAt = 0;
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;
  private readonly cancelled = new Subject<void>();

  login(request: LoginRequest) {
    return defer(() => {
      this.cancelled.next();
      this.clear();
      return this.api.login(request).pipe(
        tap((result) => {
          if (
            !result.accessToken ||
            result.tokenType !== 'Bearer' ||
            !Number.isFinite(result.expiresIn) ||
            result.expiresIn <= 0 ||
            !Number.isSafeInteger(Date.now() + result.expiresIn * 1000)
          ) {
            throw new ApiFailure(502, 'La connexion n’a pas pu être établie. Réessayez.');
          }
          this.token = result.accessToken;
          this.expiresAt = Date.now() + result.expiresIn * 1000;
          this.scheduleExpiry();
        }),
        switchMap(() => this.api.profile()),
        tap((profile) => {
          if (!this.accessToken())
            throw new ApiFailure(401, 'Votre session a expiré. Reconnectez-vous.');
          this.currentUser.set(profile);
        }),
        catchError((error: unknown) => {
          this.clear();
          return throwError(() => error);
        }),
        takeUntil(this.cancelled),
        finalize(() => {
          if (!this.currentUser()) this.clear();
        }),
      );
    });
  }

  accessToken(): string | null {
    if (this.token && Date.now() >= this.expiresAt) {
      this.expire();
      return null;
    }
    return this.token;
  }

  hasSession(): boolean {
    return this.accessToken() !== null && this.authenticated();
  }

  logout(): void {
    this.cancelled.next();
    this.clear();
    void this.router.navigateByUrl('/connexion');
  }

  expire(): void {
    const hadSession = this.token !== null;
    this.clear();
    if (hadSession)
      void this.router.navigate(['/connexion'], { queryParams: { raison: 'session-expiree' } });
  }

  private clear(): void {
    clearTimeout(this.expiryTimer);
    this.token = null;
    this.expiresAt = 0;
    this.currentUser.set(null);
  }

  private scheduleExpiry(): void {
    this.expiryTimer = setTimeout(
      () => {
        if (Date.now() >= this.expiresAt) this.expire();
        else this.scheduleExpiry();
      },
      Math.min(this.expiresAt - Date.now(), 2_147_483_647),
    );
  }
}
