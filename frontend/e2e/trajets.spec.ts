import { test, expect, Page, Route } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { api, headers, mockApi, profile, signIn } from './support/auth';
import { TrajetResponse } from '../src/app/features/trajets/models/trajet.model';

const zone = {
  zoneId: 3,
  nom: 'Bruxelles',
  actif: true,
  fuseauHoraire: 'Europe/Brussels',
  ouverteOccasionnels: true,
};
const trip: TrajetResponse = {
  trajetId: 23,
  zoneId: 3,
  vehiculeId: 5,
  organisateurId: 99,
  depart: '2099-11-03T09:00:00Z',
  arrivee: '2099-11-03T10:00:00Z',
  fuseauHoraire: 'Europe/Brussels',
  type: 'PUBLIC',
  statut: 'PLANIFIE',
  origine: 'Bruxelles-Midi',
  destination: 'Namur',
  traiteJ1Le: null,
  verificationJ1Requise: false,
};
interface Scenario {
  zonesEmpty?: boolean;
  empty?: boolean;
  paginated?: boolean;
  zonesFailures?: number;
  searchFailures?: number;
  detailStatus?: number;
  protectedStatus?: number;
  trips?: TrajetResponse[];
}
async function network(page: Page, scenario: Scenario = {}) {
  await mockApi(page, 200, { ...profile, categorie: 'VIP', zoneClientId: null });
  const content = scenario.trips ?? [
    trip,
    {
      ...trip,
      trajetId: 24,
      origine: 'Bruxelles-Central',
      destination: 'Liège-Guillemins',
      type: 'PRIVE',
      statut: 'COMPLET',
    },
  ];
  const respond = (route: Route, json: unknown, status = 200) =>
    route.fulfill({ status, headers, json });
  let zoneFailures = scenario.zonesFailures ?? 0;
  let searchFailures = scenario.searchFailures ?? 0;
  await page.route(`${api}/zones?*`, (route) => {
    expect(route.request().headers()['authorization']).toBe('Bearer jeton-de-test-interface');
    if (zoneFailures-- > 0) return respond(route, { message: 'java.sql.secret' }, 503);
    const zones = scenario.zonesEmpty ? [] : [zone, { ...zone, zoneId: 8, nom: 'Namur' }];
    return respond(route, {
      content: zones,
      page: 0,
      size: 100,
      totalElements: zones.length,
      totalPages: zones.length ? 1 : 0,
    });
  });
  await page.route(`${api}/trajets?*`, (route) => {
    const request = route.request();
    const url = new URL(request.url());
    expect(request.method()).toBe('GET');
    expect(request.headers()['authorization']).toBe('Bearer jeton-de-test-interface');
    expect([...url.searchParams.keys()].sort()).toEqual(
      url.searchParams.has('zoneId') ? ['page', 'size', 'zoneId'] : ['page', 'size'],
    );
    expect(url.searchParams.get('size')).toBe('12');
    if (scenario.protectedStatus) return respond(route, {}, scenario.protectedStatus);
    if (searchFailures-- > 0) return respond(route, { message: 'java.sql.secret' }, 503);
    const pageIndex = Number(url.searchParams.get('page') ?? 0);
    const empty = scenario.empty || url.searchParams.get('zoneId') === '8';
    const list = empty
      ? []
      : scenario.paginated
        ? Array.from({ length: 13 }, (_, i) => ({ ...trip, trajetId: 100 + i }))
        : content;
    return respond(route, {
      content: list.slice(pageIndex * 12, (pageIndex + 1) * 12),
      page: pageIndex,
      size: 12,
      totalElements: list.length,
      totalPages: Math.ceil(list.length / 12),
    });
  });
  await page.route(`${api}/trajets/*`, (route) => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1));
    if (scenario.detailStatus)
      return respond(route, { message: 'java.sql.secret' }, scenario.detailStatus);
    return respond(
      route,
      content.find((item) => item.trajetId === id) ?? { ...trip, trajetId: id },
    );
  });
}
async function openSearch(page: Page) {
  await signIn(page);
  await page.getByRole('link', { name: 'Rechercher un trajet', exact: true }).click();
  await expect(page).toHaveURL(/\/trajets(?:\?|$)/);
  await expect(
    page.getByRole('heading', { name: 'Rechercher un trajet', level: 1, exact: true }),
  ).toBeVisible();
}
for (const width of [360, 390, 768, 1024, 1440]) {
  test(`recherche et détail accessibles, largeur ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await network(page);
    await openSearch(page);
    await expect(page.locator('app-trip-card')).toHaveCount(2);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
        .violations,
    ).toEqual([]);
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: `test-results/recherche-${width}.png`, fullPage: true });
    await page.getByRole('link', { name: /Voir le détail du trajet de Bruxelles-Midi/ }).click();
    await expect(page.getByRole('heading', { name: 'Détail du trajet' })).toBeVisible();
    await expect(page.getByText('Véhicule n° 5')).toBeVisible();
    await expect(page.getByText('Nombre non communiqué')).toBeVisible();
    await expect(page.getByText('Réservation bientôt disponible', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Réserver une place' })).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
        .violations,
    ).toEqual([]);
    await page.screenshot({ path: `test-results/detail-trajet-${width}.png`, fullPage: true });
    await page.getByRole('link', { name: 'Retour aux résultats' }).click();
    await expect(page.locator('app-trip-card')).toHaveCount(2);
    await page.getByLabel('Zone de covoiturage').selectOption('8');
    await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
    await expect(page).toHaveURL(/zoneId=8/);
    await expect(page.getByText('Aucun trajet ne correspond', { exact: false })).toBeVisible();
    await expect(page.locator('app-trip-card')).toHaveCount(0);
  });
}
test('pagination, critères et retour depuis le détail restent cohérents', async ({ page }) => {
  await network(page, { paginated: true });
  await openSearch(page);
  await expect(page.locator('app-trip-card')).toHaveCount(12);
  await page.getByRole('button', { name: 'Page suivante', exact: true }).click();
  await expect(page).toHaveURL(/page=1/);
  await expect(page.locator('app-trip-card')).toHaveCount(1);
  await expect(page.getByText('Page 2 sur 2')).toBeVisible();
  await page.getByRole('link', { name: /Voir le détail du trajet/ }).click();
  await page.getByRole('link', { name: 'Retour aux résultats' }).click();
  await expect(page).toHaveURL(/page=1/);
  await expect(page.getByText('Page 2 sur 2')).toBeVisible();
  await page.getByLabel('Zone de covoiturage').selectOption('3');
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  await expect(page).toHaveURL(/zoneId=3&page=0/);
  await expect(page.getByText('Page 1 sur 2')).toBeVisible();
});
test('absence de zones : aucune fausse recherche ou zone proposée', async ({ page }) => {
  await network(page, { zonesEmpty: true });
  await openSearch(page);
  await expect(page.getByRole('heading', { name: 'Aucune zone disponible' })).toBeVisible();
  await expect(page.getByLabel('Zone de covoiturage')).toHaveCount(0);
});
test('chargements en erreur puis reprise sans exposer le message serveur', async ({ page }) => {
  await network(page, { zonesFailures: 1, searchFailures: 1 });
  await openSearch(page);
  await expect(page.getByRole('alert')).toContainText('momentanément indisponible');
  await expect(page.getByRole('alert')).not.toContainText('java.sql');
  await page.getByRole('button', { name: 'Réessayer le chargement des zones' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  await expect(page.locator('app-trip-card')).toHaveCount(2);
  await page.getByRole('button', { name: 'Actualiser les résultats' }).click();
  await expect(page.locator('app-trip-card')).toHaveCount(2);
});
test('un trajet invisible ou absent reste masqué et permet le retour aux résultats', async ({
  page,
}) => {
  await network(page, { detailStatus: 404 });
  await openSearch(page);
  await page.getByRole('link', { name: /Voir le détail du trajet de Bruxelles-Midi/ }).click();
  await expect(page.getByRole('alert')).toContainText('introuvable ou n’est plus accessible');
  await expect(page.locator('.trip-detail-main')).toHaveCount(0);
  await page.getByRole('link', { name: 'Retour aux résultats' }).click();
  await expect(page.locator('app-trip-card')).toHaveCount(2);
});
test('un 401 de recherche termine la session et demande la reconnexion', async ({ page }) => {
  await network(page, { protectedStatus: 401 });
  await signIn(page);
  await page.getByRole('link', { name: 'Rechercher un trajet', exact: true }).click();
  await expect(page).toHaveURL(/connexion\?raison=session-expiree/);
  await expect(page.getByText('Votre session a expiré', { exact: false })).toBeVisible();
});
test('un administrateur ne peut pas ouvrir le parcours client', async ({ page }) => {
  await mockApi(page, 200, {
    ...profile,
    role: 'ADMIN',
    clientId: null,
    categorie: null,
    zoneClientId: null,
    perimetreAdmin: 'GLOBAL',
  });
  await signIn(page, '/trajets');
  await expect(page).toHaveURL(/acces-refuse$/);
  await expect(
    page.getByRole('heading', { name: 'Vous n’avez pas accès à cet espace.' }),
  ).toBeVisible();
});
test('un refus serveur 403 ne présente pas de résultats et conserve la session', async ({
  page,
}) => {
  await network(page, { protectedStatus: 403 });
  await openSearch(page);
  await expect(page.getByRole('alert')).toContainText('droits nécessaires');
  await expect(page.locator('app-trip-card')).toHaveCount(0);
  await expect(page).toHaveURL(/\/trajets$/);
  await expect(page.getByRole('button', { name: 'Se déconnecter', exact: true })).toBeVisible();
});
test('les quatre statuts et les lieux historiques sont présentés sans prix ou place inventés', async ({
  page,
}) => {
  const trips = ['PLANIFIE', 'COMPLET', 'ANNULE', 'REALISE'].map((statut, i) => ({
    ...trip,
    trajetId: 23 + i,
    statut: statut as TrajetResponse['statut'],
    origine: i === 0 ? null : trip.origine,
    destination: i === 0 ? null : trip.destination,
  }));
  await network(page, { trips });
  await openSearch(page);
  await expect(page.locator('app-trip-card')).toHaveCount(4);
  await expect(page.getByText('Départ non renseigné', { exact: false })).toBeVisible();
  for (const label of ['Planifié', 'Complet', 'Annulé', 'Réalisé'])
    await expect(page.getByText(label, { exact: true })).toBeVisible();
  for (let index = 0; index < 4; index++) {
    await page.locator('app-trip-card').nth(index).getByRole('link').click();
    await expect(page.locator('.trip-detail-main')).toBeVisible();
    if (index === 1)
      await expect(page.getByText('Ce trajet est complet.', { exact: true })).toBeVisible();
    if (index === 2)
      await expect(page.getByText('Ce trajet a été annulé.', { exact: true })).toBeVisible();
    if (index === 3)
      await expect(page.getByText('Ce trajet est terminé.', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Réserver une place' })).toHaveCount(0);
    await page.getByRole('link', { name: 'Retour aux résultats' }).click();
    await expect(page.locator('app-trip-card')).toHaveCount(4);
  }
});
