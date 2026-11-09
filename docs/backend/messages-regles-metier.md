# Messages des règles métier du backend

## Contrat d’erreur

Les erreurs REST utilisent le DTO `ApiErrorResponse` : `timestamp`, `status`, `code`, `message`, `path` et `validationErrors`. Les exceptions métier restent traitées par `GlobalExceptionHandler`; les refus de sécurité du filtre Spring passent par `ApiSecurityErrorHandler` et le même DTO. Les détails d’exceptions techniques ne sont pas renvoyés au client.

Les codes métier ci-dessous sont ceux actuellement exposés par les services. Les conflits de règles métier conservent le statut HTTP 409 déjà utilisé par l’API. Les codes 400, 401, 403, 404 et 503 correspondent respectivement aux erreurs de requête/validation, d’authentification, d’autorisation, de ressource et de disponibilité technique.

| Code métier | Message français | Statut HTTP | Service concerné |
|---|---|---:|---|
| INVALID_TIMEZONE | Choisissez un fuseau horaire reconnu, par exemple Europe/Brussels. | 400 | Zones (`ReferentielService`) |
| UNAVAILABILITY_TARGET_REQUIRED | Choisissez une seule cible pour l’indisponibilité : une zone ou un véhicule. | 400 | Indisponibilités (`ReferentielService`) |
| RESERVATION_NOT_CANCELLED | Le remboursement concerne uniquement une réservation annulée. | 409 | Paiements (`PaymentService`) |
| ACTIVE_DEBT | Une pénalité reste impayée. Réglez entièrement son solde dans « Paiements et dettes » avant de créer un trajet, réserver ou payer une place. | 409 | Réservations, trajets (`BookingPolicy`) |
| ALREADY_PAID | Cette réservation est déjà payée. Retrouvez son paiement dans « Paiements et dettes ». | 409 | Paiements (`PaymentService`) |
| BOOKING_HORIZON | Ce trajet n’est pas encore réservable pour votre catégorie. Les réservations ouvrent 30 jours avant le départ pour les VIP, 10 jours pour les clients locaux et 3 jours pour les occasionnels. | 409 | Réservations et trajets (`BookingPolicy`) |
| DEPARTURE_PASSED | Le départ de ce trajet est atteint. Vous ne pouvez plus réserver, payer ou annuler une place. | 409 | Réservations (`ReservationService`) |
| DUPLICATE_RESERVATION | Vous avez déjà une réservation active pour ce trajet. | 409 | Réservations (`ReservationService`) |
| IDEMPOTENCY_CONFLICT | Cette demande de paiement concerne une autre réservation, une autre dette ou un autre montant. Consultez vos paiements avant de réessayer. | 409 | Paiements (`PaymentService`) |
| INVALID_DEBT_AMOUNT | Le montant doit être positif, comporter au plus deux décimales et ne pas dépasser le solde restant. | 409 | Paiements (`PaymentService`) |
| INVALID_INTERVAL | La date de fin doit être strictement postérieure à la date de début. | 409 | Disponibilités (`AvailabilityService`, `ReferentielService`) |
| PENDING_RESERVATION_INCOMPATIBLE | La modification rendrait le trajet inaccessible à un participant ayant réservé. Aucune modification n’a été effectuée. | 409 | Trajets (`TrajetService`) |
| RESOURCE_CHANGED | La ressource a changé pendant l’opération. Actualisez les données puis réessayez. | 409 | Zones, véhicules et disponibilités (`ReferentielService`) |
| RESOURCE_HAS_TRIP | Cette indisponibilité ne peut pas être créée ou modifiée, car un trajet utilise déjà cette ressource sur la période choisie. | 409 | Disponibilités (`ReferentielService`) |
| RESOURCE_UNAVAILABLE | Ce véhicule ou cette zone n’est pas disponible pour la période sélectionnée. | 409 | Réservations, paiements et trajets (`AvailabilityService`) |
| RESERVATION_NOT_PENDING | Cette réservation n’est plus en attente et ne peut pas être réglée. | 409 | Paiements (`PaymentService`) |
| TRIP_ALREADY_STARTED | Vous ne pouvez pas modifier ce trajet après son départ. | 409 | Trajets (`BookingPolicy`) |
| TRIP_FULL | Les quatre places passagers de ce trajet sont déjà confirmées. Choisissez un autre trajet. | 409 | Réservations et paiements (`BookingPolicy`) |
| TRIP_HAS_CONFIRMED_RESERVATION | Une réservation confirmée empêche la modification du trajet. | 409 | Trajets (`BookingPolicy`) |
| TRIP_HAS_DEBT | Une dette liée au trajet empêche sa modification. | 409 | Trajets (`BookingPolicy`) |
| TRIP_HAS_PAYMENT | Un paiement de place historique empêche la modification, même après annulation ou remboursement. | 409 | Trajets (`TrajetService`) |
| TRIP_J1_ALREADY_PROCESSED | Le contrôle J-1 a déjà été effectué : le trajet ne peut plus être modifié. | 409 | Trajets (`BookingPolicy`) |
| TRIP_J1_DUE | La vérification de la veille du départ est déjà due pour ce trajet privé. Son départ ne peut plus être modifié. | 409 | Trajets (`BookingPolicy`) |
| TRIP_J1_VERIFICATION_REQUIRED | L’historique de ce trajet doit être vérifié par un administrateur. Contactez-le avant de modifier le trajet. | 409 | Trajets (`BookingPolicy`) |
| TRIP_LOCATIONS_LOCKED | Les lieux de départ et d’arrivée ne peuvent pas être modifiés tant qu’une réservation est en attente. Les horaires et le véhicule restent soumis aux autres règles. | 409 | Trajets (`TrajetService`) |
| TRIP_NOT_MODIFIABLE | Ce trajet n’est plus planifié et ne peut pas être modifié. | 409 | Trajets (`BookingPolicy`) |
| TRIP_NOT_OPEN | Ce trajet n’est plus ouvert aux réservations, car il est annulé, complet ou terminé. | 409 | Réservations et paiements (`BookingPolicy`) |
| UNAVAILABILITY_TARGET_IMMUTABLE | La cible (zone ou véhicule) d’une indisponibilité ne peut pas être modifiée. | 409 | Disponibilités (`ReferentielService`) |
| VEHICLE_CONFLICT | Ce véhicule est déjà affecté à un trajet incompatible. Un délai de 15 minutes doit être respecté entre deux trajets. | 409 | Trajets (`TrajetService`) |
| VEHICLE_INACTIVE | Ce véhicule est désactivé et ne peut pas être utilisé pour ce trajet. | 409 | Trajets (`TrajetService`) |
| VEHICLE_ZONE_MISMATCH | Ce véhicule ne peut pas être utilisé dans une autre zone que celle du trajet. | 409 | Trajets (`TrajetService`) |
| VIP_REQUIRED | Cette autorisation de zone ne peut être gérée que pour un client VIP. | 409 | Zones (`ReferentielService`) |
| ZONE_TIMEZONE_REQUIRED | Cette zone n’a pas de fuseau horaire configuré. Contactez un administrateur avant de réserver. | 409 | Réservations et trajets (`BookingPolicy`) |
| UNAUTHORIZED | Authentification requise ou invalide. Vérifiez vos identifiants ou reconnectez-vous. | 401 | Authentification et sécurité |
| FORBIDDEN | Vous ne disposez pas des autorisations nécessaires pour effectuer cette action. | 403 | Autorisation et périmètres |
| NOT_FOUND | Ressource introuvable. | 404 | Services exposant une ressource |
| VALIDATION_ERROR | Données invalides. | 400 | Validation des requêtes |
| INVALID_REQUEST | Message adapté au paramètre, au JSON ou aux données fournies. | 400 | Requête HTTP et comptes |
| INVALID_INVITATION | Cette invitation n’est plus valide ou a déjà été utilisée. Demandez une nouvelle invitation à votre administrateur. | 400 | Invitations et activation de compte |
| INVITATION_CONFLICT | Cette invitation ne peut pas être créée ou renouvelée dans l’état actuel du compte. | 409 | Invitations et comptes |
| ACCOUNT_CONFLICT | Cette opération entre en conflit avec des données existantes. Vérifiez les informations puis réessayez. | 409 | Comptes et persistance |
| PAYMENT_UNAVAILABLE | Le service de paiement est momentanément indisponible. Réessayez plus tard. | 503 | Paiements |
| DELIVERY_UNAVAILABLE | Le service d’envoi des invitations est momentanément indisponible. Réessayez plus tard. | 503 | Invitations |
| HTTP_ERROR | La requête ne peut pas être traitée avec cette méthode ou ce format. | Statut HTTP d’origine | Protocole HTTP |
| INTERNAL_ERROR | Une erreur interne est survenue. Réessayez plus tard. | 500 | Erreurs techniques inattendues |

