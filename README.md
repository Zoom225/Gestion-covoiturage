# DriveConnect — Backend

Backend Java Spring Boot de la plateforme de gestion d’un réseau de covoiturage multi-zones.

## Démarrage local Windows — version récente

Le [guide de développement local](docs/developpement-local.md) décrit le nouveau
worktree, les fichiers privés, Docker Compose, IntelliJ et les vérifications Flyway.
Configurer la référence privée une seule fois ; ne pas écraser un fichier existant.

```powershell
.\scripts\sync-compose-env.ps1
docker compose up -d
.\scripts\dev-up.ps1 -Port 8081
```

Pour les tests : `.\scripts\dev-test.ps1`. Dans IntelliJ, ouvrir le nouveau
worktree et sélectionner la configuration partagée **DriveConnect — dev**
sur le port 8081. Swagger expose les GET `/api/admin/comptes` et
`/api/admin/comptes/{id}` ; leur utilisation reste protégée par JWT.

## Prérequis

- Java 21
- PostgreSQL 16 ou compatible
- Docker Desktop avec moteur Linux actif et Docker Compose v2 (tests Testcontainers et PostgreSQL de développement)

Le dépôt inclut Maven Wrapper : Maven n’a pas besoin d’être installé séparément.

## Configuration locale

Créez une base PostgreSQL vide nommée `carshare`, puis configurez les variables d’environnement avant de lancer l’application. Les valeurs par défaut de l’URL et du nom d’utilisateur ciblent une instance locale ; le mot de passe doit être fourni par l’environnement.

Avec un compte PostgreSQL administrateur, créez l’utilisateur et la base (en remplaçant le mot de passe d’exemple) :

```sql
CREATE ROLE carshare LOGIN PASSWORD '<mot-de-passe-local>';
CREATE DATABASE carshare OWNER carshare;
```

| Variable | Valeur par défaut | Description |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://127.0.0.1:15432/carshare` en dev ; `jdbc:postgresql://localhost:5432/carshare` hors dev | URL JDBC PostgreSQL |
| `DB_USERNAME` | `carshare` | Utilisateur PostgreSQL |
| `DB_PASSWORD` | obligatoire en dev ; vide hors dev | Mot de passe PostgreSQL |
| `JWT_SECRET` | aucune | Clé HS256 en Base64, au moins 32 octets aléatoires ; obligatoire |
| `JWT_ISSUER` | `driveconnect` | Émetteur attendu du JWT |
| `JWT_AUDIENCE` | `driveconnect-api` | Audience attendue du JWT |
| `JWT_TTL` | `PT15M` | Durée positive du jeton au format ISO-8601 ; choix technique configurable |
| `CORS_ALLOWED_ORIGINS` | vide | Origines web autorisées séparées par des virgules ; aucune par défaut |

> Définissez vos propres identifiants dans votre terminal ou dans le gestionnaire de secrets de votre environnement. Ne commitez pas de fichier contenant des secrets.

