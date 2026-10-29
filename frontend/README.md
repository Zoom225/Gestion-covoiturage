# DriveConnect — socle frontend

Angular **22.2.3**, CLI **22.2.5**, Tailwind CSS **4.3.3**, TypeScript strict et composants standalone. Node compatible : 22.22.3+, 24.15+ ou 26 ; versions verrouillées dans `package-lock.json`.

## Lancer

Depuis `frontend/` :

```powershell
npm ci
npm start -- --host localhost --port 4200
```

Ouvrir http://localhost:4200. Le backend doit fonctionner sur http://localhost:8081.
L’URL est centralisée dans `src/environments/environment.ts`, injectable par `API_URL` dans les tests. Aucun secret ne doit être placé dans cette configuration publique.

Dans l’environnement du processus Spring Boot, configurer `CORS_ALLOWED_ORIGINS=http://localhost:4200` avant son démarrage. La recette a confirmé cette origine sur le backend local existant. Ne pas désactiver Spring Security ni utiliser `--disable-web-security`.

## Structure et responsabilités

```text
src/app/
  core/auth/          DTO HTTP via AuthApi, session Signals via AuthService, validations
  core/guards/        AuthGuard, RoleGuard et destination de retour autorisée
  core/interceptors/ JWT limité à l’API et conversion des erreurs HTTP
  core/services/     URL injectable et messages d’erreur français
  features/          accueil, connexion, activation, tableau-de-bord, erreurs
  layouts/           cadre public et cadre privé
  shared/components/ logo, en-tête, pied de page, icônes, message, mot de passe, bouton
  shared/models/     contrats TypeScript conformes aux records Java
```

Les composants affichent les pages et pilotent leurs formulaires réactifs. `AuthApi` est le seul endroit contenant les trois appels HTTP d’authentification. `AuthService` organise la connexion et conserve le profil dans un Signal en lecture seule. Le Router charge les pages à la demande.

## Parcours réels

| Action | API Spring Boot |
|---|---|
| Connexion | POST `/api/auth/login`, corps `{identifiant,password}` |
| Profil et rôle courant | GET `/api/auth/me`, JWT Bearer |
| Activation | POST `/api/auth/invitations/accept`, corps `{jeton,password}` ; 204 |

Un CLIENT utilise son **matricule**, un ADMIN son **e-mail**. Le formulaire ne contraint donc pas l’identifiant au format e-mail.
Le lien émis par le courrier Spring Boot est `/activation#invitation=...`. Le fragment est lu puis retiré de l’URL par remplacement de l’entrée d’historique courante. Le code peut aussi être saisi manuellement. Après activation : connexion normale, sans JWT inventé.

Les règles de mot de passe d’activation sont reprises : 15 points de code Unicode minimum, 72 octets UTF-8 maximum et confirmation identique. La connexion ne réimpose pas une nouvelle règle de longueur minimale aux comptes déjà existants.

Pas d’inscription publique, de refresh token, de module de réservation, paiement ou administration dans ce lot. Le tableau de bord affiche uniquement le profil réellement retourné ; ses cartes « À venir » ne sont pas des liens vers des fonctionnalités fictives.

## Session et erreurs

- JWT uniquement en mémoire, sans localStorage/sessionStorage et sans mot de passe enregistré par l’application.
- Le profil n’est publié qu’après `/auth/me`. Les droits ne sont pas déduits d’un JWT décodé côté navigateur.
- L’interceptor ajoute le JWT uniquement à l’origine et au chemin de l’API prévus, sauf connexion/activation publiques. Un ancien 401 n’efface pas une nouvelle session.
- Déconnexion, expiration serveur et 401 protégé effacent la session. Une actualisation complète exige une reconnexion. Aucun endpoint de déconnexion serveur n’existe : l’effacement est local, le JWT expire selon sa durée serveur.
- Les guards facilitent la navigation ; Spring Boot reste responsable des autorisations.
- La redirection après connexion accepte uniquement la route privée de ce lot, jamais une URL externe.
- Les messages HTTP proviennent d’un dictionnaire français isolé, avec repli sûr. Le corps brut et les traces serveur ne sont jamais affichés. Les textes de page restent dans les templates pour permettre une extraction ultérieure si une traduction devient nécessaire.

## Choix UX/UI