Les refus d’authentification utilisent le même message générique pour un identifiant inconnu, un mot de passe erroné, un jeton invalide ou un compte inactif. Ils ne permettent pas de vérifier l’existence d’un compte. Les codes `TRIP_ARRIVAL_NOT_PASSED` et `PRIVATE_CANCELLATION_AVOIDS_J1_DEBT` correspondent à des gardes internes qui ne sont pas actuellement exposées par une route HTTP ; les traitements J-1 et de fin de trajet restent des tâches automatiques, sans message utilisateur ni effet métier ajouté pour cette documentation.

## Recommandations Angular

Le backend fournit `code`, `message` et `status` dans le même objet JSON. Angular
affiche désormais les messages backend pour les codes métier reconnus, aux
statuts 400 et 409. Les erreurs de validation indiquent jusqu'à trois corrections
de saisie. Les erreurs techniques 5xx et les codes inconnus gardent un message
générique sûr ; aucun détail de base de données ni trace d'exception n'est affiché.

Le départ atteint est distingué d'une réservation pas encore ouverte. Un fuseau
horaire inconnu produit `INVALID_TIMEZONE` (400) et propose Europe/Brussels comme
exemple ; une cible d'indisponibilité invalide produit
`UNAVAILABILITY_TARGET_REQUIRED` (400). `RESERVATION_NOT_CANCELLED` explique que
seules les réservations annulées peuvent faire l'objet d'un remboursement.

Une annulation avec pénalité active réussit **sans remboursement** : elle n'est
pas présentée comme une erreur d'annulation. `annuleAvecPenalite` est conservé et
l'historique expose `remboursementRefusePourPenalite`. Voir la
[règle actuelle](../regles-annulation-penalites.md).

Les paiements rejoués avec la même clé et les annulations déjà réalisées suivent leur comportement idempotent existant et restent des succès : ils ne produisent pas de message d’erreur.
