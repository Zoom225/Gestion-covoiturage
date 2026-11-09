import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Contrats d'interface contrôlés. Les règles serveur sont testées séparément avec PostgreSQL.
const api = 'http://localhost:8081/api';
const headers = {
  'access-control-allow-origin': 'http://localhost:4173',
  'access-control-allow-headers': 'authorization,content-type,idempotency-key',
  'access-control-allow-methods': 'GET,POST,PUT,OPTIONS',
};
const trip = {
  trajetId: 7,
  zoneId: 3,
  vehiculeId: 9,
  organisateurId: 99,
  origine: 'Bruxelles',
  destination: 'Namur',
  type: 'PUBLIC',
  statut: 'PLANIFIE',
  depart: '2027-01-15T10:00:00Z',
  arrivee: '2027-01-15T11:00:00Z',
  fuseauHoraire: 'Europe/Brussels',
  traiteJ1Le: null,
  verificationJ1Requise: false,
};
const pageOf = <T>(content: T[]) => ({
  content,
  page: 0,
  size: 10,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
});
const profile = {
  compteId: 1,
  identifiant: 'C0001',
  role: 'CLIENT',
  clientId: 2,
  categorie: 'LOCAL',
  zoneClientId: 3,
  perimetreAdmin: null as string | null,
  zoneAdminId: null as number | null,
};

async function prepare(page: Page, admin = false, failFirstPayment = false, activePenalty = false) {
  const user = admin
    ? { ...profile, role: 'ADMIN', categorie: null, perimetreAdmin: 'LOCAL', zoneAdminId: 3 }
    : profile;
  let bookingStatus: string | null = activePenalty ? 'CONFIRMEE' : null;
  let settled = activePenalty ? 0 : 80;
  let paid = activePenalty;
  let cancelled = false;
  let attempts = 0;
  const paymentKeys: string[] = [];
  await page.route(api + '/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/', '');
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    const send = (data: unknown, status = 200) => route.fulfill({ status, headers, json: data });
    if (path === 'auth/login')
      return send({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 900 });
    expect(request.headers()['authorization']).toBe('Bearer test-token');
    if (path === 'auth/me') return send(user);
    if (path === 'zones' || path === 'admin/zones')
      return send(
        pageOf([
          {
            zoneId: 3,
            nom: 'Bruxelles',
            actif: true,
            fuseauHoraire: 'Europe/Brussels',
            ouverteOccasionnels: true,
          },
        ]),
      );
    if (path === 'vehicules')
      return send(pageOf([{ vehiculeId: 9, zoneId: 3, zoneNom: 'Bruxelles', actif: true }]));
    if (path === 'trajets' && request.method() === 'POST') {
      const body = request.postDataJSON();
      expect(body.depart).toBe('2027-01-15T10:00:00.000Z');
      expect(body.arrivee).toBe('2027-01-15T11:00:00.000Z');
      expect(body.zoneId).toBe(3);
      expect(body.vehiculeId).toBe(9);
      return send({ ...trip, ...body, organisateurId: 2 }, 201);
    }
    if (path === 'trajets') return send(pageOf([trip]));
    if (path === 'trajets/7') return send(trip);
    if (path === 'trajets/7/reservations') {
      bookingStatus = 'EN_ATTENTE';
      return send(
        {
          reservationId: 12,
          trajetId: 7,
          clientId: 2,
          statut: bookingStatus,
          organisateur: false,
          annuleLe: null,
        },
        201,
      );
    }
    if (path === 'reservations/me')
      return send(
        pageOf(
          bookingStatus
            ? [
                {
                  reservationId: 12,
                  trajetId: 7,
                  clientId: 2,
                  statut: bookingStatus,
                  organisateur: false,
                  annuleLe: null,
                  annuleAvecPenalite: cancelled && activePenalty,
                },
              ]
            : [],
        ),
      );
    if (path === 'reservations/12/paiements') {
      paymentKeys.push(request.headers()['idempotency-key']);
      expect(paymentKeys.at(-1)).toMatch(/^[0-9a-f-]{36}$/);
      if (failFirstPayment && attempts++ === 0) return send({ code: 'PAYMENT_UNAVAILABLE' }, 503);
      paid = true;
      bookingStatus = 'CONFIRMEE';
      return send({
        paiementId: 1,
        nature: 'PLACE',
        ressourceId: 12,
        montant: 20,
        mode: 'SIMULATION',
      });
    }
    if (path === 'reservations/12/annulation') {
      cancelled = true;
      bookingStatus = 'ANNULEE';
      return send({
        reservationId: 12,
        trajetId: 7,
        clientId: 2,
        statut: bookingStatus,
        organisateur: false,
        annuleLe: new Date().toISOString(),
        annuleAvecPenalite: activePenalty,
      });
    }
    if (path === 'dettes/me')
      return send(
        pageOf([
          {
            detteId: 8,
            trajetId: 7,
            montant: 80,
            regle: settled,
            statut: settled === 80 ? 'REGULARISEE' : 'ACTIVE',
          },
        ]),
      );
    if (path === 'dettes/8/paiements') {
      expect(request.headers()['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/);
      settled += request.postDataJSON().montant;
      return send({
        paiementId: 2,
        nature: 'DETTE',
        ressourceId: 8,
        montant: settled,
        mode: 'SIMULATION',
      });
    }
    if (path === 'paiements/me')
      return send(
        pageOf(
          paid
            ? [
                {
                  paiementId: 1,
                  nature: 'PLACE',
                  ressourceId: 12,
                  montant: 20,
                  mode: 'SIMULATION',
                  valideLe: '2026-12-01T10:00:00Z',
                  montantRembourse: cancelled && !activePenalty ? 20 : 0,
                  rembourseLe: cancelled && !activePenalty ? '2026-12-02T10:00:00Z' : null,
                  remboursementRefusePourPenalite: cancelled && activePenalty,
                },
              ]
            : [],
        ),
      );
    if (path === 'admin/comptes')
      return send(
        pageOf([
          {
            compteId: 4,
            identifiant: 'C0004',
            role: 'CLIENT',
            categorie: 'LOCAL',
            etatIdentifiants: 'INVITE',
            accesEffectif: false,
          },
        ]),
      );
    if (path === 'admin/clients') {
      expect(request.postDataJSON()).toEqual({
        email: 'client@example.invalid',
        categorie: 'LOCAL',
        zoneClientId: 3,
      });
      return send({}, 201);
    }
    if (path === 'admin/comptes/4/invitations') return send({}, 202);
    throw new Error('Requête inattendue : ' + request.method() + ' ' + path);
  });
  await page.goto('/connexion');
  await page.getByLabel('Matricule ou adresse e-mail').fill('C0001');
  await page.getByLabel('Mot de passe', { exact: true }).fill('mot-de-passe-test');
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page).toHaveURL(/tableau-de-bord$/);
  return { paymentKeys };
}

