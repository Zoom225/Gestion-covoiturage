# CarShare Network — Backend

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

Flyway est configuré pour charger les migrations depuis `src/main/resources/db/migration`. Hibernate utilise `ddl-auto=validate` : il ne crée ni ne modifie le schéma. Aucune entité métier ni migration de schéma initiale n’est encore définie ; les migrations seront ajoutées avec le premier modèle de persistance.

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
