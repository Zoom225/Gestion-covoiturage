import { FormControl, FormGroup } from '@angular/forms';
import { invitationPassword, notBlank, passwordBytes, passwordsMatch } from './auth-validators';
import { safeReturnUrl } from '../guards/safe-return-url';
import { HttpErrorResponse } from '@angular/common/http';
import { toApiFailure } from '../services/api-error';

describe('Validation conforme au backend et messages français', () => {
  it('refuse les identifiants vides ou uniquement composés d’espaces', () => {
    expect(notBlank(new FormControl('  '))).toEqual({ required: true });
    expect(notBlank(new FormControl('L001'))).toBeNull();
  });
  it('compte les points de code et les octets UTF-8 du mot de passe', () => {
    expect(invitationPassword(new FormControl('a'.repeat(14)))).not.toBeNull();
    expect(invitationPassword(new FormControl('😀'.repeat(15)))).toBeNull();
    expect(passwordBytes(new FormControl('é'.repeat(36)))).toBeNull();
    expect(passwordBytes(new FormControl('é'.repeat(37)))).not.toBeNull();
  });
  it('exige une confirmation identique', () => {
    const form = new FormGroup({
      password: new FormControl('a'),
      confirmation: new FormControl('b'),
    });
    expect(passwordsMatch(form)).not.toBeNull();
  });
  it.each([
    'https://evil.test',
    '//evil.test',
    '/\\evil.test',
    '/administration',
    '/connexion',
    null,
  ])('bloque une redirection non autorisée : %s', (value) => {
    expect(safeReturnUrl(value)).toBe('/tableau-de-bord');
  });
  it('préserve le retour interne autorisé', () =>
    expect(safeReturnUrl('/tableau-de-bord?vue=profil')).toBe('/tableau-de-bord?vue=profil'));
  it('rend une panne réseau compréhensible sans détail technique', () => {
    const result = toApiFailure(new HttpErrorResponse({ status: 0 }), '/auth/login');
    expect(result.unavailable).toBe(true);
    expect(result.message).toContain('Impossible de joindre');
  });
  it('ignore tout message brut même pour un code inconnu', () => {
    const result = toApiFailure(
      new HttpErrorResponse({
        status: 500,
        error: { message: 'java.sql.Secret', code: 'UNKNOWN' },
      }),
      '/test',
    );
    expect(result.message).not.toContain('java');
  });
  it('explique une invitation expirée ou consommée', () => {
    const result = toApiFailure(
      new HttpErrorResponse({ status: 400 }),
      '/api/auth/invitations/accept',
    );
    expect(result.message).toContain('invitation');
  });
});
