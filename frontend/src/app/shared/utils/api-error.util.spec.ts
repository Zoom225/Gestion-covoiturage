import { HttpErrorResponse } from '@angular/common/http';
import { toApiFailure } from './api-error.util';

describe('Messages métier de l’API', () => {
  it('explique les erreurs de saisie et le fuseau horaire invalide', () => {
    const validation = toApiFailure(
      new HttpErrorResponse({
        status: 400,
        error: {
          code: 'VALIDATION_ERROR',
          validationErrors: [{ field: 'email', message: 'L’adresse e-mail doit être valide.' }],
        },
      }),
      '/api/admin/clients',
    );
    expect(validation.message).toContain('L’adresse e-mail doit être valide.');
    const timezone = toApiFailure(
      new HttpErrorResponse({
        status: 400,
        error: {
          code: 'INVALID_TIMEZONE',
          message: 'Choisissez un fuseau reconnu, par exemple Europe/Brussels.',
        },
      }),
      '/api/admin/zones',
    );
    expect(timezone.message).toContain('Europe/Brussels');
  });
  it('affiche le message métier J-1 connu, sans dupliquer la règle dans Angular', () => {
    const failure = toApiFailure(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'TRIP_J1_DUE', message: 'Le contrôle J-1 est déjà dû.' },
      }),
      '/api/trajets/7',
    );
    expect(failure.message).toBe('Le contrôle J-1 est déjà dû.');
  });
  it('masque les détails des codes inconnus et des erreurs internes', () => {
    for (const status of [409, 500]) {
      const failure = toApiFailure(
        new HttpErrorResponse({
          status,
          error: { code: 'UNKNOWN_INTERNAL_CODE', message: 'java.sql.secret-database-detail' },
        }),
        '/api/trajets/7',
      );
      expect(failure.message).not.toContain('java.sql');
      expect(failure.message).not.toContain('secret');
    }
  });
});
