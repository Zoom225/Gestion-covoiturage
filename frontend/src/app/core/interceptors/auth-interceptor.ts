import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth-service';
import { API_URL, isApiRequest, isPublicAuthRequest } from '../services/api-url';
import { toApiFailure } from '../services/api-error';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const base = inject(API_URL);
  if (!isApiRequest(request.url, base)) return next(request);
  const auth = inject(AuthService);
  const publicRequest = isPublicAuthRequest(request.url, base);
  const token = publicRequest ? null : auth.accessToken();
  const outgoing = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(outgoing).pipe(
    catchError((error: unknown) => {
      const failure = toApiFailure(error, request.url);
      if (!publicRequest && token && failure.status === 401 && auth.accessToken() === token)
        auth.expire();
      return throwError(() => failure);
    }),
  );
};
