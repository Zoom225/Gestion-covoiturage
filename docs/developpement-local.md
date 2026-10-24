# Développement local — version récente de DriveConnect

Ce guide accompagne le portage des corrections locales sur origin/master, sans
modifier les fonctionnalités métier, JWT, Swagger ou les migrations V1–V4.

## Ouvrir le nouveau worktree dans IntelliJ

Sur le poste préparé, ouvrir **le dossier `covoiturage-dev-latest`** dans une
nouvelle fenêtre IntelliJ : File → Open, sélectionner le dossier ou son pom.xml,
puis charger le projet Maven. Ne pas déplacer ni réinitialiser `covoiturage`.

- JDK du projet et Maven Runner : Java 21.
- Module Maven : covoiturage.
- Configuration partagée : `.run/DriveConnect_dev.run.xml`, nom **DriveConnect — dev**.
- Classe principale : `com.plateforme.covoiturage.CovoiturageApplication`.
- Profil : dev ; répertoire de travail : `$PROJECT_DIR$`.
- Script d'environnement : `$PROJECT_DIR$/scripts/intellij-env.bat`.
- Héritage de l'environnement activé ; aucune valeur secrète dans la configuration.
- Port IntelliJ retenu : 8081, via `--server.port=8081`.

Si la configuration n'apparaît pas, terminer l'import Maven puis ouvrir Run →
Edit Configurations et sélectionner/recréer ces mêmes paramètres. L'option
Environment variables / Browse for .env files and scripts doit pointer sur le
batch, pas sur le fournisseur PowerShell interne. Ce chargeur est exécuté à
chaque Run/Debug, même si IntelliJ était déjà ouvert.

Ne pas démarrer une seconde instance sur un port déjà occupé. Arrêter sa propre
instance précédente ou choisir un autre port dans Program arguments. Les scripts
ne terminent aucun autre processus Java.

## Configuration privée

`.env.local.ps1` est la référence locale ; `.env` est son export Compose, généré
avec seulement DB_USERNAME et DB_PASSWORD. Le secret JWT n'est pas exporté vers
Compose. Les fichiers privés et le registre de démonstration sont exclus de Git.

Dans le worktree préparé sur ce poste, le fichier privé `.env.local.ps1` est un
relais vers `../covoiturage/.env.local.ps1`. Le fichier initial et sa clé
`.env.jwt.local` restent à leur emplacement : aucune seconde valeur de mot de
passe ou de clé JWT n'est maintenue. Conserver ce dossier source pour ce relais.
Le relais lui-même n'est pas versionné. Son absence sur un autre poste n'est
pas masquée : configurer alors une référence privée propre à ce poste.

Pour une nouvelle installation uniquement, copier `.env.local.example.ps1` vers
`.env.local.ps1` si ce dernier n'existe pas, puis renseigner les secrets dans
cette copie ignorée par Git. Ne jamais écraser un fichier privé existant.
Employer une clé JWT locale stable en Base64 d'au moins 32 octets aléatoires,
distincte des secrets de production ; aucun secret par défaut n'est fourni.

Après une modification autorisée de la référence locale :

```powershell
.\scripts\sync-compose-env.ps1
```

Les scripts dev-up/dev-test/dev-down et le chargeur IntelliJ réalisent aussi
cet export automatiquement. Le remplacement est atomique ; les caractères
spéciaux sont échappés pour Compose. Un .env non reconnu ou enrichi de clés
supplémentaires est conservé et son remplacement est refusé.

Ne pas éditer le .env généré. Une variable déjà définie dans le terminal prend
priorité sur .env : éviter les surcharges obsolètes. Ne pas afficher les fichiers
privés ni la configuration Compose résolue. Les scripts locaux refusent prod,
même combiné avec dev, ainsi que les surcharges de datasource/Flyway/JVM.

## Docker et PostgreSQL

Ouvrir Docker Desktop avec le moteur Linux. Depuis la racine du nouveau worktree :

```powershell
.\scripts\sync-compose-env.ps1
docker compose config --services
docker compose up -d
docker compose ps
docker compose logs --tail=30 postgres
```

Compose utilise le nom de projet fixe driveconnect-dev : les worktrees partagent
le même service PostgreSQL et le même volume `driveconnect-dev_postgres-data`.
Le port publié reste `127.0.0.1:15432`, vers 5432 dans le conteneur.
L'URL JDBC locale est `jdbc:postgresql://127.0.0.1:15432/carshare`.
Ne pas créer de projet Compose concurrent pour ce port et ne pas changer les
identifiants d'un volume déjà initialisé. Le script vérifie les conteneurs
existants et refuse de remplacer un service incompatible.

Pour attendre explicitement la santé du service :

```powershell
docker compose up -d --wait
```

## Backend et API administratives

Pour préparer PostgreSQL puis démarrer la configuration IntelliJ :

```powershell
.\scripts\dev-up.ps1 -DatabaseOnly
```

Cliquer ensuite Run/Debug sur **DriveConnect — dev**. Alternative PowerShell :

```powershell
.\scripts\dev-up.ps1 -Port 8081
```

Attendre **Started CovoiturageApplication**, après Flyway et l'initialisation
Hibernate/JPA. La version récente contient les deux opérations :

- GET /api/admin/comptes
- GET /api/admin/comptes/{id}

