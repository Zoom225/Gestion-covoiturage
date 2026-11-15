// Validation frontend uniquement : toutes les API sont simulees, aucune base n'est contactee.
const fs = require("node:fs");
const path = require("node:path");
const http = require("node:http");
const assert = require("node:assert/strict");
const project = process.cwd();
const { chromium } = require(
  path.join(project, ".local-validation/ui/browser/node_modules/playwright"),
);
const { default: AxeBuilder } = require(
  path.join(
    project,
    ".local-validation/ui/browser/node_modules/@axe-core/playwright",
  ),
);
const phase = process.env.UI_PHASE || "before";
const output = path.join(project, ".local-validation/ui", process.env.UI_OUTPUT || phase);
const widths = (process.env.UI_WIDTHS || '375,768,1440').split(',').map(Number);
assert.ok(widths.length > 0 && widths.length <= 10 && widths.every(w => Number.isInteger(w) && w >= 320 && w <= 3840), 'Largeurs de validation invalides');
fs.mkdirSync(output, { recursive: true });
const root = path.join(project, "frontend/dist/frontend/browser");
const server = http.createServer((req, res) => {
  let file = path.resolve(
    root,
    "." + decodeURIComponent(new URL(req.url, "http://localhost").pathname),
  );
  if (!file.startsWith(root + path.sep) && file !== root) {
    res.writeHead(403).end();
    return;
  }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory())
    file = path.join(root, "index.html");
  res.setHeader(
    "Content-Type",
    {
      ".html": "text/html",
      ".js": "text/javascript",
      ".css": "text/css",
      ".svg": "image/svg+xml",
      ".webp": "image/webp",
      ".ico": "image/x-icon",
    }[path.extname(file)] || "application/octet-stream",
  );
  fs.createReadStream(file).pipe(res);
});
const agence = {
  id: 1,
  nom: "Agence Bruxelles · Démonstration UI",
  adresse: "Bruxelles, Belgique",
  heureOuverture: "06:00:00",
  heureFermeture: "23:00:00",
  dureeTrajetMinutes: 90,
  dureeEntreTrajetMinutes: 15,
  anneeCivile: 2026,
};
const voiture = {
  id: 1,
  nom: "Voiture de démonstration UI",
  agenceId: 1,
  agenceNom: agence.nom,
  disponible: true,
};
const client = {
  id: 2,
  matricule: "G1002",
  nom: "Démo UI",
  prenom: "Camille",
  email: "camille@example.test",
  typeVoyageur: "VIP",
  agenceId: 1,
  agenceNom: agence.nom,
  solde: 0,
  peutProposerTrajet: true,
  token: "ui-fixture-token",
};
const chauffeur = { ...client, id: 100, matricule: "G1001", prenom: "Alex" };
const admin = {
  token: "ui-fixture-admin",
  role: "GLOBAL",
  agenceId: 1,
  email: "admin@example.test",
};
const day = new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10);
const rides = [
  {
    id: 1,
    vehiculeId: 1,
    vehiculeNom: voiture.nom,
    agenceId: 1,
    agenceNom: agence.nom,
    organisateurId: 100,
    organisateurNom: "Alex Démo UI",
    chauffeurId: 100,
    chauffeurNom: "Alex Démo UI",
    villeDepart: "MONS",
    villeArrivee: "BRUXELLES",
    date: day,
    heureDebut: "09:00:00",
    heureFin: "10:30:00",
    typeTrajet: "PUBLIC",
    statut: "PLANIFIE",
    nbPassagersActuels: 0,
    placesRestantes: 4,
    placesMaximum: 4,
    prixParPassager: 25,
    dateConversionPublic: null,
  },
];
const reservations = [
  {
    id: 90,
    trajetId: 1,
    trajetDateTime: `${day}T09:00:00`,
    villeDepart: "MONS",
    villeArrivee: "BRUXELLES",
    voyageurId: 2,
    voyageurNom: "Camille Démo UI",
    statut: "ANNULEE",
    paiement: {
      id: 90,
      montant: 25,
      statut: "REMBOURSE",
      datePaiement: `${day}T08:00:00`,
    },
  },
];
const report = {
  phase,
  mode: "API simulees dans le navigateur, aucune ecriture PostgreSQL",
  pages: [],
  errors: [],
  unhandled: [],
  accessibility: [],
};
let errorApi = false;
let emptyApi = false;
let paymentFailure = 0;
async function mock(route) {
  const req = route.request(),
    url = new URL(req.url()),
    p = url.pathname.replace("/api", ""),
    method = req.method();
  const send = (json, status = 200) =>
    route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(json),
    });
  if (p === "/trajets/villes")
    return send([
      { code: "MONS", libelle: "Mons" },
      { code: "BRUXELLES", libelle: "Bruxelles" },
      { code: "LIEGE", libelle: "Liège" },
    ]);
  if (p === "/trajets/public")
    return errorApi
      ? send({ message: "Service temporairement indisponible" }, 503)
      : send(emptyApi ? [] : rides);
  if (p === "/voyageurs/login") return send(client);
  if (p === "/auth/login") return send(admin);
  if (p === "/reservations/91/absence" && method === "POST") {
    const r = reservations.find((r) => r.id === 91);
    r.absenceDeclareeLe = new Date().toISOString();
    r.motifAbsence = req.postDataJSON().motif;
    return send(r);
  }
  if (p === "/reservations" && method === "POST") {
    assert.equal(req.postDataJSON().trajetId, 1);
    assert.equal(req.postDataJSON().voyageurId, 2);
    const r = {
      ...reservations[0],
      id: 101,
      statut: "EN_ATTENTE",
      paiement: {
        id: 101,
        montant: 25,
        statut: "EN_ATTENTE",
        datePaiement: null,
      },
    };
    reservations.push(r);
    return send(r, 201);
  }
  if (p === "/paiements/reservation/101/pay" && method === "POST") {
    if (paymentFailure === 1) return send({ message: "Unauthorized" }, 401);
    if (paymentFailure === 2) return route.abort("failed");
    const r = reservations.find((r) => r.id === 101);
    r.statut = "CONFIRMEE";
    r.paiement.statut = "PAYE";
    r.paiement.datePaiement = new Date().toISOString();
    rides[0].placesRestantes = 3;
    rides[0].nbPassagersActuels = 1;
    return send(r.paiement);
  }
  if (p === "/agences") return send([agence]);
  if (/^\/agences\/\d+$/.test(p)) return send(agence);
  if (p.startsWith("/vehicules"))
    return send(
      /\/\d+$/.test(p) && !p.includes("/agence/") ? voiture : [voiture],
    );
  if (p.startsWith("/indisponibilites-service")) return send([]);
  if (p.endsWith("/restrictions"))
    return send([
      {
        id: 1,
        motif: "Historique de démonstration UI",
        dateDebut: "2026-01-01",
        dateFin: "2026-01-08",
        active: false,
        sourceEvenement: "ui:1",
      },
    ]);
  if (p.endsWith("/restriction") || p.endsWith("/balance")) return send(false);
  if (p === "/voyageurs") return send([client, chauffeur]);
  if (p.startsWith("/voyageurs/")) return send(client);
  if (p.startsWith("/reservations/voyageur/")) return send(reservations);
  if (p.startsWith("/reservations/trajet/"))
    return send(
      reservations.filter((r) => r.trajetId === Number(p.split("/").pop())),
    );
  if (p.startsWith("/paiements/voyageur/"))
    return send(reservations.map((r) => r.paiement));
  if (p.startsWith("/trajets/organisateur/"))
    return send([
      {
        ...rides[0],
        id: 2,
        organisateurId: 2,
        chauffeurId: 2,
        organisateurNom: client.prenom,
        typeTrajet: "PRIVE",
      },
    ]);
  if (p === "/trajets" || p.startsWith("/trajets/agence/")) return send(rides);
  if (/^\/trajets\/\d+$/.test(p)) return send(rides[0]);
  report.unhandled.push(method + " " + p);
  return send([]);
}
async function session(page, role) {
  await page.goto("http://127.0.0.1:4201/", { waitUntil: "networkidle" });
  await page.evaluate(
    ({ role, client, admin }) => {
      localStorage.removeItem("gestiontransport_voyageur_session");
      localStorage.removeItem("gestiontransport_admin_session");
      localStorage.setItem("gestiontransport_language", "fr");
      if (role === "voyageur")
        localStorage.setItem(
          "gestiontransport_voyageur_session",
          JSON.stringify({ voyageur: client, token: client.token }),
        );
      if (role === "admin" || role === "local")
        localStorage.setItem(
          "gestiontransport_admin_session",
          JSON.stringify({
            ...admin,
            role: role === "local" ? "AGENCE" : "GLOBAL",
          }),
        );
    },
    { role, client, admin },
  );
}
(async () => {
  await new Promise((resolve) => server.listen(4201, "127.0.0.1", resolve));
  const browser = await chromium.launch({
    executablePath:
      process.env.EDGE_PATH ||
      "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    headless: true,
  });
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.route("**/api/**", mock);
    page.on("pageerror", (error) => report.errors.push(error.message));
    const pages = [
      ["guest", "/", "accueil"],
      ["guest", "/voyageur", "connexion-voyageur"],
      ["guest", "/admin/login", "connexion-admin"],
      ["voyageur", "/voyageur/profile", "profil"],
      ["voyageur", "/voyageur/trajets", "recherche"],
      ["voyageur", "/voyageur/trajets/new", "proposer"],
      ["voyageur", "/voyageur/reservations", "reservations"],
      ["voyageur", "/voyageur/payments", "paiements"],
      ["admin", "/admin", "dashboard"],
      ["admin", "/admin/voyageurs", "voyageurs"],
      ["admin", "/admin/trajets", "trajets"],
      ["admin", "/admin/agences", "agences"],
      ["admin", "/admin/vehicules", "vehicules"],
      ["admin", "/admin/indisponibilites", "indisponibilites"],
      ["admin", "/admin/trajets/new", "proposer-admin"],
      ["local", "/admin", "admin-local"],
    ];
    for (const [role, url, name] of pages) {
      await session(page, role);
      for (const width of widths) {
        await page.setViewportSize({ width, height: 1000 });
        await page.goto("http://127.0.0.1:4201" + url, {
          waitUntil: "networkidle",
        });
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth + 1,
        );
        const missingLabels = await page
          .locator("input:not([type=hidden]),select,textarea")
          .evaluateAll((elements) =>
            elements
              .filter(
                (e) =>
                  !e.labels?.length &&
                  !e.getAttribute("aria-label") &&
                  !e.getAttribute("aria-labelledby"),
              )
              .map((e) => e.outerHTML.slice(0, 150)),
          );
        report.pages.push({ name, role, width, overflow, missingLabels });
        if (width === 1440 && name === "dashboard")
          report.fonts = await page.evaluate(() =>
            [
              ...document.querySelectorAll(
                "body,mat-card-title,mat-card-subtitle",
              ),
            ]
              .slice(0, 4)
              .map((e) => ({
                tag: e.tagName,
                family: getComputedStyle(e).fontFamily,
              })),
          );
        await page.screenshot({
          path: path.join(output, `${name}-${width}.jpg`),
          type: "jpeg",
          quality: 75,
          fullPage: true,
        });
        if (phase === "after" && width === 375) {
          const analysis = await new AxeBuilder({ page })
            .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
            .analyze();
          report.accessibility.push({
            name,
            violations: analysis.violations.map((v) => ({
              id: v.id,
              impact: v.impact,
              nodes: v.nodes.map((n) => ({
                html: n.html,
                summary: n.failureSummary,
              })),
            })),
          });
        }
      }
    }
    if (phase === "after") {
      if (process.env.UI_CAROUSEL === 'true') {
        await session(page, 'guest');
        await require('./validation-carousel.cjs')(page, report, output);
        await require('./validation-carousel.cjs').validateZoom({chromium, mock, session, report, output});
      }
      await session(page, "guest");
      await page.goto("http://127.0.0.1:4201/voyageur", {
        waitUntil: "networkidle",
      });
      await page.locator('[formcontrolname="matricule"]').fill("G1002");
      await page
        .locator('[formcontrolname="password"]')
        .fill("Only_UI_Fixture_123");
      await page.locator(".password-toggle-button").click();
      assert.equal(
        await page.locator('[formcontrolname="password"]').getAttribute("type"),
        "text",
      );
      await page.locator(".password-toggle-button").click();
      const loginA11y = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze();
      report.accessibility.push({
        name: "connexion-active",
        violations: loginA11y.violations.map((v) => ({
          id: v.id,
          impact: v.impact,
          nodes: v.nodes.map((n) => ({
            html: n.html,
            summary: n.failureSummary,
          })),
        })),
      });
      await page.locator('form button[type="submit"]').click();
      await page.waitForURL("**/voyageur/profile");
      report.login =
        "Connexion et affichage/masquage du mot de passe controles avec API simulee";
      await session(page, "guest");
      await page.goto("http://127.0.0.1:4201/admin", {
        waitUntil: "networkidle",
      });
      assert.match(page.url(), /admin\/login$/);
      await session(page, "voyageur");
      await page.goto("http://127.0.0.1:4201/voyageur/trajets", {
        waitUntil: "networkidle",
      });
      await page.locator('[formcontrolname="depart"]').click();
      await page.getByRole("option", { name: "Mons", exact: true }).click();
      await page.locator('[formcontrolname="arrivee"]').click();
      await page
        .getByRole("option", { name: "Bruxelles", exact: true })
        .click();
      await page.locator('[formcontrolname="date"]').fill(day);
      const recherche = page.waitForResponse(
        (r) =>
          r.url().includes("/trajets/public?") &&
          r.url().includes("depart=MONS") &&
          r.url().includes("arrivee=BRUXELLES"),
      );
      await page
        .getByRole("button", { name: "Rechercher un trajet", exact: true })
        .click();
      await recherche;
      await page
        .getByRole("button", { name: "Voir le trajet", exact: true })
        .first()
        .click();
      await page.getByRole("dialog").waitFor();
      for (const width of widths) {
        await page.setViewportSize({ width, height: 1000 });
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth + 1,
        );
        const box = await page.getByRole("dialog").boundingBox();
        assert.ok(box.x >= 0 && box.x + box.width <= width + 1);
        report.pages.push({
          name: "fiche-trajet",
          role: "voyageur",
          width,
          overflow,
          missingLabels: [],
        });
        await page.screenshot({
          path: path.join(output, `fiche-trajet-${width}.jpg`),
          type: "jpeg",
          quality: 75,
          fullPage: true,
        });
      }
      const dialogAnalysis = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze();
      report.accessibility.push({
        name: "fiche-trajet",
        violations: dialogAnalysis.violations.map((v) => ({
          id: v.id,
          impact: v.impact,
          nodes: v.nodes.map((n) => ({
            html: n.html,
            summary: n.failureSummary,
          })),
        })),
      });
      assert.match(
        await page.getByRole("dialog").innerText(),
        /Mons[\s\S]*Bruxelles/,
      );
      const demande = page.waitForResponse(
        (r) =>
          r.url().endsWith("/api/reservations") &&
          r.request().method() === "POST",
      );
      await page
        .getByRole("dialog")
        .getByRole("button", { name: "Réserver", exact: true })
        .click();
      await demande;
      await page.goto("http://127.0.0.1:4201/voyageur/reservations", {
        waitUntil: "networkidle",
      });
      const card = page.locator("mat-card").filter({ hasText: "#101" }).last();
      assert.match(await card.innerText(), /[Aa]ttente/);
      const pay = card.getByRole("button").filter({ hasText: /[Pp]ayer/ });
      paymentFailure = 1;
      await pay.click();
      await page
        .getByRole("alert")
        .filter({ hasText: "Votre session a expiré" })
        .waitFor();
      paymentFailure = 2;
      await pay.click();
      await page
        .getByRole("alert")
        .filter({ hasText: "Connexion impossible" })
        .waitFor();
      paymentFailure = 0;
      await pay.click();
      await page.waitForTimeout(300);
      assert.equal(reservations.find((r) => r.id === 101).statut, "CONFIRMEE");
      assert.equal(rides[0].placesRestantes, 3);
      assert.match(await card.innerText(), /[Cc]onfirm/);
      await page.goto("http://127.0.0.1:4201/voyageur/payments", {
        waitUntil: "networkidle",
      });
      assert.match(await page.locator("main").innerText(), /25/);
      report.flow =
        "Recherche → fiche → demande → paiement simule 25 EUR → confirmation → historique, API simulees";
      rides[0].statut = "COMPLET";
      rides[0].placesRestantes = 0;
      rides[0].nbPassagersActuels = 4;
      await page.goto("http://127.0.0.1:4201/voyageur/trajets", {
        waitUntil: "networkidle",
      });
      assert.equal(
        await page
          .getByRole("button", { name: "Complet", exact: true })
          .first()
          .isDisabled(),
        true,
      );
      await page
        .getByRole("button", { name: "Voir le trajet", exact: true })
        .first()
        .click();
      assert.equal(
        await page
          .getByRole("dialog")
          .getByRole("button", { name: "Réserver", exact: true })
          .isDisabled(),
        true,
      );
      await page.keyboard.press("Escape");
      // Etats administratifs : fixtures en memoire uniquement, apres les captures comparables avant/apres.
      const past = new Date(Date.now() - 2 * 86400000)
        .toISOString()
        .slice(0, 10);
      rides.push({
        ...rides[0],
        id: 3,
        date: past,
        nbPassagersActuels: 1,
        placesRestantes: 3,
        statut: "PLANIFIE",
      });
      reservations.push({
        ...reservations[0],
        id: 91,
        trajetId: 3,
        trajetDateTime: `${past}T09:00:00`,
        statut: "CONFIRMEE",
        paiement: {
          id: 91,
          montant: 25,
          statut: "PAYE",
          datePaiement: `${past}T08:00:00`,
        },
      });
      await session(page, "admin");
      await page.goto("http://127.0.0.1:4201/admin/trajets", {
        waitUntil: "networkidle",
      });
      await page
        .locator("mat-card")
        .last()
        .locator(".adm-trajet-btn-details")
        .click();
      await page
        .getByRole("button", { name: "Déclarer une absence", exact: true })
        .click();
      await page
        .locator("#absence-91")
        .fill("Absence verifiee — demonstration UI sans base de donnees.");
      for (const width of widths) {
        await page.setViewportSize({ width, height: 1000 });
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth + 1,
        );
        report.pages.push({
          name: "absence-administrative",
          role: "admin",
          width,
          overflow,
          missingLabels: [],
        });
        await page.screenshot({
          path: path.join(output, `absence-${width}.jpg`),
          type: "jpeg",
          quality: 75,
          fullPage: true,
        });
      }
      const absenceA11y = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze();
      report.accessibility.push({
        name: "absence-administrative",
        violations: absenceA11y.violations.map((v) => ({
          id: v.id,
          impact: v.impact,
          nodes: v.nodes.map((n) => ({
            html: n.html,
            summary: n.failureSummary,
          })),
        })),
      });
      await page
        .getByRole("button", {
          name: "Enregistrer la déclaration",
          exact: true,
        })
        .click();
      await page
        .getByText(
          "Absence verifiee — demonstration UI sans base de donnees.",
          { exact: false },
        )
        .first()
        .waitFor();
      await page.goto("http://127.0.0.1:4201/admin/voyageurs", {
        waitUntil: "networkidle",
      });
      await page
        .getByRole("button", {
          name: "Historique des restrictions",
          exact: true,
        })
        .first()
        .click();
      await page
        .getByText("Historique de démonstration UI", { exact: false })
        .waitFor();
      for (const width of widths) {
        await page.setViewportSize({ width, height: 1000 });
        report.pages.push({
          name: "historique-restrictions",
          role: "admin",
          width,
          overflow: await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth + 1,
          ),
          missingLabels: [],
        });
        await page.screenshot({
          path: path.join(output, `restrictions-${width}.jpg`),
          type: "jpeg",
          quality: 75,
          fullPage: true,
        });
      }
      const restrictionsA11y = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze();
      report.accessibility.push({
        name: "historique-restrictions",
        violations: restrictionsA11y.violations.map((v) => ({
          id: v.id,
          impact: v.impact,
          nodes: v.nodes.map((n) => ({
            html: n.html,
            summary: n.failureSummary,
          })),
        })),
      });
      report.adminStates =
        "Formulaire absence avec motif et historique restrictions controles avec API simulees";
      await page.setViewportSize({ width: 375, height: 900 });
      await page.goto("http://127.0.0.1:4201/admin", { waitUntil: "networkidle" });
      const adminMenu = page.locator(".admin-menu-toggle");
      assert.equal(await adminMenu.isVisible(), true);
      assert.equal(await adminMenu.getAttribute("aria-expanded"), "false");
      await adminMenu.click();
      await page.waitForFunction(() => document.querySelector('.admin-menu-toggle')?.getAttribute('aria-expanded') === 'true');
      assert.equal(await adminMenu.getAttribute("aria-expanded"), "true");
      await page.locator("#admin-navigation-links a").first().focus();
      await page.keyboard.press("Escape");
      await page.waitForFunction(() => document.querySelector('.admin-menu-toggle')?.getAttribute('aria-expanded') === 'false');
      assert.equal(await adminMenu.getAttribute("aria-expanded"), "false");
      assert.equal(await adminMenu.evaluate((e) => e === document.activeElement), true);
      await adminMenu.click();
      await page.waitForFunction(() => document.querySelector('.admin-menu-toggle')?.getAttribute('aria-expanded') === 'true');
      await page.locator('#admin-navigation-links a[href="/admin/agences"]').click();
      await page.waitForURL("**/admin/agences");
      await page.waitForFunction(() => document.querySelector('.admin-menu-toggle')?.getAttribute('aria-expanded') === 'false');
      assert.equal(await adminMenu.getAttribute("aria-expanded"), "false");
      report.adminNavigation = "Menu mobile : ouverture, Echap, focus et navigation controles";
      await session(page, "guest");
      await page.setViewportSize({ width: 375, height: 900 });
      await page.goto("http://127.0.0.1:4201/", { waitUntil: "networkidle" });
      await page.locator(".lang-trigger").click();
      await page.locator(".lang-option").last().click();
      await page
        .getByRole("button", { name: "View ride", exact: true })
        .first()
        .waitFor();
      await page.locator(".lang-trigger").click();
      await page.locator(".lang-option").first().click();
      await page.reload({ waitUntil: "networkidle" });
      await page.keyboard.press("Tab");
      assert.match(await page.locator(":focus").innerText(), /contenu/);
      await page.getByRole("button", { name: "Menu", exact: true }).click();
      await page.keyboard.press("Escape");
      await page.waitForFunction(
        () =>
          document
            .querySelector(".menu-toggle")
            ?.getAttribute("aria-expanded") === "false",
      );
      emptyApi = true;
      await page.reload({ waitUntil: "networkidle" });
      assert.match(await page.locator("main").innerText(), /trajet/i);
      emptyApi = false;
      errorApi = true;
      await page.reload({ waitUntil: "networkidle" });
      await page.getByRole("alert").waitFor();
      errorApi = false;
      await page.emulateMedia({ reducedMotion: "reduce" });
      assert.equal(
        await page.evaluate(
          () => matchMedia("(prefers-reduced-motion: reduce)").matches,
        ),
        true,
      );
      assert.ok(
        await page
          .locator(".nav-link")
          .first()
          .evaluate(
            (e) => parseFloat(getComputedStyle(e).transitionDuration) <= 0.001,
          ),
      );
      report.translationAndMotion =
        "FR/EN et preference de mouvement reduit controles";
      report.keyboardAndStates =
        "Lien evitement, menu/Echap, protection admin, etats vide/erreur controles";
      assert.equal(
        report.pages.filter((p) => p.overflow).length,
        0,
        "Debordement horizontal",
      );
      assert.equal(
        report.pages.filter((p) => p.missingLabels.length).length,
        0,
        "Champs sans libelle",
      );
      assert.deepEqual(report.errors, []);
      assert.deepEqual(report.unhandled, []);
      assert.equal(
        report.accessibility.flatMap((a) => a.violations).length,
        0,
        "Violations axe-core",
      );
    }
  } finally {
    fs.writeFileSync(
      path.join(output, "rapport.json"),
      JSON.stringify(report, null, 2),
    );
    await browser.close();
    server.close();
  }
  console.log(
    JSON.stringify({
      phase,
      pages: report.pages.length,
      overflow: report.pages.filter((p) => p.overflow).length,
      errors: report.errors,
      unhandled: report.unhandled,
      flow: report.flow,
    }),
  );
})().catch((e) => {
  console.error(e.message);
  server.close();
  process.exitCode = 1;
});
