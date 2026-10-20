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

> Définissez vos propres identifiants dans votre terminal ou dans le gestionnaire de secrets de votre environnement. Ne commitez pas de fichier contenant des secrets.

### PowerShell

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/carshare"
$env:DB_USERNAME = "carshare"
$env:DB_PASSWORD = "<mot-de-passe-local>"
```

### Bash

```bash
export DB_URL=jdbc:postgresql://localhost:5432/carshare
export DB_USERNAME=carshare
export DB_PASSWORD='<mot-de-passe-local>'
```

Flyway est configuré pour charger les migrations depuis `src/main/resources/db/migration`. Hibernate utilise `ddl-auto=validate` : il ne crée ni ne modifie le schéma. La migration `V1__create_referentials.sql` crée les premiers référentiels `zone`, `client` et `vehicule`. Les états des zones et véhicules seront ajoutés lorsque leurs valeurs seront définies ; aucune valeur arbitraire n’est imposée.

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
Elle conserve les rapports Surefire. Son exécution distante reste à vérifier après
publication de la branche.

À implémenter dans les lots suivants : comptes et JWT/BCrypt, formats des numéros
Vxxxx/Cxxxx/Oxxxx (RM09–RM11), états des référentiels, indisponibilités, trajets,
réservations, paiements, dettes, scheduler et API. Les dépendances de sécurité
présentes ne constituent pas une authentification JWT opérationnelle.
Les futures requêtes suivront Controller → Service → Repository → PostgreSQL ;
aucun endpoint métier ni service vide n’est ajouté dans ce lot de persistance.

Points à arbitrer avant les services concernés : heure de fin d’un trajet,
paiement des participants privés, annulation/remboursement/réinscription,
heure et fuseau du scheduler J-1, transitions REALISE et définition des zones
accessibles VIP/OCCASIONNEL. Les règles confirmées de capacité quatre,
paiement 20 € et dette 80 € ne sont pas encore implémentées.
