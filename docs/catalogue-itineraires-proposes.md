# Catalogue des itinéraires proposés

## Audit et architecture

Base : master `b165ba02cf8cd299e2c6a3bc9f095099e4daa4ce`, PR #45 fusionnée,
CI verte https://github.com/Zoom225/Gestion-covoiturage/actions/runs/40506523561.
Branche isolée : `feature/catalogue-itineraires-proposes`.

Le modèle existant conserve des libellés origine/destination libres de 255
caractères dans Trajet et CreateTrajetRequest. Aucun itinéraire prédéfini ni
jeu de routes de démonstration n'existait. Zone est un périmètre d'exploitation
portant droits, fuseau et flotte ; aucun code géographique ni lien ville-zone
ne permet d'affecter les dix propositions à des zones sans inventer de données.

V18 ajoute donc seulement `itineraire_propose` : identifiant, origine,
destination. Les couples sont directionnels et uniques sans casse, les lieux
sont non vides et sans espaces périphériques. Bruxelles → Mons et Mons →
Bruxelles restent distincts. Aucun lien fictif vers une zone, un organisateur,
un véhicule, une date ou une réservation n'est créé. V1 à V17 restent inchangées.

La consultation réutilise ClientReferentielController, ReferentielService,
ClientAuthorization, ReferencePageRequest et ReferencePageResponse. Le repository
est limité à la lecture ; aucune nouvelle couche de service, administration ou
écriture HTTP du catalogue n'est ajoutée.

## Références initiales

1. Bruxelles → Mons
2. Mons → Bruxelles
3. Charleroi → Nivelles
4. Nivelles → Charleroi
5. Zaventem → Mons
6. Bruxelles → Namur
7. Bruxelles → Charleroi
8. Bruxelles → Liège
9. Namur → Charleroi
10. Zaventem → Bruxelles

Ce sont des lieux proposés, pas dix trajets disponibles ou réservables.

## Contrat HTTP

`GET /api/itineraires?origine=brux&destination=mon&page=0&size=20`

- JWT CLIENT à accès effectif obligatoire : VIP, LOCAL et OCCASIONNEL.
- Références communes ; aucune zone ou flotte privée divulguée. Les routes de
  référentiels opérationnels et de création conservent leurs droits propres.
- Administrateurs GLOBAL et LOCAL : 403 sur cette API client ; JWT absent,
  invalide ou compte/client inactif : 401, selon les conventions existantes.
- Filtres facultatifs, espaces périphériques retirés, recherche partielle sans
  casse. Les accents restent significatifs. `%`, `_` et les apostrophes sont
  des saisies littérales, jamais des moyens d'élargir une recherche SQL.
- Filtres limités à 255 caractères ; pagination existante : page à partir de
  zéro, taille 1 à 100, valeur par défaut 20, décalage contrôlé. Erreurs 400 et
  messages français existants conservés.
- Recherche et comptage en SQL avant pagination ; ordre stable par identifiant.
- Réponse 200 avec `Cache-Control: no-store` et champs de page existants :

```json
{
  "content": [
    {"itineraireId": 1, "origine": "Bruxelles", "destination": "Mons"}
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`itineraireId` identifie une référence ; ce n'est jamais un identifiant de trajet
ou de réservation. Aucun autre verbe HTTP n'est exposé pour ce catalogue.

## Deux parcours, une création réelle

Angular peut sélectionner une référence et copier ses deux lieux dans le
formulaire, ou laisser l'utilisateur saisir ses propres libellés. Dans les deux
cas, il faut encore choisir une zone autorisée et un véhicule, saisir les dates
et le type puis appeler **l'API existante POST /api/trajets**. Aucun itineraireId
n'est requis ni ajouté au contrat de création.

TrajetController, TrajetService, CreateTrajetRequest, ZoneAccessPolicy,
AvailabilityService, BookingPolicy et les paiements ne sont pas modifiés.
L'organisateur vient du JWT ; zone, catégorie, dette ACTIVE, disponibilité,
activité et zone du véhicule, horizons et conflits de planification restent
contrôlés à la création. Une référence n'accorde aucun droit supplémentaire.
Seule une création explicitement acceptée crée le trajet et sa place logique
EN_ATTENTE ; aucun paiement ou confirmation n'est inventé.

## Validation et limites

Les tests ciblés couvrent normalisation, validations françaises, JWT et rôles,
trois catégories, recherche combinée, caractères LIKE littéraux, pagination,
doublons SQL, deux parcours de création et refus métier sans écriture.
Deux scénarios Flyway PostgreSQL vérifient l'absence totale de données
opérationnelles sur installation vide et la conservation de l'historique
financier lors du passage V17 → V18, avec migration idempotente.

Validation du 27 octobre 2026 :

- Tests ciblés par Maven Wrapper : **62 réussis**, zéro échec, erreur ou ignoré.
  Ils comprennent 53 nouveaux cas et les neuf tests de contexte existants.
- `mvnw.cmd --batch-mode --no-transfer-progress clean verify` : **BUILD SUCCESS,
  687 tests réussis**, zéro échec, zéro erreur, zéro ignoré ; 15 min 37 s.
- Comparaison par classe et nom des cas JUnit avec #45 : les **634 tests existants**
  sont tous conservés et réussis ; 47 nouveaux cas PostgreSQL/HTTP et six unitaires.
- L'assertion existante de version courante Flyway passe de 17 à 18 ; aucun test
  n'est supprimé ou désactivé. Aucun changement des fichiers V1 à V17.

Les tests utilisent seulement PostgreSQL 16.6 Testcontainers ; aucune base locale
existante, aucun service déjà lancé, aucun fichier Angular ni secret n'est modifié.
Aucun déploiement et aucune fusion automatique.

Hors périmètre : administration du catalogue, rattachement géographique à des
zones, géocodage, distances, durées, disponibilité et tarification par itinéraire.
Ces enrichissements nécessiteraient des règles et données explicites ; aucun
d'eux n'est nécessaire aux deux parcours demandés dans ce lot.

## Fichiers du lot

- Nouvelle migration : `V18__create_proposed_route_catalogue.sql`.
- Nouveau modèle : `ItinerairePropose` et `ItineraireProposeRepository` en lecture seule.
- Nouveaux DTO : `ItineraireSearchRequest` et `ItineraireProposeResponse`.
- Réutilisation : `ClientReferentielController`, `ReferentielService`,
  `SecurityConfiguration` (uniquement le nouveau GET réservé à CLIENT).
- Tests : `ItineraireSearchRequestTests`, `ItineraireCatalogueIntegrationTests`
  et adaptation de la version attendue dans `CovoiturageApplicationTests`.
- Documentation : ce fichier. Douze fichiers livrés, aucun changement Angular.
