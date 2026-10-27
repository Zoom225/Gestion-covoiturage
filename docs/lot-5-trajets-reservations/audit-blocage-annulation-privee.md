# Issue #26 — préparation de la garde d'annulation privée

## Base vérifiée le 27 octobre 2026

- `master` : `20b3fc35ff75e15eef1d89764b3a7fbf234aa8e7`.
- PR #42 et #43 fusionnées ; migrations V16 et V17 présentes.
- CI de ce master réussie : https://github.com/Zoom225/Gestion-covoiturage/actions/runs/40472747022.
- Branche isolée : `feature/issue-26-blocage-annulation-privee`.
- Issue #26 lue intégralement ; elle reste ouverte.

Après cet audit, le responsable a validé l'option 1 : préparer uniquement une
garde métier réutilisable et testée, sans nouvel endpoint d'annulation de trajet.
Ce lot ne livre pas le parcours complet d'annulation des trajets.

## Parcours réellement disponibles

`TrajetController` expose la création et la consultation des trajets, les
réservations publiques, l'ajout de participants privés, les réservations
personnelles et `POST /api/reservations/{id}/annulation`.

Cette dernière route appelle `ReservationService.cancel` et annule uniquement
la réservation personnelle avant le départ. Elle conserve sa ligne, son paiement
et les dettes. Elle ne passe pas le trajet à `ANNULE` et n'empêche donc pas J-1.
Le test `cancellingOwnPrivateBookingBeforeJ1DoesNotAvoidOrganizerDebt` vérifie déjà
ce cas. Il ne faut pas lui appliquer le nouveau refus d'annulation de trajet.

Il n'existe ni route d'annulation de trajet, ni méthode correspondante dans
`TrajetService`, ni mutateur d'annulation dans l'entité `Trajet`, ni DTO de demande
d'annulation de trajet. `StatutTrajet.ANNULE` et le CHECK SQL existent ; certains
tests préparent cet état par SQL directement. Leur présence ne constitue pas
un parcours d'annulation ouvert à l'organisateur.

## Règles existantes à conserver

- `BookingPolicy.MAX_CONFIRMED` vaut quatre ; le compteur utilisé est
  `ReservationRepository.countByTrajetIdAndStatut(..., CONFIRMEE)`.
- `J1ProcessingService` considère les trajets `PRIVE` et `PLANIFIE` incomplets.
  La limite est le jour précédant la date de départ, à **00:15 Europe/Brussels**,
  borne incluse. Ce n'est pas un délai glissant de 24 heures.
- Le traitement convertit en `PUBLIC`, crée la dette existante de 80 EUR une
  seule fois par trajet, et conserve le rattrapage et l'idempotence.
- L'ordre partagé des verrous est zone, véhicule, trajet, client, avec relecture
  après attente. Paiement et traitement J-1 l'utilisent déjà.
- Les opérations contrôlées par `BookingPolicy.eligible` refusent une dette
  `ACTIVE`. `PaymentService.payDebt` laisse le blocage après paiement partiel et
  régularise au paiement intégral, sans nouveau montant ni pénalité.
- V16 conserve les réservations historiques et limite l'unicité aux lignes non
  annulées ; V17 conserve les paiements après annulation personnelle.
- `ClientAuthorization` vérifie le compte JWT et l'accès effectif. Une future
  commande de trajet doit aussi vérifier sa propriété sous verrou.
- `BusinessRuleException` est traduite en HTTP 409 avec un code métier fixe.
  Le gestionnaire actuel utilise un message générique : un éventuel message
  spécifique doit rester un texte public fixe, sans détail de persistance.

## Sources consultées

- Issue GitHub #26 et nouvelle décision explicite du responsable dans la mission.
- `docs/lot-4-zones-vehicules/decisions-metier-d1-d5.md` : D4 et D5 priment sur
  les propositions historiques antérieures.
- `docs/lot-5-trajets-reservations/cycle-de-vie-partiel.md` : absence volontaire
  de commande d'annulation de trajet pour éviter le contournement de J-1.
- `docs/recette-backend/rapport.md` : annulation de trajet, REALISE et
  remboursements encore partiellement définis.
- Le cahier externe référencé par README sous `../cahier des charge/` n'est
  pas présent dans cet environnement. Les documents de traçabilité du dépôt
  ont été consultés ; le contenu du cahier absent n'est pas présumé vérifié.