Swagger UI : <http://127.0.0.1:8081/swagger-ui/index.html>.
OpenAPI : <http://127.0.0.1:8081/v3/api-docs>.
Les GET administratifs exigent toujours un JWT ADMIN ; un visiteur reçoit 401
et un CLIENT authentifié 403. Les périmètres GLOBAL/LOCAL restent inchangés.
Swagger peut décrire ces routes sans qu'un compte soit créé ou connecté.
Aucun compte ni mot de passe de démonstration n'est initialisé.

## Flyway et données existantes

Le backend récent contient V1–V4. Avant le premier démarrage sur une base ancienne,
auditer les matricules, la casse/unicité des logins et le lien Client/Compte.
V3/V4 refusent les données incompatibles au lieu de les convertir silencieusement.
Une incompatibilité exige un plan explicite ; ne pas modifier les migrations.
V3 prépare les séquences et réservations ; V4 ajoute les contraintes de login.
Ces migrations ne réécrivent ni IDs ni hashes BCrypt des comptes existants.

Hibernate conserve ddl-auto=validate et Flyway reste activé. Une fois V3/V4
appliquées, la base partagée est en V4 pour tous les worktrees. Un retour à un
ancien code ne remet pas son schéma en V2 : éviter les écritures avec ce code
ancien et ne pas lancer de migration inverse improvisée.

## Compiler et tester

```powershell
.\scripts\dev-test.ps1
```

Le script exécute `mvnw.cmd clean verify`, avec tous les tests existants et
PostgreSQL Testcontainers éphémère, et affiche les totaux Surefire réels.
Il retourne un code non nul en cas d'échec, d'absence de tests ou de test ignoré.
Les tests n'utilisent pas la base Docker de développement et ne nécessitent pas
de secret JWT partagé : leur configuration génère une clé propre aux tests.
Le premier téléchargement nécessite Maven Central et Docker Hub.

Pour une invocation Maven directe après chargement des variables locales :

```powershell
. .\.env.local.ps1 *> $null
.\mvnw.cmd clean verify
```

## Arrêt

Arrêter d'abord le backend avec Ctrl+C ou Stop IntelliJ, puis :

```powershell
.\scripts\dev-down.ps1
```

Ce script arrête PostgreSQL proprement et conserve conteneur et volume.
`docker compose down` retire conteneur et réseau mais conserve le volume ;
cette commande affecte le service partagé avec les autres worktrees.
Ne jamais ajouter -v ni lancer de prune. Aucun script ne supprime les données.

## Portée du portage

Les scripts publics et les seules propriétés dev nécessaires sont portés ;
le README du dossier Swagger n'est pas copié sur celui du master.
La configuration prod, le code applicatif, les tests métier et V1–V4 restent
inchangés. Les résultats de validation sont consignés après leur exécution.

## Validation réalisée le 24 octobre 2026

- Base de branche : origin/master a2bf04b (PR #16 fusionnée).
- Java 21.0.9, Spring Boot 4.1.1, PostgreSQL 16.6.
- `scripts/dev-test.ps1` → `mvnw.cmd clean verify` : BUILD SUCCESS,
  95 tests réussis, zéro échec, zéro erreur et zéro test ignoré ; JAR généré.
- Docker Compose direct fonctionne depuis ce worktree après génération du .env.
- JDBC TCP réel depuis Windows vers 127.0.0.1:15432 : authentification réussie.
- Audit préalable V2 : zéro Client et zéro Compte, aucune collision ou
  incompatibilité de matricule/login ; aucune conversion de compte nécessaire.
- Démarrage réel via `dev-up.ps1 -Port 8081`, profil dev :
  `Started CovoiturageApplication` confirmé après Flyway et Hibernate/JPA.
- V3 et V4 existantes appliquées avec succès ; V1–V4 inchangées dans Git.
- Les quatre tables métier préexistantes client/compte/zone/vehicule ont les
  mêmes nombres de lignes et contenus après migration ; aucun compte créé.
- Volume driveconnect-dev_postgres-data d'origine, créé le 21 octobre 2026,
  conservé ; service healthy et port 15432 accessible.
- Swagger UI, bundle JavaScript, swagger-config et OpenAPI : HTTP 200.
- OpenAPI contient GET /api/admin/comptes et GET /api/admin/comptes/{id}.
- Ces deux GET et /api/auth/me sans JWT : HTTP 401 ; aucun droit public ajouté.
- Relais privé et batch IntelliJ : mêmes valeurs reçues, vérifiées sans affichage.
- Le processus de vérification est arrêté pour libérer 8081 ; PostgreSQL reste
  disponible. Le clic Run dans la nouvelle fenêtre IntelliJ reste à effectuer
  par l'utilisateur ; aucun lancement IDE non observé n'est annoncé.

Acquis concernés partiellement : AA4 (migration et intégrité), AA5 (configuration
et couches conservées), AA6 (JWT et secrets), AA7 (OpenAPI et routes protégées),
AA10 (95 tests et intégration), AA11 (installation et preuves). Cette correction
d'environnement ne constitue pas une validation complète de ces acquis et
n'implémente aucune fonctionnalité Angular ou invitation.

Suivi : [issue #17](https://github.com/Zoom225/Gestion-covoiturage/issues/17).
