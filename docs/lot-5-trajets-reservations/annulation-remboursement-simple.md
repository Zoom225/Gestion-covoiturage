# Annulation et remboursement simulé — étape 1

Issue #50, liée à #26. Les règles validées pour cette étape remplacent la mention historique « aucun remboursement automatique » des lots précédents. Aucun droit d'annuler le trajet entier n'est ajouté.

## Règles et résultat visible

Le propriétaire authentifié appelle `POST /api/reservations/{id}/annulation` avant le départ. La réponse reste la réservation au statut `ANNULEE`. Au départ exact ou après, le serveur refuse avec HTTP 409 et `DEPARTURE_PASSED`. Une réservation appartenant à un autre client reste inaccessible.

| Réservation avant annulation | Temps restant avant le départ | Remboursement |
| --- | --- | --- |
| EN_ATTENTE | Avant le départ | Aucun paiement, aucun remboursement |
| CONFIRMEE | Au moins 24 heures, limite incluse | Simulation intégrale de 20 € |
| CONFIRMEE | Moins de 24 heures | Aucun remboursement |
| ANNULEE | Avant le départ | Même résultat, aucune nouvelle opération |

Les 24 heures sont une durée exacte entre deux instants, pas un changement de jour civil. La date provient de l'horloge du serveur, jamais de la requête.

`GET /api/paiements/me` conserve le paiement initial de 20 € et expose deux informations supplémentaires : `montantRembourse` (0 ou 20) et `rembourseLe` (null ou date UTC). Les paiements de dette ont toujours un montant remboursé de 0 et une date nulle. Le filtrage par propriétaire et la pagination restent appliqués. Une réservation sans paiement n'ajoute pas de ligne à cet historique.

## Parcours du code et des données

1. **Controller :** `TrajetController.cancelBooking` reçoit la demande et transmet l'identité issue du JWT à `ReservationService.cancel`.
2. **Service de réservation :** il vérifie le propriétaire et le départ, puis utilise les verrous `JourneyLocks` existants et relit la réservation. Il annule une seule fois la ligne historique.
3. **Service de paiement :** pour une réservation initialement confirmée, `PaymentService.refundSeat` applique la limite H-24, retrouve le paiement et appelle `PaymentGateway.refund`. Le simulateur existant autorise cette opération uniquement avec le profil `dev`, sans `prod`, et `driveconnect.payments.simulation-enabled=true`.
4. **Repositories :** `ReservationRepository` enregistre le statut ; `PaiementPlaceRepository` retrouve le paiement et permet sa consultation dans l'historique. JPA enregistre la date du remboursement au commit.
5. **PostgreSQL :** la migration additive V19 ajoute seulement `paiement_place.rembourse_le`. Sa valeur nulle signifie « aucun remboursement enregistré ». Le montant remboursé est déduit des 20 € initiaux, car seul le remboursement intégral existe. Les anciennes lignes restent inchangées, sans remboursement rétroactif.

`@Transactional` entoure l'annulation et le remboursement. La méthode de remboursement exige une transaction déjà active (`MANDATORY`). Une erreur de simulation remonte jusqu'à cette transaction : le statut de réservation, la capacité du trajet et les données du remboursement reviennent ensemble à leur état initial. Les traces du journal existant ne sont publiées qu'après un commit réussi.

Deux annulations simultanées utilisent les mêmes verrous que les paiements. La seconde relit une réservation déjà annulée et ne rappelle pas le simulateur. Une date déjà enregistrée n'est pas remplacée. La contrainte d'un paiement par réservation reste conservée.

Les protections V16/V17 restent en place : aucune réactivation de réservation annulée ; une réinscription crée une nouvelle ligne et nécessite un nouveau paiement. L'annulation d'une place confirmée libère la capacité selon le mécanisme existant. Un remboursement ne modifie aucune dette J-1. Le remboursement reste une simulation locale : aucun appel bancaire n'est effectué.

## Tests compréhensibles

`ReservationCancellationIntegrationTests` couvre les demandes HTTP réelles sur PostgreSQL Testcontainers : H-25, H-24 exact, H-23 ; réservation en attente ; refus au départ et après ; demandes répétées et simultanées ; conservation du paiement et de la dette ; nouveau paiement après réinscription ; refus d'annuler le trajet entier.

Le test d'échec remplit d'abord un trajet avec quatre confirmations, provoque une erreur du simulateur, puis vérifie que la réservation est toujours confirmée, le trajet toujours complet et le remboursement absent. Une nouvelle tentative réussit et libère la place.

`CancellationIntegrityIntegrationTests` vérifie aussi la migration V18 vers V19 avec un paiement et une dette historiques : ils sont conservés, aucun remboursement n'est inventé et relancer Flyway n'exécute pas à nouveau la migration. `SimulationGatewayTests` vérifie les conditions d'activation du remboursement simulé. Les tests existants de sécurité, réinscription, paiements et historique sont conservés.

Commande complète : `.\mvnw.cmd clean verify`. Les tests utilisent leurs conteneurs jetables ; aucune migration n'est exécutée sur une base existante par cette vérification.

Validation locale finale : **BUILD SUCCESS — 703 tests, 0 échec, 0 erreur, 0 test ignoré** sous Java 21 avec PostgreSQL 16.6 Testcontainers.

## Présentation au jury — environ 45 secondes

« Un participant peut annuler sa réservation avant le départ. Le contrôleur reçoit la demande et le service vérifie son identité et l'heure du trajet. Si la place était payée et qu'il reste au moins 24 heures, le service de paiement simule le remboursement intégral des 20 euros. Je conserve le paiement initial et j'ajoute seulement la date du remboursement. L'annulation et le remboursement partagent une transaction : si la simulation échoue, rien n'est validé. Les verrous empêchent de rembourser deux fois. Les tests vérifient les limites à 25, 24 et 23 heures, l'historique et le retour à l'état initial en cas d'erreur. Une nouvelle réservation nécessite un nouveau paiement et aucune dette existante n'est effacée. »
