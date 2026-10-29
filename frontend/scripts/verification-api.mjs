import assert from 'node:assert/strict';
import { chromium } from '@playwright/test';

// Recette sans données privées ni écriture de compte : utilise le vrai Spring Boot.
// Lancer Angular sur localhost:4200 et Spring Boot sur localhost:8081 auparavant.
const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  await page.goto('http://localhost:4200/connexion');
  await page
    .getByLabel('Matricule ou adresse e-mail')
    .fill('compte-inexistant-recette@example.invalid');
  await page.getByLabel('Mot de passe', { exact: true }).fill('recette-refus-sans-secret');
  const refused = page.waitForResponse(
    (response) => response.url() === 'http://localhost:8081/api/auth/login',
  );
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  assert.equal((await refused).status(), 401);
  await page.getByRole('alert').filter({ hasText: 'Identifiants incorrects' }).waitFor();
  await page.goto(`http://localhost:4200/activation#invitation=${'a'.repeat(43)}`);
  await page
    .getByLabel('Choisir un mot de passe', { exact: true })
    .fill('recette-refus-sans-secret');
  await page
    .getByLabel('Confirmer le mot de passe', { exact: true })
    .fill('recette-refus-sans-secret');
  const invalid = page.waitForResponse(
    (response) => response.url() === 'http://localhost:8081/api/auth/invitations/accept',
  );
  await page.getByRole('button', { name: 'Activer mon compte', exact: true }).click();
  assert.equal((await invalid).status(), 400);
  await page.getByRole('alert').filter({ hasText: 'invitation' }).waitFor();
  const protectedResponses = await page.evaluate(async () => {
    const statuses = [];
    for (const path of ['/zones?page=0&size=100', '/trajets?page=0&size=12', '/trajets/1']) {
      const response = await fetch(`http://localhost:8081/api${path}`);
      statuses.push(response.status);
    }
    return statuses;
  });
  assert.deepEqual(protectedResponses, [401, 401, 401]);
  console.log(
    'Recette navigateur → Spring Boot réel : connexion refusée 401, invitation invalide 400, zones/recherche/détail sans JWT refusés 401 ; CORS et messages français vérifiés.',
  );
} finally {
  await browser.close();
}
