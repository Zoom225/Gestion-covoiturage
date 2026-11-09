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

const businessCodes = new Set([
  'INVALID_TIMEZONE',
  'UNAVAILABILITY_TARGET_REQUIRED',
  'RESERVATION_NOT_CANCELLED',
  'ACTIVE_DEBT',
  'ALREADY_PAID',
  'BOOKING_HORIZON',
  'DEPARTURE_PASSED',
  'DUPLICATE_RESERVATION',
  'IDEMPOTENCY_CONFLICT',
  'INVALID_DEBT_AMOUNT',
  'INVALID_INTERVAL',
  'PENDING_RESERVATION_INCOMPATIBLE',
  'RESOURCE_CHANGED',
  'RESOURCE_HAS_TRIP',
  'RESOURCE_UNAVAILABLE',
  'TRIP_ARRIVAL_NOT_PASSED',
  'TRIP_ALREADY_STARTED',
  'TRIP_NOT_MODIFIABLE',
  'TRIP_HAS_CONFIRMED_RESERVATION',
  'TRIP_HAS_PAYMENT',
  'TRIP_HAS_DEBT',
  'TRIP_LOCATIONS_LOCKED',
  'TRIP_FULL',
  'TRIP_NOT_OPEN',
  'VEHICLE_CONFLICT',
  'VEHICLE_INACTIVE',
  'VEHICLE_ZONE_MISMATCH',
  'VIP_REQUIRED',
  'ZONE_TIMEZONE_REQUIRED',
  'UNAVAILABILITY_TARGET_IMMUTABLE',
  'RESERVATION_NOT_PENDING',
  'TRIP_J1_ALREADY_PROCESSED',
  'TRIP_J1_VERIFICATION_REQUIRED',
  'TRIP_J1_DUE',
]);

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
  if (
    error.status === 400 &&
    code === 'VALIDATION_ERROR' &&
    typeof payload === 'object' &&
    payload !== null &&
    'validationErrors' in payload &&
    Array.isArray(payload.validationErrors)
  ) {
    const details = payload.validationErrors
      .filter(
        (item: unknown): item is { message: string } =>
          typeof item === 'object' &&
          item !== null &&
          'message' in item &&
          typeof item.message === 'string',
      )
      .slice(0, 3)
      .map((item) => item.message)
      .join(' ');
    if (details) return new ApiFailure(400, 'Vérifiez votre saisie : ' + details);
  }
  if (
    (error.status === 400 || error.status === 409) &&
    businessCodes.has(code) &&
    typeof payload === 'object' &&
    payload !== null &&
    'message' in payload &&
    typeof payload.message === 'string'
  ) {
    return new ApiFailure(error.status, payload.message);
  }
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
