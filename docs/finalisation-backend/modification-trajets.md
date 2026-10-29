# Modification contrôlée d'un trajet

`PUT /api/trajets/{id}` reçoit le JWT d'un CLIENT et un corps complet :

```json
{
  "vehiculeId": 2,
  "depart": "2026-11-03T09:00:00Z",
  "arrivee": "2026-11-03T10:00:00Z",
  "origine": "Bruxelles-Central",
  "destination": "Namur"
}
```

Seul l'organisateur peut modifier son trajet PLANIFIE, avant le départ.
La zone, le type, l'organisateur et les statuts ne sont jamais modifiés par cette demande.
Le véhicule doit être actif dans la zone du trajet. Les lieux restent des libellés :
le modèle ne permet pas de vérifier leur appartenance géométrique à une zone.

## Motifs de refus

- Utilisateur absent : 401 ; utilisateur non organisateur ou administrateur : 403.
- Trajet parti, ANNULE, REALISE ou COMPLET : 409.
- Au moins une réservation CONFIRMEE active : 409, organisateur inclus.
- Tout paiement de place lié au trajet : 409, même si sa réservation est annulée ou son montant remboursé.
- Contrôle J-1 effectué, vérification historique requise ou dette liée au trajet : 409.
- Pour un trajet privé, échéance J-1 initiale ou proposée déjà atteinte : 409.
  Cela empêche de reporter un contrôle manqué ou d'imposer une nouvelle échéance rétroactive.
  La borne reste la veille à 00 h 15 à Bruxelles, et non H-24.
- Droits de zone, dette ACTIVE de l'organisateur et horizon J-30/J-10/J-3 : règles existantes.
- Véhicule indisponible, chevauchement ou pause inférieure à quinze minutes : 409.
- DTO incomplet, lieu vide ou trop long, arrivée non postérieure au départ : 400.

Les réservations EN_ATTENTE conservent leurs identifiants et statuts.
Le service revérifie leur éligibilité existante (zone, activité, dette, horizon de réservation)
pour les nouveaux horaires. Une incompatibilité refuse toute la modification avec le code
`PENDING_RESERVATION_INCOMPATIBLE` et un message explicite. Aucune réservation n'est
automatiquement confirmée, annulée ou transférée.

Règle validée : dès qu'une réservation EN_ATTENTE active existe, les lieux sont figés,
y compris lorsque seule la place logique de l'organisateur est en attente. Le code
`TRIP_LOCATIONS_LOCKED` renvoie 409 avec un message explicite. Une valeur identique à celle
enregistrée, après la normalisation habituelle des espaces, ne constitue pas un changement.
Les réservations annulées ne bloquent pas ce contrôle. Les autres restrictions, notamment
les paiements historiques, continuent de s'appliquer. Les horaires et le véhicule restent
modifiables sous les conditions précédentes ; aucune réservation n'est annulée pour cela.

## Parcours dans le code

1. `TrajetController.update` reçoit et valide `UpdateTrajetRequest`.
2. `TrajetService.update`, avec `@Transactional`, vérifie l'identité, appelle
   `BookingPolicy.requireModifiable` et `eligible`, puis `AvailabilityService`.
3. `JourneyLocks` sérialise modification, réservation, paiement, J-1 et flotte :
   zone, ancien et nouveau véhicules dans l'ordre des identifiants, trajet, clients.
   Le trajet est relu après l'attente de la zone pour tenir compte d'un changement de véhicule.
4. `ReservationRepository` vérifie les confirmations et fournit les demandes en attente.
   `PaiementPlaceRepository` recherche aussi les paiements historiques.
   `TrajetRepository.overlapsExceptTrip` ignore le trajet lui-même et applique le tampon de 900 secondes.
5. Après tous les contrôles, l'entité `Trajet` change ses cinq champs autorisés.
   JPA enregistre les colonnes existantes dans PostgreSQL ; les contraintes SQL restent actives.
   Une erreur annule la transaction. La trace `TRAJET_MODIFIE` est publiée seulement après commit.

Il n'y a ni migration, ni nouvelle table, ni dépendance. J-1 et REALISE ne sont pas réécrits.
Les paiements, remboursements et dettes ne sont pas modifiés.

## Vérification

`TripUpdateIntegrationTests` utilise les JWT réels, les requêtes HTTP, PostgreSQL Testcontainers
et l'horloge de test déjà présents. Les tests couvrent les autorisations, états, bornes temporelles,
paiements annulés/remboursés, J-1, dettes, réservations en attente, disponibilité, chevauchement,
pause de 899/900 secondes, persistance et validation des saisies.
Des instantanés des trajets, réservations et données financières prouvent qu'un refus ne laisse
aucune modification. Deux tests concurrents vérifient la cohérence avec réservation et paiement.

Validation du 28 octobre 2026 : les 35 nouveaux cas passent ; `clean verify` termine avec
785 tests réussis, aucun échec, aucune erreur et aucun test ignoré. Le test Swagger existant
vérifie désormais le contrat PUT limité et maintient l'absence de suppression de trajet.

Commandes :

```powershell
.\mvnw.cmd '-Dtest=TripUpdateIntegrationTests,TrajetIntegrationTests,PaymentIntegrationTests' test
.\mvnw.cmd clean verify
```

## Présentation orale — environ 45 secondes

« L'organisateur peut modifier les horaires, le véhicule et les lieux d'un trajet qui n'est
pas encore parti. Le contrôleur reçoit la demande sécurisée par JWT. Le service vérifie
l'identité et les règles métier : aucune confirmation ni aucun paiement historique, pas de
contrôle J-1 déjà effectué et aucun contournement de dette. Il contrôle aussi les zones,
la disponibilité du véhicule et les quinze minutes entre deux trajets. Les demandes en attente
sont conservées et figent les lieux ; une incompatibilité fait refuser l'ensemble. Les repositories interrogent
PostgreSQL, puis la transaction enregistre les changements ensemble. Les mêmes verrous
empêchent une réservation ou un paiement simultané de contourner les contrôles. Les tests
HTTP et PostgreSQL vérifient les réussites, les refus et la conservation de l'historique. »
