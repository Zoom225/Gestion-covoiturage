import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Ces réponses contrôlées appartiennent exclusivement aux tests d'interface.
const api = 'http://localhost:8081/api';
const profile = {
  compteId: 1,
  identifiant: 'L001',
  role: 'CLIENT',
  clientId: 2,
  categorie: 'LOCAL',
  zoneClientId: 3,
  perimetreAdmin: null,
  zoneAdminId: null,
};
const headers = {
  'access-control-allow-origin': 'http://localhost:4200',
  'access-control-allow-headers': 'authorization,content-type',
  'access-control-allow-methods': 'GET,POST,OPTIONS',
};
async function mockApi(page: Page, status = 200) {
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
      return route.fulfill({ headers, json: profile });
    }
    return route.abort();
  });
}
async function signIn(page: Page) {
  await page.goto('/connexion');
  await page.getByLabel('Matricule ou adresse e-mail').fill('L001');
  await page.getByLabel('Mot de passe', { exact: true }).fill('mot-de-passe-de-test');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page).toHaveURL(/\/tableau-de-bord$/);
}
for (const width of [360, 390, 768, 1024, 1440]) {
  test(`pages publiques et privées sans débordement, largeur ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockApi(page);
    for (const path of [
      '/',
      '/connexion',
      '/activation',
      '/acces-refuse',
      '/connexion-api',
      '/inconnue',
    ]) {
      await page.goto(path);
      await expect(page.locator('h1')).toBeVisible();
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true);
      const audit = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
        .analyze();
      expect(audit.violations, `${path} : ${audit.violations.map((v) => v.id).join(', ')}`).toEqual(
        [],
      );
    }
    await signIn(page);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
        .violations,
    ).toEqual([]);
    await page.screenshot({ path: `test-results/tableau-${width}.png`, fullPage: true });
    await page.goto('/');
    await expect(page.locator('.feature-grid')).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: `test-results/accueil-${width}.png`, fullPage: true });
    await page.goto('/connexion');
    await expect(page.locator('#login-password')).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: `test-results/connexion-${width}.png`, fullPage: true });
  });
}
test('validation, refus, mot de passe visible et erreur serveur sans détail brut', async ({
  page,
}) => {
  await mockApi(page, 401);
  await page.goto('/connexion');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByText('Saisissez votre identifiant', { exact: false })).toBeVisible();
  await page.getByLabel('Matricule ou adresse e-mail').fill('L001');
  await page.getByLabel('Mot de passe', { exact: true }).fill('incorrect');
  await page.getByRole('button', { name: 'Afficher le mot de passe', exact: true }).click();
  await expect(page.locator('#login-password')).toHaveAttribute('type', 'text');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Identifiants incorrects');
  await expect(page.getByRole('alert')).not.toContainText('java.sql');
});
test('bloque un second envoi pendant une connexion en cours', async ({ page }) => {
  let release: () => void = () => undefined;
  const pending = new Promise<void>((resolve) => (release = resolve));
  await page.route(`${api}/auth/login`, async (route) => {
    if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    await pending;
    return route.fulfill({ status: 401, headers, json: { code: 'UNAUTHORIZED' } });
  });
  await page.goto('/connexion');
  await page.getByLabel('Matricule ou adresse e-mail').fill('L001');
  await page.getByLabel('Mot de passe', { exact: true }).fill('mot-de-passe-de-test');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Connexion en cours…' })).toBeDisabled();
  release();
  await expect(page.getByRole('alert')).toContainText('Identifiants incorrects');
  await expect(page.getByRole('button', { name: 'Se connecter', exact: true })).toBeEnabled();
});
test('activation depuis le lien réel du courrier, puis connexion normale demandée', async ({
  page,
}) => {
  await page.route(`${api}/auth/invitations/accept`, (route) => {
    if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    expect(route.request().postDataJSON()).toEqual({
      jeton: 'a'.repeat(43),
      password: 'mot-de-passe-de-test',
    });
    return route.fulfill({ status: 204, headers });
  });
  await page.goto(`/activation#invitation=${'a'.repeat(43)}`);
  await expect(page).toHaveURL(/\/activation$/);
  await page.getByLabel('Choisir un mot de passe', { exact: true }).fill('mot-de-passe-de-test');
  await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill('mot-de-passe-de-test');
  await page.getByRole('button', { name: 'Activer mon compte', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Votre compte est prêt.' })).toBeVisible();
  await page.getByRole('link', { name: 'Aller à la connexion' }).click();
  await expect(page.locator('h2')).toHaveText('Connexion');
});
test('guard, redirection interne, déconnexion et perte volontaire de session au rechargement', async ({
  page,
}) => {
  await mockApi(page);
  await page.goto('/tableau-de-bord');
  await expect(page).toHaveURL(/connexion\?retour=/);
  await signIn(page);
  await expect(page.getByText('L001', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  await page.getByRole('button', { name: 'Se déconnecter', exact: true }).click();
  await expect(page).toHaveURL(/\/connexion$/);
  await signIn(page);
  await page.reload();
  await expect(page).toHaveURL(/connexion\?retour=/);
});
test('navigation au clavier et préférence de réduction des animations', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByText('Aller au contenu principal')).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('main')).toBeFocused();
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior)).toBe(
    'auto',
  );
});
test('panne réseau réelle du transport : message et possibilité de réessayer', async ({ page }) => {
  await page.route(`${api}/auth/login`, (route) => route.abort('connectionfailed'));
  await page.goto('/connexion');
  await page.getByLabel('Matricule ou adresse e-mail').fill('L001');
  await page.getByLabel('Mot de passe', { exact: true }).fill('mot-de-passe-de-test');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Impossible de joindre');
  await expect(page.getByRole('button', { name: 'Se connecter', exact: true })).toBeEnabled();
  await expect(page.getByRole('link', { name: 'Consulter l’aide à la connexion' })).toBeVisible();
});