Bleu nuit, turquoise, surfaces claires, police Manrope hébergée avec l’application, illustration SVG du réseau sans statistique inventée. Les tokens Tailwind sont dans `src/styles.css`, ainsi que les styles partagés et les adaptations mobile. Pas de bibliothèque de composants ou d’icônes supplémentaire.

Labels explicites, aides reliées aux champs, erreurs annoncées, mot de passe affichable à la demande, boutons bloqués pendant l’envoi, focus visible, lien d’évitement et animations réduites selon les préférences système.

## Vérifier

```powershell
npm run build
npm test -- --watch=false
npx playwright install chromium
npm run test:e2e
```

Les tests unitaires vérifient contrats, session, interceptor, erreurs, expiration, guards, validation et routes. Playwright démarre `npm run preview`, qui sert le build de production local sans rechargement automatique. Il faut donc compiler après toute modification. Les scénarios vérifient les pages aux largeurs **360, 390, 768, 1024 et 1440 px**, les parcours de formulaire, le clavier et les règles automatiques axe WCAG A/AA. Les captures/rapports sont produits dans `test-results/` et `playwright-report/`, exclus de Git.

**Distinction des preuves :** les tests d’interface utilisent un transport HTTP contrôlé dans `e2e/`, jamais dans l’application. Ils ne constituent pas une authentification réussie sur la base locale. Aucun mode d’authentification factice ni donnée de démonstration n’est livré dans `src/`.

Avec les deux serveurs démarrés, la commande suivante teste les refus réels Spring Boot depuis Chromium, CORS compris, sans écrire de compte :

```powershell
npm run test:api
```

La base locale était vide de comptes lors de la recette. Une connexion réussie et une activation valide sur cette base exigent un compte/invitation réel fourni par le parcours opérateur existant. Les réussites sont vérifiées côté frontend avec le transport de test et côté backend par la suite de validation existante ; aucune réussite locale de bout en bout n’est prétendue sans identifiants.

Le workflow `.github/workflows/frontend.yml` installe avec `npm ci`, compile, exécute les tests unitaires puis les contrôles Chromium et publie les rapports. Le workflow backend est conservé.

### Résultats locaux — 29 octobre 2026

- `npm run build` : réussi, chargement initial 298,80 ko avant compression (81,57 ko estimés transférés).
- `npm test -- --watch=false` : **40 tests réussis**, 5 fichiers, aucun test ignoré.
- `npm run test:e2e` : **11 scénarios réussis**, incluant les sept pages publiques/privées aux cinq largeurs et les parcours de formulaire. Aucune violation détectée par les règles axe WCAG A/AA sélectionnées ; ceci ne constitue pas un audit humain complet ni une certification.
- `npm run test:api` : deux refus réels vérifiés dans Chromium vers Spring Boot : 401 connexion, 400 invitation invalide, messages français et CORS fonctionnels.
- Vérification visuelle des captures d’accueil et de connexion sur mobile et ordinateur. Clavier, lien d’évitement et préférence d’animations réduites réellement testés.
- `tsc --noUnusedLocals --noUnusedParameters --noEmit` : réussi ; audit npm : aucune vulnérabilité signalée au moment de l’installation.
- Limite restante : pas de compte/invitation valide disponible dans la base locale vide ; connexion et activation réussies sur cette base à rejouer avec un compte réel, sans contourner le backend.

## Présentation orale — environ 60 secondes

« Le frontend est entièrement isolé dans le dossier frontend. Angular assemble des composants standalone : chaque page a sa responsabilité et les éléments communs, comme les champs de mot de passe ou les messages, sont réutilisés. Un service appelle les trois endpoints Spring Boot existants ; un autre gère la session et le profil avec des Signals. Après la connexion, le profil est vérifié par le backend avant d’ouvrir le tableau de bord. Le JWT reste en mémoire et l’interceptor l’ajoute uniquement aux requêtes de notre API. Les guards orientent l’utilisateur selon sa session et son rôle, mais les vraies autorisations restent côté serveur. L’activation respecte le lien personnel envoyé par invitation. L’interface est en français et s’adapte aux petits écrans. Les tests couvrent les formulaires, les erreurs, les accès, la déconnexion et le comportement responsive, sans développer les modules métier suivants. »

Références : [contrat backend](../docs/frontend/contrat-api-angular.md), [compatibilité Angular](https://angular.dev/reference/versions), [installation Tailwind](https://tailwindcss.com/docs/installation/framework-guides/angular).
