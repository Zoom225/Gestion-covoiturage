import { HttpErrorResponse } from '@angular/common/http';

// Dictionnaire français isolé pour faciliter une future traduction, sans infrastructure i18n.
const messages: Readonly<Record<string, string>> = {
  UNAUTHORIZED: 'Votre session a expiré ou n’est plus disponible. Reconnectez-vous.',
  FORBIDDEN: 'Vous ne disposez pas des droits nécessaires pour accéder à cette ressource.',
  INVITATION_INVALID:
    'Cette invitation est invalide, expirée ou déjà utilisée. Demandez une nouvelle invitation à votre administrateur.',
  INVALID_INVITATION:
    'Cette invitation est invalide, expirée ou déjà utilisée. Demandez une nouvelle invitation à votre administrateur.',
  VALIDATION_ERROR: 'Vérifiez les informations saisies avant de réessayer.',
  INVALID_REQUEST: 'Les informations transmises ne sont pas valides. Vérifiez votre saisie.',
};

export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly unavailable = false,
  ) {
    super(message);
  }
}

export function toApiFailure(error: unknown, url: string): ApiFailure {
  if (error instanceof ApiFailure) return error;
  if (!(error instanceof HttpErrorResponse))
    return new ApiFailure(
      500,
      'Une erreur inattendue est survenue. Réessayez dans quelques instants.',
    );
  const payload: unknown = error.error;
  const code =
    typeof payload === 'object' &&
    payload !== null &&
    'code' in payload &&
    typeof payload.code === 'string'
      ? payload.code
      : '';
  if (error.status === 0)
    return new ApiFailure(
      0,
      'Impossible de joindre DriveConnect. Vérifiez votre connexion puis réessayez.',
      true,
    );
  if (url.endsWith('/auth/login') && error.status === 401)
    return new ApiFailure(
      401,
      'Identifiants incorrects ou compte indisponible. Vérifiez votre matricule ou votre adresse e-mail et votre mot de passe.',
    );
  if (url.endsWith('/auth/invitations/accept') && error.status === 400)
    return new ApiFailure(400, messages['INVITATION_INVALID']);
  if (error.status >= 500)
    return new ApiFailure(
      error.status,
      'Le service est momentanément indisponible. Réessayez dans quelques instants.',
      true,
    );
  return new ApiFailure(
    error.status,
    messages[code] ??
      (
        {
          400: 'Vérifiez les informations saisies avant de réessayer.',
          401: messages['UNAUTHORIZED'],
          403: messages['FORBIDDEN'],
          404: 'La ressource demandée n’est pas disponible.',
          409: 'Cette opération n’est plus possible dans l’état actuel. Actualisez la page puis réessayez.',
          429: 'Trop de tentatives. Patientez quelques instants avant de réessayer.',
        } as Record<number, string>
      )[error.status] ??
      'Cette action n’a pas pu aboutir. Réessayez.',
  );
}