### PowerShell

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/carshare"
$env:DB_USERNAME = "carshare"
$env:DB_PASSWORD = "<mot-de-passe-local>"
# Générer une clé locale sans l'afficher ni la commiter.
$jwtBytes = New-Object byte[] 32
$jwtRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$jwtRandom.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:CORS_ALLOWED_ORIGINS = "http://localhost:4200"
```

### Bash

```bash
export DB_URL=jdbc:postgresql://localhost:5432/carshare
export DB_USERNAME=carshare
export DB_PASSWORD='<mot-de-passe-local>'
export JWT_SECRET="$(openssl rand -base64 32)"
export CORS_ALLOWED_ORIGINS=http://localhost:4200
```

Flyway est configuré pour charger les migrations depuis `src/main/resources/db/migration`. Hibernate utilise `ddl-auto=validate` : il ne crée ni ne modifie le schéma. V1 crée les référentiels `zone`, `client` et `vehicule` ; V2 ajoute `compte`, sans altérer V1. Les états des zones et véhicules seront ajoutés lorsque leurs valeurs seront définies ; aucune valeur arbitraire n’est imposée.

## Lancer le backend

Depuis la racine du dépôt :

```powershell
.\mvnw.cmd spring-boot:run
```

Sur Linux ou macOS :

```bash
./mvnw spring-boot:run
```

## Compiler et tester

Compilation :

```powershell
.\mvnw.cmd -DskipTests compile
```

Tests et vérifications Maven :

```powershell
.\mvnw.cmd verify
```

Pour reconstruire entièrement le projet et exécuter tous les tests :

```powershell
.\mvnw.cmd clean verify
```

Sur Linux ou macOS, remplacez `.\mvnw.cmd` par `./mvnw`. Le test d’intégration démarre PostgreSQL 16.6 avec Testcontainers et nécessite un daemon Docker actif.

## Fondations et avancement réel — issue #1

Stack conservée : Java 21, Spring Boot 4.1.1, Maven Wrapper 3.3.4
(distribution Maven 3.10.0), PostgreSQL 16.6, Flyway, Spring Data JPA,
Testcontainers 2.0.5. Les versions des dépendances restent gérées par Spring Boot.
Le premier téléchargement nécessite un accès à Maven Central et Docker Hub.

Référence consultée : `../cahier des charge/FIN-DriveConnect_Cahier_des_charges_FINAL_25P_DIAGRAMMES_UML_EA.docx`,
sections 7.1, 19 et 25, registre RM01–RM60. Ce document externe n’est pas
distribué dans le dépôt ; sa version de référence reste à versionner.

Réalisé : configuration PostgreSQL externalisée, Flyway comme propriétaire du
schéma, validation Hibernate, migration V1, entités et repositories des trois
premiers référentiels. Contraintes couvertes : catégories VIP/LOCAL/OCCASIONNEL
(RM04), rattachement obligatoire du client LOCAL (partie persistante de RM07),
clés étrangères (RM58) et numéro client unique (partie de RM59).
Le nom de zone n’est pas considéré comme un identifiant métier unique.

Les tests chargent le contexte complet avec PostgreSQL réel, valident et rejouent
Flyway sans nouvelle migration, vérifient les lectures/écritures JPA et le refus
des doublons, catégories inconnues, clients LOCAL sans zone et relations invalides.
Les écritures de tests sont annulées par transaction. Aucun test n’est ignoré
lorsque Docker manque : la vérification doit alors échouer.

La CI `.github/workflows/backend.yml` exécute `verify` sous Java 21 avec Docker
sur les push master/main/feature/chore, les pull requests et sur déclenchement manuel.
Elle conserve les rapports Surefire. Les exécutions du lot 1 ont réussi sur la
PR #2 fusionnée. La PR #4 du lot sécurité est également fusionnée et l'issue #3
est fermée. Le lot de configuration Swagger est suivi dans l'issue #5.

À implémenter dans les lots suivants : provisionnement des comptes, formats des numéros
Vxxxx/Cxxxx/Oxxxx (RM09–RM11), états des référentiels, indisponibilités, trajets,
réservations, paiements, dettes, scheduler et leurs API.
L'authentification du lot 2 suit Controller → Service → Repository → PostgreSQL.

Points à arbitrer avant les services concernés : heure de fin d’un trajet,
paiement des participants privés, annulation/remboursement/réinscription,
heure et fuseau du scheduler J-1, transitions REALISE et définition des zones
accessibles VIP/OCCASIONNEL. Les règles confirmées de capacité quatre,
paiement 20 € et dette 80 € ne sont pas encore implémentées.

## Authentification et sécurité — issue #3

`AuthController` délègue à `AuthService`, qui utilise `CompteRepository` et
`TokenService`. Les repositories/entités Client et Zone existants sont réutilisés.
Spring Security valide les JWT avec le support Resource Server déjà présent ;
aucune bibliothèque JWT ni filtre d'authentification supplémentaire n'est ajouté.
La structure suit le [support JWT officiel Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

| Endpoint | Accès | Résultat |
| --- | --- | --- |
| `POST /api/auth/login` | Public | JWT Bearer après vérification BCrypt et de l'état du compte/client |
| `GET /api/auth/me` | JWT valide et compte actif | Profil du compte authentifié uniquement |

Exemple de corps JSON de connexion :

```json
{"identifiant":"<identifiant-du-compte>","password":"<mot-de-passe>"}
```

La réponse contient `accessToken`, `tokenType` (`Bearer`) et `expiresIn` (secondes).
Utiliser ensuite `Authorization: Bearer <accessToken>` pour `/api/auth/me`.
Le profil contient `compteId`, `identifiant`, `role`, et, selon le compte,
`clientId`, `categorie`, `zoneClientId`, `perimetreAdmin`, `zoneAdminId`.
Aucun hash ni mot de passe n'est renvoyé. Les réponses sensibles portent `Cache-Control: no-store`.

Le JWT HS256 contient uniquement `sub` (id du compte), `iss`, `aud`, `iat`, `exp`
et `jti`. Signature, algorithme, émetteur, audience et dates sont contrôlés.
Il n'y a pas de session HTTP, de cookie de connexion ni d'authentification Basic.
La protection CSRF est désactivée pour cette API utilisant exclusivement le header
Bearer. CORS autorise uniquement les origines configurées, sans cookies.

À chaque requête, le serveur recharge le compte, son rôle, son rattachement et
l'état actif du client depuis PostgreSQL. Les rôles, zones et identifiants de
client fournis dans le JSON, les paramètres ou les claims JWT n'accordent aucun
droit. Les catégories VIP/LOCAL/OCCASIONNEL ne sont jamais des rôles Spring Security.
La désactivation ou suppression d'un compte invalide son accès dès la requête
suivante, même avec un jeton non expiré.

V2 impose un rôle technique CLIENT ou ADMIN, un lien client unique pour CLIENT,
et un périmètre GLOBAL ou LOCAL pour ADMIN, avec zone obligatoire pour LOCAL.
Un compte possède un seul rôle dans ce premier modèle ; aucun cumul de rôles ni
de profil administrateur/client n'est introduit. `AdminAuthorization.canManageZone`
centralise RM49/RM51 et peut être appelé par les services avec la zone réelle de la
ressource lue en base. Le préfixe `/api/admin/**` exige ADMIN ; aucun endpoint
administratif métier n'est encore livré. Les autres routes sont refusées par défaut.

Les erreurs de validation/JSON utilisent 400 ; identifiants incorrects, compte
indisponible, JWT absent/invalide/expiré utilisent 401 ; droits insuffisants utilisent
403. Les contrôleurs et la chaîne de sécurité utilisent le même format
`timestamp`, `status`, `code`, `message`, `path`, `validationErrors`.
Les erreurs ne reprennent ni valeurs rejetées sensibles ni détails techniques.
Une réponse de connexion invalide ne révèle pas si le compte existe.

Le login est un identifiant technique exact, sensible à la casse, distinct du
numéro métier du client. Aucun email ni format métier n'est supposé. Les mots de
passe sont limités à 72 octets UTF-8, limite BCrypt ; aucune politique de complexité
ou de renouvellement non spécifiée n'est ajoutée.

### Provisionnement et limites

Le parcours de création de compte, l'inscription publique, le choix email/numéro
de connexion et la politique de renouvellement des mots de passe restent à préciser.
Ce lot authentifie des comptes préprovisionnés par une procédure contrôlée côté
serveur. Aucun compte par défaut, endpoint d'inscription, bootstrap administrateur,
refresh token, mot de passe en clair ou secret de production n'est fourni.
Le hash doit être produit avec le bean `PasswordEncoder` BCrypt et inséré dans
`compte.password_hash` ; une chaîne en clair est rejetée par la contrainte SQL.
Les fixtures de test créent leurs comptes et hashes exclusivement dans PostgreSQL
éphémère. Le démarrage normal sur base vide est possible, mais aucune connexion
ne peut réussir tant qu'un compte n'a pas été provisionné.

La déconnexion consiste à supprimer le jeton côté client. Sans révocation par jeton,
une copie reste utilisable jusqu'à expiration, sauf désactivation du compte/client.
La clé doit rester stable entre redémarrages et être fournie hors dépôt ; la changer
invalide tous les jetons. HTTPS doit être assuré par l'environnement de déploiement.
Les contrôles de dette, de propriété des réservations et de périmètre des futures
ressources seront appliqués dans leurs services ; ils ne sont pas annoncés comme livrés.

Les tests génèrent leur propre clé aléatoire et ne nécessitent pas `JWT_SECRET`
dans le terminal ni dans GitHub Actions. Ils conservent les sept tests du lot 1 et
ajoutent les parcours HTTP complets, JWT, BCrypt, désactivation, rôles, périmètres,
CORS et contraintes du compte.

Vérification locale du 21 octobre 2026 : `.\mvnw.cmd verify` → **BUILD SUCCESS**,
29 tests, aucun échec, aucune erreur, aucun test ignoré : 8 tests de persistance
(les 7 du lot 1 et la montée V1 → V2 avec conservation des données), 18 tests
d'intégration de sécurité par HTTP et 3 tests de configuration JWT.
Java 21.0.9, PostgreSQL 16.6 via Testcontainers. V1 et les dépendances Maven sont
inchangées. Cette vérification locale ne constitue pas une exécution CI du lot 2.

Traçabilité du lot : RM01 (authentification), RM02 (rôles), RM03 (profil propre),
RM04 (catégories distinctes), RM13 (état actif pour l'accès authentifié), RM49/RM51
(garde de périmètre administrative testée, à intégrer aux futurs services),
RM58/RM59 (relations et unicité des comptes). Les fonctions métier de création et
réservation restent à développer ; leur conformité à ces règles n'est pas affirmée.
Acquis concernés : AA3/AA4 (modèle et intégrité), AA5 (couches), AA6 (sécurité),
AA7 (API de connexion/profil), AA9 (validation et erreurs), AA10 (tests), AA11
(documentation), avec une couverture limitée au périmètre de ce lot.

## Configuration Spring Boot et Swagger — issue #5

Java 21, Spring Boot 4.1.1 et Maven 3.10.0 via le wrapper existant sont conservés.
La seule dépendance directe ajoutée est
`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`. La version stable 3.1.1
est annoncée dans la [documentation officielle](https://springdoc.org/) ; son
[POM officiel](https://github.com/springdoc/springdoc-openapi/blob/v3.1.1/pom.xml)
utilise Spring Boot 4.1.0. La compatibilité avec Spring Boot 4.1.1 est vérifiée
par les tests de ce dépôt, sans changer la version de Spring Boot.

`OpenApiConfiguration` fournit le titre **DriveConnect — API REST**, la description
de la plateforme et la version issue de `pom.xml`, injectée par le filtrage Maven
déjà configuré par le parent Spring Boot. Le schéma HTTP Bearer JWT s'appelle
`bearerJwt`. Les réponses et erreurs des deux endpoints existants sont documentées
en français ; le principal Spring Security et les entités de persistance ne sont
pas exposés comme paramètres de requête.

### Profils et exposition

| Profils actifs | Documentation HTTP | Politique de sécurité |
| --- | --- | --- |
| Aucun | Désactivée | Règles JWT habituelles |
| `dev` | Activée | GET Swagger/OpenAPI public ; API métier protégée |
| `prod` | Désactivée | Aucune exception publique pour Swagger |
| `dev,prod` | Désactivée | La politique de production prévaut |

Le profil `dev` lie le serveur à `127.0.0.1`. Il n'est pas activé automatiquement.
Les paramètres PostgreSQL et JWT restent externalisés pour tous les profils ;
aucun mot de passe ni clé de signature de secours n'est ajouté. Les migrations
V1/V2 restent inchangées. Hibernate valide le schéma créé par Flyway.

Le choix retenu pour la production est de désactiver Swagger UI et OpenAPI.
Une future exposition en production nécessitera une politique d'accès dédiée
avant modification. Le simple ajout d'un profil de documentation ne doit pas
rendre les endpoints métier publics.

### PostgreSQL de développement reproductible

Une instance PostgreSQL déjà installée reste utilisable avec les variables de la
section « Configuration locale ». Sinon, le fichier `compose.yaml` fournit la
même version PostgreSQL 16.6 que les tests. Il publie uniquement
`127.0.0.1:15432`, utilise la base `carshare`, vérifie sa disponibilité et conserve
les données dans un volume Docker. Le mot de passe doit être fourni au terminal.

Après avoir défini `DB_PASSWORD`, `JWT_SECRET` et éventuellement `DB_USERNAME`
comme indiqué plus haut :

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:15432/carshare"
docker compose up -d --wait
docker compose exec -T postgres pg_isready -U carshare -d carshare
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Si `DB_USERNAME` a été personnalisé, adapter l'argument `-U`. Sur Bash :

```bash
export DB_URL=jdbc:postgresql://localhost:15432/carshare
docker compose up -d --wait
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Autre possibilité : définir `SPRING_PROFILES_ACTIVE=dev` dans le terminal puis
exécuter directement `.\mvnw.cmd spring-boot:run`. Ne pas activer `dev` en production.
Arrêter le backend avec Ctrl+C et PostgreSQL avec `docker compose down` ; le volume
de données est conservé. Réutiliser les mêmes identifiants aux démarrages suivants.

### URLs et utilisation de Swagger

- Backend local : <http://localhost:8080> ; la racine n'est pas un endpoint public.
- Interface : <http://localhost:8080/swagger-ui/index.html>.
- Spécification JSON : <http://localhost:8080/v3/api-docs>.
- Configuration de l'interface : <http://localhost:8080/v3/api-docs/swagger-config>.

Dans Swagger, exécuter `POST /api/auth/login` avec un compte préprovisionné.
Copier uniquement la valeur `accessToken`, ouvrir **Authorize**, coller le jeton
dans `bearerJwt` sans préfixe `Bearer`, puis valider. Exécuter `GET /api/auth/me` :
l'interface ajoute le header `Authorization: Bearer <jeton>`. La connexion reste
publique dans la spécification ; le profil courant exige le schéma Bearer.
La conservation du jeton après rechargement de Swagger est désactivée.

Ce lot ne crée ni compte de démonstration ni endpoint d'inscription. Sur une base
vide, la documentation est accessible mais une connexion valide nécessite toujours
le provisionnement sécurisé d'un compte décrit dans la section sécurité.

### Vérifications et dépannage

Les tests ajoutés contrôlent le JSON OpenAPI, le schéma Bearer, les opérations,
les ressources Swagger, les refus sans JWT et l'absence de documentation en
production, y compris avec `dev,prod`. Les tests de sécurité existants sont
conservés et exécutés avec `dev` pour vérifier leur coexistence avec Swagger.
PostgreSQL reste réel via Testcontainers ; aucun test n'utilise H2.

Vérifications effectuées le 21 octobre 2026 sous Windows / Java 21.0.9 :

- `.\mvnw.cmd clean verify` : **BUILD SUCCESS**, **36 tests**, aucun échec,
  aucune erreur et aucun test ignoré. Répartition : 9 tests de contexte/persistance,
  22 tests HTTP de sécurité/documentation en `dev`, 3 tests de configuration JWT
  et 2 tests de désactivation en production. Les 29 tests précédents sont conservés.
- `.\mvnw.cmd spring-boot:run` avec `SPRING_PROFILES_ACTIVE=dev` et PostgreSQL
  Compose sur le port 15432 : démarrage réel réussi sur le port 8080.
- `pg_isready` : PostgreSQL accepte les connexions ; l'historique Flyway contient
  V1 et V2 avec `success=true`. Hibernate a validé les mappings.
- GET `/swagger-ui/index.html`, `/v3/api-docs` et `/v3/api-docs/swagger-config` :
  **200**. Le JSON généré est parsé et ses métadonnées, opérations et schéma Bearer
  sont vérifiés. Les ressources JavaScript locales répondent également **200**.
- GET `/api/auth/me` sans JWT : **401** ; connexion avec JSON malformé : **400** ;
  connexion avec identifiants invalides : **401**. Les tests valident également
  l'accès au profil avec un JWT obtenu par connexion et les autorisations 403.
- Compose sans `DB_PASSWORD` : refus explicite de la configuration, sans valeur
  de secours. L'environnement manuel de vérification utilise un projet Compose
  temporaire distinct de l'environnement de développement et des secrets éphémères.

Cas vérifiés et corrections :

| Symptôme | Cause et action |
| --- | --- |
| Swagger retourne 401 hors `dev` | Politique volontaire. Activer uniquement `dev` pour un usage local ; la combinaison `dev,prod` laisse la documentation désactivée. |
| `/api/auth/me` retourne 401 | Jeton absent, invalide, expiré ou compte indisponible. Se connecter avec un compte actif et renseigner le JWT via Authorize. |
| Connexion retourne 400 | Corps JSON malformé ou champs invalides. Respecter le schéma affiché dans Swagger. |
| Compose réclame `DB_PASSWORD` | Définir le mot de passe dans le terminal avant `docker compose up -d --wait`. |
| Configuration JWT refusée | Les tests confirment le refus d'une clé absente, mal formée ou trop courte. Générer au moins 32 octets aléatoires, les encoder en Base64 et définir `JWT_SECRET`. |

La clé JWT et le mot de passe PostgreSQL doivent être conservés dans un gestionnaire
de secrets pour un environnement durable. Une base déjà initialisée dans le volume
Compose conserve ses identifiants ; modifier seulement la variable du terminal ne
modifie pas le mot de passe de ce compte PostgreSQL.

Les AA concernés sont AA5 (configuration et couches), AA6 (sécurité conservée),
AA7 (contrat REST), AA10 (tests et reproductibilité) et AA11 (documentation).
Ce lot ne livre aucune nouvelle règle métier, aucun module clients/zones et aucun
compte de démonstration. Aucun commit, push ni pull request n'est effectué avant
validation. Les résultats ci-dessus sont locaux ; la CI de ce lot reste à exécuter
après sa publication autorisée.

## Lot 1 — numérotation des clients

La migration additive V3 et le générateur transactionnel V/C/O sont décrits dans le [dossier du lot](docs/gestion-comptes/lot-1-numerotation.md). V1/V2, les anciens logins, JWT et BCrypt sont conservés. Les passages précédents décrivent les lots antérieurs ; la nouvelle connexion e-mail ADMIN / matricule CLIENT reste à réaliser. Aucun compte de démonstration ni amorçage au démarrage.

## Lot 2 — connexion e-mail administrateur et matricule client

La [connexion DEC-COMPTES-003](docs/gestion-comptes/lot-2-authentification.md), issue #11, conserve login/me, BCrypt et JWT. V4 garantit l'unicité sans casse et la cohérence du matricule, sans convertir les anciens comptes. Les passages précédents décrivent les lots antérieurs. Aucun compte de démonstration ni migration de la base locale dans cette validation.

## Lot 3B — consultation administrative des comptes

Les deux GET /api/admin/comptes et /api/admin/comptes/{id} sont décrits dans les [contrats courants](docs/gestion-comptes/contrats-api.md#5-consultation-administrative--implémentée-lot-3b), avec GLOBAL réseau, LOCAL limité aux CLIENT de sa zone, filtres avant pagination/count et 404 uniforme absent/invisible. Les sections des lots précédents restent historiques. Le [dossier TFE du lot 3B](docs/gestion-comptes/lot-3b-consultation.md) précise architecture, modèle conservé, sécurité, tests et couverture partielle AA1–AA11. Aucune création de compte, invitation, mutation ou migration nouvelle.

## Lot 3C.1 — socle des comptes invités

Le [dossier du lot 3C.1](docs/gestion-comptes/lot-3c1-comptes-invites.md), issue #19,
décrit DEC-COMPTES-004 : INVITE sans mot de passe, INITIALISE avec hash BCrypt.
V5 conserve les anciens comptes initialisés ; les activités compte/client restent indépendantes.
Login et JWT refusent INVITE. Le DTO administratif expose seulement l'état non sensible.
Aucun jeton d'invitation, envoi d'e-mail, endpoint de création ou compte fictif n'est ajouté.
Les preuves PostgreSQL/HTTP et limites AA figurent dans le dossier du lot.
V5 est testée dans Testcontainers ; la base de développement n'est pas migrée pendant cette livraison.
## Lot 3C.2 — jetons d'invitation internes

Le [dossier du lot 3C.2](docs/gestion-comptes/lot-3c2-invitations.md), issue #21, décrit
V6 et InvitationService : SecureRandom 32 octets, SHA-256 seul en base, expiration configurable,
révocation, renouvellement et consommation atomique PostgreSQL. Aucun endpoint, SMTP ou création de compte.
INVITATION_TTL propose PT24H sous dev seul ; hors dev, la durée doit être fournie avant émission.
Le futur parcours devra consommer la preuve et établir BCrypt dans la même transaction.
Aucune V6 appliquée à la base locale partagée ; les tests utilisent Testcontainers.

## Préparation du parcours complet des comptes — issue #23

Le [dossier de préparation](docs/gestion-comptes/parcours-invitations-preparation.md) distingue
le socle interne réellement implémenté (acceptation atomique BCrypt et outbox AES-GCM) des
endpoints de création/acceptation, du bootstrap et d'Angular, encore non livrés.
Les arbitrages P05/P07/P08/P09 restent ouverts ; aucune permission n'est inventée.
V7 concerne seulement l'outbox et n'est pas appliquée à la base partagée.
Le worker SMTP reste désactivé par défaut ; aucun envoi réel n'est effectué par les tests.

## Création et activation des comptes — PR #24

DEC-COMPTES-006 valide P05/P07/P08/P09 pour les créations administratives et mots de passe par invitation. Les quatre nouveaux POST, le renvoi sécurisé, V8 additive et le bootstrap opérateur sont décrits dans [le dossier de réalisation](docs/gestion-comptes/parcours-invitations-preparation.md). Les instantanés des lots précédents sont historiques. Aucun compte créé au démarrage, aucune migration exécutée sur la base partagée pendant les tests ; SMTP simulé et PostgreSQL Testcontainers.

## Lot 4 — consultation des référentiels

Le [socle du lot 4](docs/lot-4-zones-vehicules/consultation.md), lié à l’issue #25, ajoute six GET sécurisés pour zones, véhicules et périodes d’indisponibilité. Le périmètre GLOBAL/LOCAL est appliqué en SQL avant pagination et comptage. V9 est additive ; aucune mutation, suppression logique ou règle de trajet non validée n’est exposée. Les lots 5/6 attendent la stabilisation du modèle et des contrats.


## Zones et acces client - lot 4

Contrats courants, decisions D1-D5, limites et tests : [dossier lot 4](docs/lot-4-zones-vehicules/decisions-metier-d1-d5.md). V10 additive ; aucune migration appliquee a la base partagee. Les mutations vehicules/indisponibilites restent fermees faute de droits definis.


## Trajets et reservations - lot 5

[Contrats, modele, regles et limites](docs/lot-5-trajets-reservations/implementation.md). Reservations EN_ATTENTE uniquement ; confirmation reservee au paiement. Ne pas lancer Flyway contre la base partagee sans autorisation.