La nouvelle décision valide le refus d'une annulation privée incomplète avant
J-1 qui éviterait les 80 EUR. Elle ne définit pas, à elle seule, les annulations
autorisées pour les autres états et types de trajet.

## Garde préparée dans la politique existante

`BookingPolicy.requireCancellationPreservesJ1Debt` refuse avec le code fixe
`PRIVATE_CANCELLATION_AVOIDS_J1_DEBT` lorsque toutes les conditions suivantes
sont réunies : trajet `PRIVE`, statut `PLANIFIE`, instant strictement antérieur
à la limite J-1 existante, moins de quatre réservations `CONFIRMEE`, et aucune
dette déjà présente pour ce trajet. L'existence d'une dette est indépendante de
son statut ACTIVE ou REGULARISEE : cette garde ne demande pas une seconde dette.

La méthode ne fait que lire l'état et les compteurs. Elle ne modifie ni trajet,
ni réservation, ni paiement, ni dette. Elle réutilise la capacité existante et
le même calcul calendaire que J1ProcessingService, sans modifier ce traitement.
Elle ne crée aucun composant, endpoint, migration, pénalité ou remboursement.
Les prix et les contrôles existants de dette restent inchangés.

Le retour normal de cette méthode **n'autorise pas une annulation**. Un futur
service devra vérifier l'identité et la propriété de l'organisateur, prendre les
verrous JourneyLocks, relire les données et appeler la garde dans la même
transaction que son éventuelle mutation. Aucun parcours actuel ne l'appelle :
l'annulation personnelle de réservation est intégralement conservée.

## Décisions toujours nécessaires avant une nouvelle commande

L'absence de parcours existant ne permet pas de « conserver » un comportement
d'annulation des trajets publics ou privés complets. Avant une nouvelle commande,
il faudra définir : annulation directe ou demande, états sources, cas PUBLIC
et privé complet, limite temporelle, idempotence et devenir des réservations.
Les paiements et dettes devront rester conservés, sans remboursement automatique.

À l'instant exact de J-1 ou après celui-ci, un trajet peut être encore privé
si le traitement automatique attend un verrou ou n'a pas encore effectué son
rattrapage. Autoriser son annulation supprimerait sa candidature actuelle à J-1.
Le comportement de cette nouvelle commande doit donc être précisé pour ce cas,
sans avancer la dette ni créer de pénalité supplémentaire par supposition.

## Recette de cette préparation

`BookingCancellationPolicyTests` couvre les compteurs de zéro à quatre confirmés,
le refus sans mutation ni appel d'écriture, une nanoseconde avant la limite,
l'instant exact et les instants postérieurs, les autres types et statuts,
l'existence préalable d'une dette, les changements d'heure de printemps et
d'automne, la date locale différente de la date UTC et le fuseau métier fixe.

Les scénarios PostgreSQL Testcontainers existants restent inchangés : conversion
J-1, dette unique de 80 EUR, règlements 20 puis 60 EUR avec blocage puis
restauration, historique V16/V17, concurrence et sécurité JWT.

Les tests de concurrence d'une future annulation de trajet et ses tests HTTP
devront accompagner la définition de cette nouvelle commande. Ils ne sont pas
présentés comme livrés par cette garde. La validation complète demandée est
`./mvnw.cmd clean verify`, avant commit et PR vers master, sans fusion automatique.

REALISE, annulation globale des réservations et remboursements restent ouverts.

## Résultats de validation du 27 octobre 2026

- Tests unitaires ciblés par Maven Wrapper : 20 réussis, aucun échec, erreur ou ignoré.
- `mvnw.cmd --batch-mode --no-transfer-progress clean verify` : BUILD SUCCESS,
  **621 tests réussis**, aucun échec, aucune erreur, aucun test ignoré ; 16 min 44 s.
- Les 601 tests existants sont conservés, avec 20 nouveaux cas unitaires.
- Les tests PostgreSQL utilisent uniquement les conteneurs jetables existants.
- Aucun changement de J1ProcessingService, PaymentService, ReservationService,
  des contrôleurs ou des migrations V1 à V17 ; aucun nouvel endpoint ni migration.
- La validation GitHub Actions de la nouvelle branche sera suivie après publication.
