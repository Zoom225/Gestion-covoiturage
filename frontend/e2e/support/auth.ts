import { expect, Page } from '@playwright/test';
import { ProfileResponse } from '../../src/app/shared/models/auth.models';

export const api = 'http://localhost:8081/api';
export const profile: ProfileResponse = {
  compteId: 1,
  identifiant: 'L001',
  role: 'CLIENT',
  clientId: 2,
  categorie: 'LOCAL',
  zoneClientId: 3,
  perimetreAdmin: null,
  zoneAdminId: null,
};
export const headers = {
  'access-control-allow-origin': 'http://localhost:4200',
  'access-control-allow-headers': 'authorization,content-type',
  'access-control-allow-methods': 'GET,POST,OPTIONS',
};

// Transport contrôlé uniquement pour les tests ; tout endpoint non déclaré est refusé.
export async function mockApi(page: Page, status = 200, user: ProfileResponse = profile) {
  await page.route(`${api}/**`, async (route) => {
    const req = route.request();
    if (req.method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    if (req.url() === `${api}/auth/login`)
      return route.fulfill({
        status,
        headers,
        json:
          status === 200
            ? { accessToken: 'jeton-de-test-interface', tokenType: 'Bearer', expiresIn: 900 }
            : { code: 'UNAUTHORIZED', message: 'java.sql.ne-pas-afficher' },
      });
    if (req.url() === `${api}/auth/me`) {
      expect(req.headers()['authorization']).toBe('Bearer jeton-de-test-interface');
      return route.fulfill({ headers, json: user });
    }
    return route.abort();
  });
}
export async function signIn(page: Page, destination = '/tableau-de-bord') {
  await page.goto(`/connexion?retour=${encodeURIComponent(destination)}`);
  await page.getByLabel('Matricule ou adresse e-mail').fill('L001');
  await page.getByLabel('Mot de passe', { exact: true }).fill('mot-de-passe-de-test');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  if (destination === '/tableau-de-bord') await expect(page).toHaveURL(/\/tableau-de-bord$/);
}
