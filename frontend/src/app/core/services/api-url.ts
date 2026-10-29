import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export const API_URL = new InjectionToken<string>('API_URL', { factory: () => environment.apiUrl });

export function isApiRequest(url: string, base: string): boolean {
  try {
    const target = new URL(url);
    const api = new URL(base);
    return (
      target.origin === api.origin &&
      (target.pathname === api.pathname || target.pathname.startsWith(`${api.pathname}/`))
    );
  } catch {
    return false;
  }
}

export function isPublicAuthRequest(url: string, base: string): boolean {
  if (!isApiRequest(url, base)) return false;
  const path = new URL(url).pathname;
  const prefix = new URL(base).pathname;
  return path === `${prefix}/auth/login` || path === `${prefix}/auth/invitations/accept`;
}
