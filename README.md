# GestionTransport-Multiple

Plateforme académique de covoiturage entre villes belges, construite sur l'application existante
avec **Java 21, Spring Boot, Angular et PostgreSQL 15**.

**Un trajet = un chauffeur déjà associé + quatre passagers maximum.**
Un voyageur autorisé qui propose un trajet en devient le chauffeur. Les quatre places
sont destinées aux autres passagers. Le même compte peut réserver une place sur le
trajet d’un autre chauffeur. Chaque passager paie **25 €** ; quatre passagers payants
représentent **100 €**. Les places inoccupées ne sont facturées à personne.
Les paiements et remboursements sont **simulés**, sans prestataire bancaire.

## Connexions de validation

Accès directs aux portails de l'application :
* **Portail Administrateur** : [Connexion Admin](http://127.0.0.1:4200/admin/login) (ou port `4202`)
* **Portail Voyageur** : [Connexion Voyageur](http://127.0.0.1:4200/voyageur) (ou port `4202`)

### 1. Comptes Administrateurs

| Rôle | Identifiant (Email) | Mot de passe | Périmètre d'administration |
| :--- | :--- | :--- | :--- |
| **Administrateur global** | `admin@transp.com` | `Admin1234!` | Accès total : toutes les agences, tous les véhicules, création de trajets et KPIs globaux |
| **Administrateur local Lyon** | `admin.lyon@transp.com` | `Admin1234!` | Gestionnaire d'agence : accès restreint aux ressources et voyageurs de son agence |
| **Administrateur local Paris** | `admin.paris@transp.com` | `Admin1234!` | Second gestionnaire d'agence : gestion des ressources de son agence |

### 2. Comptes Voyageurs

| Profil / Usage | Matricule | Mot de passe | Type | Solde | Statut & Règle métier démontrée |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Chauffeur autorisé** | `V1001` | `Voyage1234!` | `VIP` | `0.0 €` | **Chauffeur actif** : autorisé à proposer et publier des trajets publics et privés |
| **Client VIP** | `V1002` | `Voyage1234!` | `VIP` | `0.0 €` | **Client actif** : réservations prioritaires et simulation de paiement |
| **Client local** | `L10001` | `Voyage1234!` | `LOCAL` | `0.0 €` | **Client actif** : rattaché à l'agence de démonstration |
| **Client libre** | `F10001` | `Voyage1234!` | `LIBRE` | `0.0 €` | **Client actif** : voyageur standard tout public |
| **Client pénalisé (Démo)** | `F10002` | `Voyage1234!` | `LIBRE` | **`100.0 €`** | **Cas de pénalité & restriction** :<br>• Solde débiteur de **100 €** (impayé de 4 places pour trajet privé resté incomplet)<br>• **Restriction active 7 jours** : réservations et créations de trajets bloquées par les règles métier |

Ces identifiants concernent la copie locale de validation. Les suffixes numériques,
identifiants internes, catégories, agences et historiques sont conservés.
Les mots de passe sont enregistrés sous forme de hachages BCrypt. Après leur
modification, se déconnecter puis se reconnecter avec les nouveaux identifiants.
Les informations sont également visibles au début de `DataInitializer.java`.

L'initialiseur reste désactivé par défaut. Une mise à jour de comptes existants
exige explicitement le profil `dev`, `DEMO_DATA_ENABLED=true` et
`DEMO_UPDATE_CREDENTIALS=true`, exclusivement sur une copie locale nommée
`transport_schema_copy_...` ou `transport_demo_...`. Les doublons sont contrôlés
avant écriture et les changements sont transactionnels. Aucune mise à jour des
comptes de transportService n'est autorisée par ce mécanisme.

## Fonctionnement métier

Le formulaire de création charge les véhicules disponibles de l'agence choisie.
Le champ est désactivé pendant le chargement ou lorsqu'aucun véhicule n'est
disponible ; un message et un nouvel essai sont proposés en cas d'erreur.
Avec plusieurs véhicules, choisir explicitement dans la liste ; un véhicule
unique est présélectionné. Un changement d'agence efface l'ancien choix.

Sur la copie locale de validation, une **Renault Clio** et une **Peugeot 308**
par agence permettent ces essais. Ces modèles sont des exemples de démonstration.
Les cinq ressources historiques sont
conservées et restent indisponibles. Cette préparation réutilise DataInitializer
avec `DEMO_PREPARE_VEHICLES=true`, profil `dev` et démonstration activée ; elle
est désactivée par défaut, idempotente et refuse la base métier.

- Départ et destination sont deux villes belges distinctes, choisies dans la liste centrale
  du backend. Bruxelles, Mons, Charleroi, Namur, Liège, Anvers, Gand, Louvain, Nivelles et
  Wavre sont proposés. Les retours sont des trajets indépendants ; Bruxelles n'est pas obligatoire.
  **Règle C01 : une ville ne peut pas être à la fois le départ et la destination.**
  La ville déjà choisie est désactivée dans l'autre liste. Un choix identique
  invalide immédiatement le formulaire de création ou de recherche et bloque
  l'envoi. Le backend refuse également les appels directs avec HTTP 400 :
  « Choisissez deux villes différentes : la ville de départ et la destination
  ne peuvent pas être identiques. » Les recherches sans ville ou avec une seule
  ville restent possibles ; aucune donnée historique n'est réécrite.
- Un Voyageur autorisé à proposer un trajet devient automatiquement son chauffeur depuis
  son authentification. Un administrateur sélectionne un chauffeur existant dans son périmètre.
  Aucun chauffeur librement substituable depuis le navigateur.
  La création d'un voyageur par un administrateur demande un mot de passe initial personnel
  de 12 caractères minimum. Aucun mot de passe commun n'est attribué aux nouveaux comptes.
- Le véhicule et le chauffeur ne peuvent pas effectuer deux trajets incompatibles. Les horaires,
  durées estimées et indisponibilités configurés de l'agence sont conservés.
  Un véhicule marqué indisponible dans l'administration ne peut pas recevoir de nouveau trajet.
  Les anciennes ressources sont indisponibles par défaut et nécessitent une qualification
  explicite comme véhicules, sans modification de leur nom, identifiant ou données historiques.
- Un trajet PUBLIC peut être recherché ; les voyageurs locaux restent limités à leur zone.
  Un trajet PRIVE nécessite une invitation. La publication à H-24 exige le consentement enregistré.
- Une demande EN_ATTENTE et son paiement associé ne bloquent **aucune place**. Le premier
  paiement accepté confirme une place disponible, sous transaction et verrou PostgreSQL.
  À quatre confirmations, le trajet est COMPLET ; une cinquième confirmation est refusée.
- Une demande impayée expire après le délai configurable (30 minutes par défaut).
  Une annulation autorisée libère une place confirmée et marque son paiement REMBOURSE.
  Les réservations annulées ou expirées et les paiements restent dans l'historique.
- Une restriction active ou une dette empêche de réserver. Une restriction expirée cesse
  automatiquement de bloquer, sans suppression de son historique. La durée de référence est
  configurable, sept jours par défaut. L'ajout est protégé contre les doublons.
  Les déclenchements non définis (absence, abus, annulation tardive) restent des décisions
  métier à valider ; aucune sanction pour des places inoccupées n'est inventée.

| Catégorie | Matricule      | Fenêtre de réservation | Zone                          |
| --------- | -------------- | ---------------------- | ----------------------------- |
| VIP       | V + 4 chiffres | Jusqu'à J-21           | Toutes les agences autorisées |
| Local     | L + 5 chiffres | Jusqu'à J-14           | Agence autorisée              |
| Libre     | F + 5 chiffres | Jusqu'à J-5            | Trajets publics autorisés     |

Les fenêtres concernent les clients qui réservent, pas la date de création d'un trajet par son chauffeur.

## Architecture conservée

```text
src/main/java/com/transport/gestiontransport/
  controller/       API REST, validation et identité authentifiée
  dto/              Requêtes et réponses API
  mapper/           Conversion entre entités et DTO
  service/impl/     Règles métier, transactions et verrouillages
  repository/       Persistance et requêtes Spring Data JPA
  entity/           Agence, Vehicule, Trajet, Voyageur, Reservation,
                    Paiement, Restriction, IndisponibiliteService, Administrateur
  config/           Sécurité JWT, audit des dates, Swagger
  scheduler/        Publication consentie et expiration
frontend/src/app/
  core/             Services HTTP, sessions, guards, interceptor, internationalisation
  features/         Accueil, espace voyageur et administration
  shared/           Modèles, recherche et cartes réutilisables
```

Backend monolithique Spring Boot 3.5.0, Spring Security/JWT, JPA, validation et exceptions centralisées.
Frontend Angular 21, Angular Material, Tailwind CSS et formulaires réactifs.
Les versions du `pom.xml` et du verrou npm sont conservées. Le diff local de `pom.xml`,
notamment l'artifactId `industri`, reste préservé hors commit.

## Prérequis

- Java/JDK **21**.
- Docker Desktop avec moteur Linux opérationnel.
- Node.js **24** et npm **11** pour les versions Angular présentes.
- Git. Maven est fourni par le wrapper du dépôt.

Commandes PowerShell, depuis la racine du projet :

```powershell
java -version
node --version
npm --version
docker version
docker info
git status
```

## Repères pour compiler et démarrer sous Windows

Ouvrir trois terminaux PowerShell dans le dossier contenant `pom.xml` et
`docker-compose.yml`. Adapter ce chemin à l'emplacement du dépôt :

```powershell
Set-Location 'C:\chemin\vers\GestionTransport-Multiple'
```

| Terminal | Dossier de travail | Commandes détaillées |
| --- | --- | --- |
| 1 — Docker / PostgreSQL | Racine du dépôt | [Démarrer et vérifier PostgreSQL](#1-postgresql-et-docker) |
| 2 — Backend Java 21 | Racine du dépôt | [Compiler, tester et démarrer Spring Boot](#3-compiler-et-démarrer-le-backend) |
| 3 — Frontend Angular | `frontend` | [Installer, compiler, tester et démarrer Angular](#4-installer-tester-et-lancer-angular) |

Arrêter une instance backend avant une reconstruction Maven et éviter une
compilation IntelliJ simultanée. Arrêter `ng serve` avec **Ctrl+C** avant `npm ci`
pour éviter le verrouillage d'esbuild sous Windows. Les commandes de compilation
ci-dessous ne démarrent pas les serveurs ; les commandes de lancement sont séparées.

## 1. PostgreSQL et Docker

Depuis la racine du dépôt, dans le terminal 1 :

```powershell
docker compose config --quiet
docker compose up -d postgres
docker compose ps
docker compose exec postgres pg_isready -U transport -d transportService
docker compose logs --tail=100 postgres
```

Le fichier Compose utilise l'image officielle `postgres:15`, sans Dockerfile
applicatif. Il démarre PostgreSQL ; le backend et le frontend sont compilés avec
Maven et npm dans les sections suivantes. Le contrôle `pg_isready` doit indiquer
que PostgreSQL accepte les connexions.

Configuration locale de développement :

```text
Base         : transportService
Utilisateur  : transport
Mot de passe : transport
Port hôte    : 5440
Port interne : 5432
Version      : PostgreSQL 15
```

Le conteneur `padel-db` et le volume historique `padel-db-data` gardent leurs identités
pour conserver les données. Changer le nom du volume créerait un autre stockage.
Les variables POSTGRES\_\* initialisent uniquement une base neuve : elles ne renomment
pas un rôle ou une base existante.

Les migrations locales déjà réalisées sont documentées dans
[Exécution Transport](docs/transport-execution.md), avec les scripts
[sauvegarde/renommage du rôle](scripts/migrate-dev-db-user.ps1) et
[renommage de la base](scripts/rename-dev-database.ps1).
Ne pas les rejouer sur une installation déjà migrée.

Vérifier le serveur sans écrire de données :

```powershell
docker exec padel-db pg_isready -U transport -d transportService
docker exec padel-db psql -U transport -d transportService -c "SELECT version();"
docker volume ls
```

**Ne pas supprimer le volume ni utiliser `docker compose down -v`.**
Pour arrêter les services en conservant le stockage : `docker compose stop`.

## 2. Données existantes et schéma

Le mécanisme initial `spring.jpa.hibernate.ddl-auto=update` est conservé.
Le garde de schéma refuse les tables historiques ou parallèles avant Hibernate.
Le lancement de validation utilise explicitement `ddl-auto=validate` sur une copie
déjà migrée. La copie de validation actuellement configurée est sur le port **5541** ;
le Compose principal ci-dessus expose la base métier sur **5440**.
La préparation de cette copie est décrite dans [l'audit SQL](docs/covoiturage/issue-3-modele-simple.md).
Aucun traitement ne remplace les anciens montants.

Exemple de sauvegarde PostgreSQL, sans redirection binaire PowerShell :

```powershell
New-Item -ItemType Directory -Force .local-validation/backups | Out-Null
$archiveTransport = "transportService_$(Get-Date -Format yyyyMMdd_HHmmss).dump"
docker exec padel-db pg_dump -U transport -Fc -f "/tmp/$archiveTransport" transportService
docker cp "padel-db:/tmp/$archiveTransport" ".local-validation/backups/$archiveTransport"
```

Le statut EXPIREE nécessite le script SQL
[issue-4-reservation-expiration.sql](scripts/migrations/issue-4-reservation-expiration.sql)
sur un schéma historique. Après sauvegarde et revue, appliquer explicitement à la base choisie :

```powershell
Get-Content -Raw -Encoding UTF8 scripts/migrations/issue-4-reservation-expiration.sql |
  docker exec -i padel-db psql -U transport -d transportService -X -v ON_ERROR_STOP=1
```

Ce script modifie uniquement le CHECK des statuts, sans réécrire de réservation ou de paiement.
Pendant la validation du projet, il a été appliqué uniquement à la base de test isolée.

Le script [invariants PostgreSQL](scripts/migrations/issue-7-invariants-covoiturage.sql)
ajoute les index de recherche/créneau, l'unicité d'une demande active et le contrôle
des villes, horaires et quatre places pour les trajets qualifiés. Même procédure
explicite après sauvegarde ; il refuse les doublons existants, sans les supprimer.
Les tests PostgreSQL actuels utilisent son équivalent sous les noms Transport :
[issue-3-invariants-transport.sql](scripts/migrations/issue-3-invariants-transport.sql).

Les scripts ci-dessus concernent uniquement l'ancien schéma : ne pas les rejouer
après renommage. Les mappings actuels utilisent les noms Transport ; la migration
explicite est détaillée dans [l'audit SQL](docs/covoiturage/issue-3-modele-simple.md).
Les catégories VIP/LOCAL utilisent encore les valeurs SQL GLOBAL/SITE via le convertisseur existant.
Les anciens trajets sans villes renseignées ne sont pas vendus comme trajets de covoiturage :
ils restent dans l'historique, en attente d'une qualification explicite.
Aucune ville, date de création ou nouvelle tarification n'est inventée pour ces anciennes lignes.

## 3. Compiler et démarrer le backend

Depuis la racine du dépôt, dans le terminal 2. Vérifier que Maven utilise Java 21 :

```powershell
java -version
.\mvnw.cmd -version
```

Si nécessaire, définir le JDK 21 installé avant de poursuivre, en adaptant le chemin :

```powershell
$env:JAVA_HOME = 'C:\chemin\vers\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -version
```

**Compilation seule** :

```powershell
.\mvnw.cmd -B compile
```

**Tests backend** :

```powershell
.\mvnw.cmd -B test
```

**Compilation, tests et génération du JAR** — commande de validation habituelle :

```powershell
.\mvnw.cmd -B verify
```

Pour reconstruire entièrement les fichiers générés, backend et compilation IDE arrêtés :

```powershell
.\mvnw.cmd -B clean verify
```

Le JAR est généré dans `target/`. Son nom dépend de l'`artifactId` du `pom.xml` ;
la valeur locale `industri` reste préservée. Les rapports de tests sont dans
`target/surefire-reports/`.

Sans `POSTGRES_BASELINE_URL`, les tests usuels utilisent H2 et les tests PostgreSQL opt-in sont ignorés.
La compilation ne prouve pas le démarrage HTTP : ce dernier fait l'objet des vérifications décrites plus bas.

**Démarrage Spring Boot**, après compilation : remplacer `<identifiant>` par
le nom d'une copie PostgreSQL restaurée et migrée existante sur le port 5541.
Créer une clé JWT dans l'environnement, sans l'afficher :

```powershell
$octetsJwtTransport = New-Object byte[] 48
$generateurTransport = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $generateurTransport.GetBytes($octetsJwtTransport) }
finally { $generateurTransport.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($octetsJwtTransport)
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5541/transport_schema_copy_<identifiant>'
$identifiantsPostgres = Get-Credential -UserName 'transport' -Message 'Identifiants PostgreSQL de la copie de validation'
$env:DB_USER = $identifiantsPostgres.UserName
$env:DB_PASSWORD = $identifiantsPostgres.GetNetworkCredential().Password
$env:DEMO_DATA_ENABLED = 'false'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
.\mvnw.cmd '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false' spring-boot:run
```

Laisser ce terminal ouvert. **Ctrl+C** arrête le backend.

- Backend : [http://127.0.0.1:8080](http://127.0.0.1:8080) — les endpoints sont sous `/api`.
- Swagger : [http://127.0.0.1:8080/swagger-ui.html](http://127.0.0.1:8080/swagger-ui.html).
- OpenAPI : [http://127.0.0.1:8080/v3/api-docs](http://127.0.0.1:8080/v3/api-docs).

Conserver `DEMO_DATA_ENABLED=false` sur toute base métier.
Remplacer `<identifiant>` par une copie restaurée/migrée existante. Ne pas
substituer transportService tant que son schéma ambigu n'a pas été régularisé
et migré après validation explicite ; le garde doit en refuser le démarrage.
Pour une présentation, privilégier la base de démonstration isolée décrite ci-dessous.

Dans IntelliJ, la configuration Spring Boot **GestionTransportApplication** doit
elle aussi recevoir `JWT_SECRET`. Une variable `$env:JWT_SECRET` définie dans un
terminal ne configure pas une instance IntelliJ déjà ouverte. Pour préparer cette
configuration locale sans afficher ni publier de secret :

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/configure-intellij-jwt.ps1
```

Le script ajoute une clé aléatoire uniquement si elle manque dans `.idea/workspace.xml`,
ignoré par Git, et préserve une clé déjà configurée. Relancer ensuite **GestionTransportApplication**.
Il ne modifie ni la base, ni les autres variables de lancement. L'erreur
`Could not resolve placeholder 'JWT_SECRET'` indique cette configuration manquante
au démarrage. Aucun secret par défaut n'est ajouté à `application.properties`.

| Variable                | Usage                                                         | Défaut                                 |
| ----------------------- | ------------------------------------------------------------- | -------------------------------------- |
| JWT_SECRET              | Clé JWT, au moins 32 octets, obligatoire                      | Aucun                                  |
| DB_URL                  | Connexion PostgreSQL                                          | localhost:5440/transportService        |
| DB_USER / DB_PASSWORD   | Identifiants du serveur                                       | transport / transport en développement |
| DEMO_DATA_ENABLED       | Activation explicite de la démonstration                      | false                                  |
| DEMO_PASSWORD           | Mot de passe démo, min. 12 caractères, max. 72 octets UTF-8 | Aucun                                  |
| RESERVATION_PAYMENT_TTL | Délai ISO-8601 de paiement                                    | PT30M                                  |
| RESTRICTION_DAYS        | Durée d'une restriction validée                               | 7                                      |
| SERVER_PORT             | Port HTTP Spring Boot                                         | 8080                                   |

## 4. Installer, tester et lancer Angular

Depuis la racine du dépôt, dans le terminal 3 :

```powershell
Set-Location frontend
npm ci
```

`npm ci` installe les versions du `package-lock.json` sans le remplacer.

**Compilation de production** :

```powershell
npm run build
```

**Tests frontend** — le script du projet exécute Vitest puis se termine :

```powershell
npm test
```

**Démarrage du serveur de développement**, backend déjà démarré :

```powershell
npm start -- --host 127.0.0.1 --port 4202
```

Laisser ce terminal ouvert. **Ctrl+C** arrête Angular. Pour revenir à la racine :
`Set-Location ..`.

- Interface : [http://127.0.0.1:4202](http://127.0.0.1:4202).
- Le port 4202 est utilisé pour laisser le port 4200 de l'autre application intact.
  Sur une autre machine où 4200 est libre, `npm start` utilise ce port par défaut.
- Le proxy existant transmet `/api` à `http://localhost:8080`.
- Build de production : `frontend/dist/frontend/browser`.
- L'interceptor JWT et les guards existants sont réutilisés.
- Les nouveaux textes de recherche, cartes et accueil utilisent l'internationalisation FR/EN existante.
- Références responsive : 375, 768 et 1440 px.

[Aperçu de l'accueil](docs/images/accueil-transport-demonstration.png), capturé sur une
base de démonstration isolée : ces données ne constituent pas une offre de trajets en production.

Aucune bibliothèque UI supplémentaire, carte géographique, géolocalisation ou intégration bancaire n'est ajoutée.

## 5. Démonstration sur base isolée vide

Le générateur existant est limité au **profil dev**, au flag explicite et à une base vide nommée
`transport_demo_<suffixe>` ou `issue1_validation_<suffixe>`. Il refuse `transportService`,
ne migre aucun compte et ne modifie aucune base déjà renseignée.

Créer une nouvelle base dédiée, sans supprimer un stockage existant :

```powershell
$baseDemoTransport = 'transport_demo_' + (Get-Date -Format yyyyMMddHHmmss)
docker exec padel-db createdb -U transport $baseDemoTransport
$env:DB_URL = "jdbc:postgresql://localhost:5440/$baseDemoTransport"
$env:DB_USER = 'transport'
$env:DB_PASSWORD = 'transport'
$env:DEMO_DATA_ENABLED = 'true'
$secretDemoTransport = Read-Host 'Mot de passe de démonstration (min. 12 caractères, max. 72 octets UTF-8)' -AsSecureString
$pointeurDemoTransport = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secretDemoTransport)
try { $env:DEMO_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointeurDemoTransport) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointeurDemoTransport) }
# Définir JWT_SECRET comme dans la section backend.
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Comptes créés uniquement dans cette base de démonstration :

| Espace | Profil / Rôle | Identifiant / Matricule | Mot de passe | Solde | Rôle & Particularité démonstration |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Voyageur** | Chauffeur autorisé | `V1001` | `Voyage1234!` | `0.0 €` | Propose et publie des trajets |
| **Voyageur** | Client VIP | `V1002` | `Voyage1234!` | `0.0 €` | Réservations prioritaires |
| **Voyageur** | Client local | `L10001` | `Voyage1234!` | `0.0 €` | Client rattaché à l'agence |
| **Voyageur** | Client libre | `F10001` | `Voyage1234!` | `0.0 €` | Voyageur standard tout public |
| **Voyageur** | **Client pénalisé** | `F10002` | `Voyage1234!` | **`100.0 €`** | **Cas concret pénalité** : solde impayé (4 × 25 €) + restriction 7 jours (bloqué à la réservation & création) |
| **Admin** | Administrateur global | `admin@transp.com` | `Admin1234!` | — | Supervision et gestion complète |
| **Admin** | Administrateur local 1 | `admin.lyon@transp.com` | `Admin1234!` | — | Gestionnaire d'agence Lyon |
| **Admin** | Administrateur local 2 | `admin.paris@transp.com` | `Admin1234!` | — | Gestionnaire d'agence Paris |

Lorsque `DEMO_PASSWORD` est fourni comme dans la commande ci-dessus, il remplace
les deux mots de passe de démonstration. Sans cette variable, les valeurs sont
`Admin1234!` pour les administrateurs et `Voyage1234!` pour les voyageurs ; elles
sont personnalisables avec `DEMO_ADMIN_PASSWORD` et `DEMO_VOYAGEUR_PASSWORD`.
Les neuf trajets de démonstration proposent chacun quatre places (dont le trajet privé
resté incomplet rattaché au voyageur pénalisé `F10002`). Ils ne sont jamais insérés automatiquement dans la base métier.

Parcours de présentation :

1. Se connecter comme client et rechercher Mons → Bruxelles.
2. Créer une demande, puis payer depuis « Mes réservations » : une place est confirmée.
3. Consulter les paiements et l'historique ; annuler une réservation autorisée.
4. Se connecter comme V1001 et proposer un trajet avec ses villes et son véhicule.
5. Créer un trajet privé, inviter des clients et choisir explicitement la publication H-24.
6. Se connecter comme administrateur, gérer les voyageurs autorisés à conduire,
   créer un trajet avec chauffeur sélectionné et consulter les statistiques du périmètre autorisé.

## 6. Tests PostgreSQL réels

Créer une base de test distincte de la base métier et de la démonstration :

```powershell
docker exec padel-db createdb -U transport issue1_validation_tests
$env:POSTGRES_BASELINE_URL = 'jdbc:postgresql://localhost:5440/issue1_validation_tests'
$env:DB_USER = 'transport'
$env:DB_PASSWORD = 'transport'
.\mvnw.cmd -B verify
Remove-Item Env:POSTGRES_BASELINE_URL
```

Les tests opt-in refusent une URL dont le nom ne suit pas `issue1_validation_<suffixe>`.
Ils ajoutent leurs fixtures sans vider la base après exécution. Une base neuve évite
les différences de contraintes d'un ancien schéma de test.

Couverture : chauffeur authentifié, refus sans chauffeur, villes différentes,
chauffeur exclu des places, quatre confirmations, cinquième refusée, concurrence
sur la dernière place, paiement 25 €, recettes 100 €, annulation/remboursement,
expiration, catégories J-21/J-14/J-5, agence locale, droits administrateurs,
accès privé, recherche, contraintes PostgreSQL et restrictions.

La CI GitHub ajoute PostgreSQL 15 isolé, Java 21, tests Maven, Node 24,
`npm ci`, tests Angular et build de production. Les résultats de CI doivent être
consultés sur la PR ; une configuration de workflow ne constitue pas une exécution réussie.

## 7. API REST et Swagger

- Swagger : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).
- OpenAPI : [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).
- Villes : `GET /api/trajets/villes`.
- Recherche : `GET /api/trajets/public?depart=MONS&arrivee=BRUXELLES&date=2026-12-01`.
  Adapter la date au jour de démonstration.
- Connexion voyageur : `POST /api/voyageurs/login`.
- Connexion administrateur : `POST /api/auth/login`.
- Création : `POST /api/trajets`.
- Demande : `POST /api/reservations`.
- Paiement simulé : `POST /api/paiements/reservation/{reservationId}/pay`.
- Historique : endpoints voyageur de réservations et paiements, protégés par JWT.

Erreurs de formulaire : 400 ; accès refusé : 403 ; ressource absente : 404 ;
conflit de persistance : 409. Les réponses ne présentent pas de détails SQL sensibles.

## 8. Traçabilité GitHub et documentation

Toutes les PR ciblent `develop`. Les branches fonctionnelles partent du dernier
`develop` validé. Aucune modification métier n'est committée dans `master`.

| Issue | Branche / travail                           | Pull request                                                                                                                           |
| ----- | ------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| #1    | chore/issue-1-audit-baseline                | [#8](https://github.com/Zoom225/GestionTransport-Multiple/pull/8)                                                                      |
| #1    | Nomenclature et paramètres locaux Transport | [#9](https://github.com/Zoom225/GestionTransport-Multiple/pull/9), [#12](https://github.com/Zoom225/GestionTransport-Multiple/pull/12) |
| #4    | Tarif 100 € / 25 €                          | [#10](https://github.com/Zoom225/GestionTransport-Multiple/pull/10)                                                                    |
| #2    | feature/issue-2-analyse-transport           | [#11](https://github.com/Zoom225/GestionTransport-Multiple/pull/11)                                                                    |
| #3    | feature/issue-3-domain-persistence          | [#13](https://github.com/Zoom225/GestionTransport-Multiple/pull/13)                                                                    |
| #4    | feature/issue-4-transport-services          | [#14](https://github.com/Zoom225/GestionTransport-Multiple/pull/14)                                                                    |
| #5    | feature/issue-5-transport-api               | [#15](https://github.com/Zoom225/GestionTransport-Multiple/pull/15)                                                                    |
| #6    | feature/issue-6-transport-frontend          | [#16](https://github.com/Zoom225/GestionTransport-Multiple/pull/16)                                                                    |
| #7 | feature/issue-7-integration-validation | [#17](https://github.com/Zoom225/GestionTransport-Multiple/pull/17) |
| #6 | feature/issue-6-ui-professionnelle | [#21](https://github.com/Zoom225/GestionTransport-Multiple/pull/21) |
| #4, #6, #7 | feature/issue-4-soutenance-finalisation | [#18](https://github.com/Zoom225/GestionTransport-Multiple/pull/18) |

Documents à lire pour présenter le projet :

- [Analyse métier et cas d'utilisation](docs/covoiturage/analyse-metier.md).
- [Modèle relationnel, diagrammes et migration](docs/covoiturage/modele-donnees.md).
- [Modèle JPA simple](docs/covoiturage/issue-3-modele-simple.md).
- [Réservation, paiement et concurrence](docs/covoiturage/issue-4-reservation-paiement.md).
- [API et sécurité](docs/covoiturage/issue-5-api-securite.md).
- [Interface et responsive](docs/covoiturage/issue-6-interface.md).
- [Validation finale et préservation des données](docs/covoiturage/issue-7-validation.md).
- [Audit initial historique](docs/audit-initial.md) et [exécutions de baseline](docs/baseline-execution.md).

## Candidat de soutenance — finalisation issues #4 et #7

Dernière validation : **155 tests backend dont 15 PostgreSQL**, **63 tests Angular**,
builds réussis et [CI réussie](https://github.com/Zoom225/GestionTransport-Multiple/actions/runs/43277688472).
Le parcours réel de réservation/paiement/remboursement et neuf contrôles responsive
à 375, 768 et 1440 px ont réussi sur une base de démonstration isolée.

Les archives locales sont conservées dans `.local-validation/`, hors Git et hors
`target` : `mvn clean` ne les supprime pas. Le diff local de `pom.xml` reste hors commit.

Les huit parcours de démonstration sont créés à J+2, de 07 h à 21 h, avec un seul
chauffeur et véhicule, 90 minutes par trajet et aucune réservation initiale : Mons,
Bruxelles, Charleroi, Namur, Liège, Anvers, Gand et Louvain. Six trajets sont publics,
deux privés. Un redémarrage sur une base non vide ne réécrit aucun compte ni paiement.
Le profil `dev`, `DEMO_DATA_ENABLED=true`, une base isolée nommée `transport_demo_*`
et `DEMO_PASSWORD` de 12 caractères minimum et 72 octets UTF-8 maximum (BCrypt)
sont tous requis. Aucun chargement en production.

### Restrictions : fonctionnement et décisions métier

Une restriction conserve son motif, sa période `[dateDebut, dateFin[` et une référence
unique de fait déclencheur. La date de début historique reste inconnue si elle n'était
pas enregistrée. Le verrou du voyageur et l'unicité SQL empêchent les doublons, y compris
en concurrence. Une restriction expirée cesse automatiquement de bloquer la réservation,
sans suppression de son historique. La durée de référence est `RESTRICTION_DAYS=7`.

| Variable | Valeur par défaut | Activation explicite |
| --- | --- | --- |
| LATE_CANCELLATION_ENABLED | false | Annulation par le client d'une réservation confirmée avant le départ |
| LATE_CANCELLATION_HOURS | 0 | Seuil positif à valider avant activation |
| NO_SHOW_PENALTY_ENABLED | false | Absence explicitement déclarée par un administrateur autorisé |
| DRIVER_CANCELLATION_PENALTY_ENABLED | false | Annulations de trajets par leur chauffeur |
| DRIVER_CANCELLATION_THRESHOLD | 0 | Nombre d'annulations à valider |
| DRIVER_CANCELLATION_WINDOW_DAYS | 0 | Fenêtre d'observation positive à valider |

Une politique activée sans seuil valide empêche le démarrage. Aucun montant de sanction
n'est ajouté. Une demande en attente ou expirée ne crée pas automatiquement une dette.
Un solde exigible positif bloque toujours réservation et paiement de confirmation.

- Administration des trajets : `POST /api/reservations/{id}/absence`, JSON
  `{ "motif": "Absence injustifiée vérifiée" }`. Réservation payée et confirmée,
  départ passé, trajet non annulé, administrateur du bon périmètre requis. La date,
  le motif et l'administrateur déclarant sont conservés ; une répétition ne les écrase pas.
- Administration des voyageurs : `GET /api/voyageurs/{id}/restrictions` retourne
  l'historique et l'état actif calculé, selon le périmètre d'agence de l'administrateur.
- Migration additive : [issue-4-restrictions-soutenance.sql](scripts/migrations/issue-4-restrictions-soutenance.sql).
  Revue et sauvegarde requises avant application sur une base existante ; aucun paiement réécrit.

### Démonstration devant le jury

Suivre les commandes Docker, backend et frontend des sections 1 à 6. Ouvrir Swagger,
puis rechercher Mons → Bruxelles dans Angular. Se connecter avec le client VIP de la
base isolée, réserver et payer **25 € simulés** : trois places restent disponibles.
Quatre paiements donnent **100 €** et aucune cinquième confirmation. Une annulation
autorisée conserve l'historique, rembourse le paiement simulé et libère la place.
L'administration présente les réservations, recettes et restrictions selon les droits.
Les commandes et preuves de validation figurent dans
[issue-7-validation.md](docs/covoiturage/issue-7-validation.md).

La CI contrôle désormais les PR vers `develop` **et `master`**. La livraison vers
`master` se fait par PR distincte après validation de `develop`, sans fusion automatique.

## Refonte UX/UI — issue #6

La [PR #21](https://github.com/Zoom225/GestionTransport-Multiple/pull/21), sur
`feature/issue-6-ui-professionnelle`, harmonise le frontend : thème marine/bleu/turquoise,
navigation mobile, pied de page commun, formulaires, cartes, espaces voyageur/chauffeur
et administration. **Voir le trajet** ouvre une fiche accessible avec les informations
déjà disponibles et la même action de réservation. Les services API, guards, interceptor,
contrats REST et règles backend sont conservés.

Les couleurs et composants communs sont centralisés dans `frontend/src/styles.scss`,
le header/footer dans `frontend/src/app/app.scss`. Les SVG sont locaux et la police
utilise Inter lorsqu'elle est installée, puis les polices système : aucune police
distante requise. Les nouveaux libellés, erreurs de session/réseau et statuts sont FR/EN.

Comparaisons avec réponses API de démonstration **simulées dans le navigateur** :

| Écran | Avant | Après |
| --- | --- | --- |
| Accueil ordinateur | [Avant](docs/images/ui/avant-accueil-1440.jpg) | [Après](docs/images/ui/apres-accueil-1440.jpg) |
| Accueil mobile | [Avant](docs/images/ui/avant-accueil-375.jpg) | [Après](docs/images/ui/apres-accueil-375.jpg) |
| Administration | [Avant](docs/images/ui/avant-dashboard-1440.jpg) | [Après](docs/images/ui/apres-dashboard-1440.jpg) |

Build et tests : `cd frontend`, `npm ci`, `npm run build`, `npm test`.
Validation de cette refonte : **69 tests Angular**, build réussi, **57 contrôles
responsive** et **20 analyses axe-core** sans violation signalée sur les règles
sélectionnées. Parcours de réservation/paiement vérifié avec des API simulées,
sans accès aux données PostgreSQL.
Le détail des vérifications, limites et commandes navigateur figure dans
[le compte rendu UI](docs/covoiturage/issue-6-interface.md).

## Dépannage

Pour le lancement IntelliJ actuel, utiliser **GestionTransportApplication** et
configurer une copie restaurée/migrée, sans écrire dans la base métier :

```powershell
$env:DB_PASSWORD='<mot de passe de la copie de validation>'
.\scripts\configure-intellij-validation.ps1 -Database transport_schema_copy_<identifiant>
```

Le script configure l'URL locale explicite, `ddl-auto=validate`, désactive la
démo et le redémarrage DevTools, et réutilise la clé JWT personnelle. Il refuse
la base métier. [Diagnostic détaillé](docs/covoiturage/issue-7-validation.md).
La configuration Maven partagée ci-dessous exige ses propres variables
d'environnement ; elle n'hérite pas de celles de la configuration Spring Boot.

- IntelliJ : utiliser la configuration partagée **GestionTransport-Multiple**
  (`.run/GestionTransport-Multiple.run.xml`), qui exécute `spring-boot:run` via Maven
  sans dépendre du nom local du module. Configurer les variables DB/JWT comme ci-dessus.
  Pour une configuration Spring Boot personnelle, la classe principale est
  `com.transport.gestiontransport.GestionTransportApplication` ; les tests se trouvent
  dans `com.transport.gestiontransport`. Après un renommage, vérifier ces deux champs
  dans **Run → Edit Configurations**, puis recharger le projet Maven.

- Sous Windows, arrêter une instance lancée avec `java -jar` avant de régénérer
  son JAR : le processus peut empêcher Maven de renommer le fichier pendant l'empaquetage.

- Docker inaccessible : vérifier `docker version`, `docker info`, `docker context ls`
  et `wsl --status`. Ne pas réinitialiser Docker ni supprimer ses volumes.
- Erreur de connexion PostgreSQL : vérifier le port 5440, les variables DB\_\* et l'état healthy.
- Backend arrêté : vérifier Java 21 et JWT_SECRET ; les tests unitaires seuls ne prouvent pas le démarrage.
- Recherche vide : seuls les trajets futurs, qualifiés avec deux villes et encore disponibles sont proposés.
- Création refusée : vérifier la permission chauffeur, le véhicule, l'agence, les horaires et chevauchements.
- Paiement refusé : actualiser la disponibilité, vérifier le délai, la catégorie, la zone et les restrictions.
- Ancien montant ou trajet non qualifié : régularisation métier explicite, sans réécriture automatique des paiements.

## Harmonisation PostgreSQL Transport — issue #3

Branche `refactor/issue-3-schema-transport`, distincte de l'interface premium
`feature/issue-6-admin-ui-premium` ([PR #22](https://github.com/Zoom225/GestionTransport-Multiple/pull/22)).
Les tables cibles sont agences, vehicules, voyageurs, trajets, reservations,
paiements, restrictions, indisponibilites_service et administrateurs.
Les renommages explicites préservent les données, identifiants, FK et montants.
[Audit, résultats, incident et procédure](docs/covoiturage/issue-3-modele-simple.md) ;
[diagrammes de classes et entité-association](docs/covoiturage/modele-donnees.md).

**Ne pas lancer Hibernate update sur la base historique pendant un changement
de mappings.** Arrêter Spring Boot/DevTools et la compilation automatique avant
de changer de branche. Le nouveau garde bloque le démarrage avant Hibernate
si les tables historiques sont présentes. La migration sur transportService
exige une autorisation distincte, après revue et restauration testée.

Un rechargement IntelliJ resté actif a créé des tables Transport vides sur la
base métier pendant ce chantier. Cette instance a été arrêtée ; aucune table
n'a été supprimée. Les empreintes des données historiques sont identiques à
la sauvegarde initiale. L'état ambigu est sauvegardé et refusé par le migrateur ;
sa régularisation n'est pas exécutée automatiquement.

```powershell
# Restaurer et migrer une copie depuis une sauvegarde connue, jamais la source :
.\scripts\valider-schema-transport.ps1 -BackupFile C:\chemin\sauvegarde.dump -DatabaseUser transport
# Vérifier les mappings sans génération implicite de tables :
$env:TRANSPORT_SCHEMA_URL='jdbc:postgresql://127.0.0.1:5541/transport_schema_copy_<identifiant>'
$env:DB_USER='transport'
$env:DB_PASSWORD='<mot de passe de validation>'
mvn -B -Dtest=TransportSchemaValidationTest test
```

Résultats du chantier SQL : `mvn -B verify` réussi, **160 tests backend sans
échec, erreur ou ignoré**, dont 15 tests PostgreSQL et deux tests JPA de la copie
restaurée en mode validate. Migration exécutée deux fois, empreintes des neuf
tables identiques ; contraintes supplémentaires validées sans réécriture des
données. Un vrai lancement du jar refuse le schéma historique avant Hibernate.
Le nouveau backend et le frontend premium ont aussi réussi ensemble le parcours
réel sur `transport_demo_premium_20261113181820` : GLOBAL/AGENCE, périmètre local,
réservation Angular, paiement25, confirmation, historique, annulation/remboursement,
quatre clients/100€, concurrence dernière place ; douze contrôles responsive réels
sans débordement et aucune erreur JS. Les dumps confidentiels restent hors Git.
## Finition premium et accès administrateur

**Finition Transport — issue #6**, branche `feature/issue-6-bannieres-agences-transport` :
illustration automobile originale, accueil et cartes de trajets illustrés,
bannière commune aux pages internes et menu d’administration compact sur mobile.
Les filtres affichent les noms fournis par l’API, sans remplacement fictif dans Angular.
Sur la copie locale de validation, les deux anciens noms sont devenus
**Agence Transport Élégant** et **Agence Transport Design**, avec les mêmes identifiants,
adresses et rattachements. Les villes historiques ne sont pas transformées en villes belges.
La base métier n’est pas renommée automatiquement.
Voir les [résultats et captures du chantier](docs/covoiturage/issue-6-interface.md#bannières-automobiles-et-noms-des-agences--14-novembre-2026).

Le header propose **Accès administrateur** avant connexion. Après connexion, le rôle affiche **Administration globale** ou **Administration de mon agence**, avec navigation dédiée responsive vers les pages existantes. Les recettes sont consultables au dashboard ; restrictions et absences dans la gestion des voyageurs.

Chantier issue #6, branche `feature/issue-6-admin-ui-premium`, complément de #21 non fusionnée. [Rapport et captures](docs/covoiturage/issue-6-interface.md). Build et 72 tests Angular réussis ; 15 tests PostgreSQL isolés réussis ; parcours réel GLOBAL/AGENCE et réservation/paiement Angular réussi sur `transport_demo_premium_20261112234317`. Les 57 contrôles visuels avec API simulées sont distincts de cette intégration réelle. Aucune migration sur la base métier.