test.use({ timezoneId: 'Europe/Brussels' });

test('recherche, réservation, reprise de paiement sans double débit et annulation', async ({
  page,
}) => {
  const state = await prepare(page, false, true);
  await page.getByRole('link', { name: 'Trajets', exact: true }).click();
  await page.getByRole('link', { name: 'Consulter le trajet n° 7' }).click();
  await page.getByRole('button', { name: 'Demander ma réservation' }).click();
  await expect(page.getByText('EN_ATTENTE', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Payer 20 € en simulation' }).click();
  await expect(page.getByRole('alert')).toContainText('momentanément indisponible');
  await page.getByRole('button', { name: 'Payer 20 € en simulation' }).click();
  await expect(page.getByText('CONFIRMEE', { exact: false })).toBeVisible();
  expect(state.paymentKeys).toHaveLength(2);
  expect(state.paymentKeys[0]).toBe(state.paymentKeys[1]);
  await page.getByRole('button', { name: 'Annuler cette réservation' }).click();
  await page.getByRole('button', { name: "Confirmer l'annulation" }).click();
  await expect(page.getByText('ANNULEE', { exact: false })).toBeVisible();
  await page.getByRole('link', { name: 'Paiements et dettes', exact: true }).click();
  await expect(page.getByRole('cell', { name: /SIMULATION/ })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Remboursement' })).toBeVisible();
});

test('création : sélection de zone et véhicule, horaires locaux convertis en UTC', async ({
  page,
}) => {
  await prepare(page);
  await page.getByRole('link', { name: 'Ouvrir mes trajets' }).click();
  await page
    .getByRole('combobox', { name: 'Zone', exact: true })
    .selectOption({ label: 'Bruxelles' });
  await page
    .getByRole('combobox', { name: 'Véhicule', exact: true })
    .selectOption({ label: 'Véhicule n° 9 · Bruxelles' });
  await page.getByLabel('Départ', { exact: true }).fill('Bruxelles');
  await page.getByLabel('Destination', { exact: true }).fill('Namur');
  await page.getByLabel('Date et heure de départ').fill('2027-01-15T11:00');
  await page.getByLabel("Date et heure d'arrivée").fill('2027-01-15T12:00');
  await page.getByRole('button', { name: 'Enregistrer le trajet' }).click();
  await expect(page).toHaveURL(/trajets\/7$/);
});

test('règlements partiels puis complets de la dette, contrôle des montants', async ({ page }) => {
  await prepare(page, false, false, true);
  await page.getByRole('link', { name: 'Paiements et dettes', exact: true }).click();
  const amount = page.getByLabel('Montant à régler pour la dette n° 8');
  await amount.fill('81');
  await page.getByRole('button', { name: 'Régler en simulation' }).click();
  await expect(page.getByRole('alert')).toContainText('limité au solde restant');
  await amount.fill('20.50');
  await page.getByRole('button', { name: 'Régler en simulation' }).click();
  await expect(amount).toHaveValue('59.5');
  await page.getByRole('button', { name: 'Régler en simulation' }).click();
  await expect(page.getByText(/REGULARISEE/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Régler en simulation' })).toHaveCount(0);
});

test('annulation avec pénalité : aucun remboursement, même après règlement de la dette', async ({
  page,
}) => {
  await prepare(page, false, false, true);
  await page.getByRole('link', { name: 'Mes réservations', exact: true }).click();
  await page.getByRole('button', { name: 'Annuler cette réservation' }).click();
  await expect(page.getByText(/Aucun remboursement n'est accordé/)).toBeVisible();
  await page.getByRole('button', { name: "Confirmer l'annulation" }).click();
  await expect(page.getByRole('status')).toContainText('sans remboursement');
  await page.getByRole('link', { name: 'Paiements et dettes', exact: true }).click();
  await expect(page.getByRole('cell', { name: /Refusé : pénalité active/ })).toBeVisible();
  await page.getByRole('button', { name: 'Régler en simulation' }).click();
  await expect(page.getByText(/REGULARISEE/)).toBeVisible();
  await expect(page.getByRole('cell', { name: /Refusé : pénalité active/ })).toBeVisible();
});

test('modification : conservation des secondes et absence de transfert de zone ou de type', async ({
  page,
}) => {
  await prepare(page);
  const ownTrip = { ...trip, organisateurId: 2, depart: '2027-01-15T10:00:37Z' };
  await page.route(api + '/trajets/7', async (route) => {
    if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
    if (route.request().method() === 'PUT') {
      expect(route.request().postDataJSON()).toEqual({
        vehiculeId: 9,
        origine: 'Bruxelles',
        destination: 'Namur',
        depart: '2027-01-15T10:00:37.000Z',
        arrivee: '2027-01-15T11:00:00.000Z',
      });
    }
    return route.fulfill({ headers, json: ownTrip });
  });
  await page.getByRole('link', { name: 'Trajets', exact: true }).click();
  await page.getByRole('link', { name: 'Consulter le trajet n° 7' }).click();
  await page.getByRole('link', { name: 'Modifier mon trajet' }).click();
  await expect(page.getByRole('combobox', { name: 'Zone', exact: true })).toBeDisabled();
  await expect(page.getByLabel('Type de trajet')).toBeDisabled();
  await expect(page.getByLabel('Date et heure de départ')).toHaveValue('2027-01-15T11:00:37');
  await page.getByRole('button', { name: 'Enregistrer le trajet' }).click();
  await expect(page).toHaveURL(/trajets\/7$/);
});

test('administration locale : création de client et renouvellement sans choix de rôle global', async ({
  page,
}) => {
  await prepare(page, true);
  await page.getByRole('link', { name: 'Administration', exact: true }).click();
  await page.getByLabel('Adresse e-mail du client').fill('client@example.invalid');
  await expect(page.getByLabel('Catégorie').locator('option[value="VIP"]')).toHaveCount(0);
  await page.getByRole('button', { name: 'Créer le client' }).click();
  await expect(page.getByRole('status')).toContainText('Client créé');
  await page.getByRole('button', { name: "Renouveler l'invitation" }).click();
  await expect(page.getByRole('status')).toContainText('précédente est révoquée');
});

test('erreur métier affichée et navigation mobile accessible', async ({ page }) => {
  await prepare(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('link', { name: 'Trajets', exact: true }).click();
  await page.getByRole('link', { name: 'Consulter le trajet n° 7' }).click();
  await page.route(api + '/trajets/7/reservations', (route) =>
    route.fulfill({
      status: 409,
      headers,
      json: { code: 'TRIP_FULL', message: 'Ce trajet ne dispose plus de places disponibles.' },
    }),
  );
  await page.getByRole('button', { name: 'Demander ma réservation' }).click();
  await expect(page.getByRole('alert')).toContainText('plus de places disponibles');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  const accessibility = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(accessibility.violations).toEqual([]);
});
