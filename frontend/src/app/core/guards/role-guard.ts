import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth-service';
import { Role } from '../../shared/models/auth.models';

export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const roles = route.data['roles'] as readonly Role[] | undefined;
  return (
    (auth.hasSession() && (!roles?.length || roles.includes(auth.user()!.role))) ||
    inject(Router).createUrlTree(['/acces-refuse'])
  );
};
