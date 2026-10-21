# DriveConnect — Backend

Backend Java Spring Boot de la plateforme de gestion d’un réseau de covoiturage multi-zones.

## Prérequis

- Java 21
- PostgreSQL 16 ou compatible
- Docker Desktop (uniquement pour exécuter le test d’intégration Testcontainers)

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
| `DB_URL` | `jdbc:postgresql://localhost:5432/carshare` | URL JDBC PostgreSQL |
| `DB_USERNAME` | `carshare` | Utilisateur PostgreSQL |
| `DB_PASSWORD` | vide | Mot de passe PostgreSQL |
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
sur les push master/main/feature, les pull requests et sur déclenchement manuel.
Elle conserve les rapports Surefire. Les exécutions du lot 1 ont réussi sur la
PR #2 fusionnée. Le lot 2 doit encore être publié et vérifié en CI après validation.

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
