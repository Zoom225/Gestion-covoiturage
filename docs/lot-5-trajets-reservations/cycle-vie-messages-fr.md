# Issue #26 — cycle de vie, messages français et JavaDoc

## Audit du 27 octobre 2026

Base : master `200f3c765e16761a93521e30c008dd71dba2f6a5`, PR #42, #43 et #44
fusionnées, migrations V1 à V17 présentes. La CI de master est verte :
https://github.com/Zoom225/Gestion-covoiturage/actions/runs/40499874756.

L'issue #26 a été lue intégralement. Ses paragraphes historiques sur les obstacles
V16/V17 ne décrivent plus l'état courant : la réinscription et l'annulation
personnelle avec conservation du paiement sont livrées. La garde de #44 reste
une préparation, sans commande d'annulation de trajet.

Sources disponibles : décisions D1–D5, préparation et implémentation des lots 5
et 6, recette backend, documentation V16/V17 et de #44. Le cahier externe référencé
dans README sous `../cahier des charge/` est absent de cet environnement ; son
contenu n'est pas présenté comme vérifié. Les décisions explicites du responsable
et les documents de traçabilité du dépôt constituent les sources disponibles.

## Tableau des règles confirmées et décisions restantes

| Scénario / état initial | Acteur | Temps / conditions | État final du trajet | Réservations | Paiements | Dettes | HTTP | Justification / décision |
|---|---|---|---|---|---|---|---|---|
| Création | CLIENT authentifié | Horizon VIP J-30, LOCAL J-10 dans sa zone, OCCASIONNEL J-3 ; véhicule disponible ; aucune dette ACTIVE | PLANIFIE, type demandé | Place logique organisateur EN_ATTENTE | Aucun paiement fictif | Inchangées | 201 ; contrôles 401/403/409 | RM05–08, RM14, D2–D4 ; confirmé |
| Réservation / ajout privé | Client personnel / organisateur pour l'ajout privé | Éligibilité, trajet ouvert, absence de doublon actif | Inchangé | Nouvelle EN_ATTENTE | Aucun | Restriction ACTIVE conservée pour le client concerné | 201 / 409 | RM18, RM24, RM36–38, V16 ; confirmé |
| Confirmation / PLANIFIE | Propriétaire authentifié de la réservation | Paiement validé de 20 EUR, éligibilité recontrôlée, moins de quatre confirmés | COMPLET au quatrième confirmé | EN_ATTENTE vers CONFIRMEE | Preuve de 20 EUR conservée | Inchangées | 200 / 409 ; 503 si prestataire indisponible | RM27, RM40, D4 ; confirmé |
| J-1 / PRIVE PLANIFIE incomplet | Traitement backend | Jour précédent le départ à 00:15 Europe/Brussels, borne incluse, rattrapage sous verrou | PUBLIC, PLANIFIE | Conservées | Conservés | Dette unique de 80 EUR pour l'organisateur | Pas de commande HTTP de transition | RM41–45, D5 ; confirmé |
| Annulation personnelle | Propriétaire de la réservation | Strictement avant le départ, opération idempotente avant cette limite | Inchangé ; COMPLET redevient PLANIFIE si une confirmation est libérée | Ligne ANNULEE conservée ; réinscription = nouvelle ligne | Ancienne preuve conservée ; nouveau paiement requis après réinscription | Conservées, aucun effacement d'ACTIVE | 200 ; 401/403/404/409 selon contrôle | Décisions du responsable, PR #42/#43, V16/V17 ; confirmé |
| Garde préparatoire / PRIVE PLANIFIE | Futur service autorisé sous JourneyLocks | Avant J-1, moins de quatre CONFIRMEE, aucune dette existante du trajet | Aucun changement | Aucun changement | Aucun changement | Empêche de préparer un évitement des 80 EUR | Aucun endpoint ; exception métier à code stable | Décision explicite, PR #44 ; confirmé, sans autorisation d'annuler |
| Annulation d'un trajet privé incomplet avant J-1 | Organisateur directement, sans validation administrative (dernière décision du responsable) | Refus validé lorsque l'annulation éviterait la dette | Aucun changement dans le cas refusé | Conservées après refus | Conservés | Mécanisme J-1 conservé | Aucun endpoint ; futur refus 409 cohérent | Garde #44 ; délai et conséquences globales à arbitrer |
| Annulation d'un trajet PUBLIC ou privé complet | Organisateur directement, sans validation administrative (dernière décision du responsable) | Délai et états sources à arbitrer | ANNULE prévu au modèle, transition non livrée | Sort global à arbitrer ; historique à conserver | Conservation obligatoire ; remboursement non défini | Toute dette existante conservée | À définir ; aucune nouvelle route | RM34, RM46–47 et issue #26 : la décision sur l'acteur ne suffit pas à définir la commande |
| Annulation à/après J-1 avant rattrapage | Organisateur directement, sans validation administrative | Trajet potentiellement encore privé ; délai et concurrence avec J-1 à définir | À arbitrer sans permettre un évitement implicite | À arbitrer | Conservation obligatoire | Ni avance de dette ni pénalité supplémentaire supposée | À définir | D5 et garde #44 ; arbitrage indispensable sur le délai et les conséquences |
| Paiement partiel / dette ACTIVE | Propriétaire authentifié | Montant positif, solde non dépassé, idempotence | Inchangé | Conservées | Paiement de dette conservé | ACTIVE : blocage maintenu | 200 ; opérations bloquées 409 | RM44, RM52–54 ; confirmé |
| Règlement intégral / dette ACTIVE | Propriétaire authentifié | Total réglé égal à 80 EUR | Inchangé | Conservées | Historique conservé | REGULARISEE ; droits immédiatement restaurés | 200 | RM52–54 et tests existants ; confirmé |
| REALISE | Acteur / mécanisme à arbitrer | Seulement après l'arrivée ; condition nécessaire, insuffisante à elle seule | Statut absent de l'enum et du CHECK actuel | À arbitrer | Conservation obligatoire | Conservation obligatoire | Aucune route définie | Cahier rapporté comme indicatif ; issue #26 : acteur et états sources non définis |
| Remboursement | Acteur à arbitrer | Éligibilité, montant, délai et mode à définir | À arbitrer | Historique conservé | Aucun remboursement automatique | Dette jamais effacée par supposition | Aucune route définie | RM46–47 et décisions explicites ; ouvert |

Les transitions ambiguës ne sont pas implémentées dans ce lot. La garde de #44
est conservée ; son retour normal n'autorise toujours pas une annulation.
Le comptage et l'ordre de verrouillage existants ne sont pas modifiés.

Actualisation de la finalisation backend : l'annulation directe par l'organisateur
est désormais validée, sans contrôle administratif préalable. Voir le
[nouvel audit ciblé](finalisation-backend-preparation.md) pour les décisions encore
ouvertes, la préparation temporelle non active de fin de trajet et l'analyse RM21.

## Périmètre autorisé de ce lot

Traduire les textes réellement exposés aux utilisateurs et documenter en français
les méthodes métier, sans modifier les codes d'erreur, statuts persistés, noms
JSON, endpoints, règles, tarifs ou transactions. Les messages de configuration,
les exceptions SQL internes et les logs techniques ne sont pas des traductions
à exposer via l'API. Aucun texte d'exception arbitraire ne sera renvoyé au client.

## Messages effectivement traduits ou corrigés

Les textes anglais ci-dessous sont ceux du bundle installé d'Hibernate Validator.
Ils pouvaient être exposés dans `validationErrors` selon la langue du client ou
du serveur. Le bundle applicatif `ValidationMessages.properties` les rend français
y compris avec `Accept-Language: en`, sans changer leurs contraintes ni leurs clés.

| Usage | Ancienne formulation | Nouvelle formulation |
|---|---|---|
| NotNull | must not be null | Ce champ est obligatoire. |
| NotBlank | must not be blank | Ce champ ne doit pas être vide. |
| Positive | must be greater than 0 | La valeur doit être strictement positive. |
| Min | must be greater than or equal to {value} | La valeur doit être supérieure ou égale à {value}. |
| Max | must be less than or equal to {value} | La valeur doit être inférieure ou égale à {value}. |
| Size | size must be between {min} and {max} | La longueur doit être comprise entre {min} et {max} caractères. |
| Pattern | must match "{regexp}" | Le format de ce champ est invalide. |
| Email | must be a well-formed email address | L'adresse électronique doit être valide. |
| DecimalMin | must be greater than [or equal to] {value} | La valeur doit être strictement supérieure / supérieure ou égale à {value}. |
| DecimalMax | must be less than [or equal to] {value} | La valeur doit être strictement inférieure / inférieure ou égale à {value}. |
| Digits | `numeric value out of bounds (<{integer} digits>.<{fraction} digits> expected)` | Le nombre doit contenir au plus {integer} chiffres avant la virgule et {fraction} après. |
| Conflit métier générique | Operation incompatible avec les regles metier | Opération incompatible avec les règles métier. |
| Paramètre requis | Parametre requis absent ou invalide | Paramètre requis absent ou invalide. |
| Intervalle du trajet | L'arrivee doit suivre strictement le depart | L'arrivée doit être strictement postérieure au départ. |

Les variantes inclusive/exclusive des bornes décimales restent dépendantes de
l'annotation existante ; les valeurs et la règle de validation ne changent pas.
Le motif technique d'un jeton n'est plus repris dans le texte destiné au client.
Les erreurs 401, 403 et 404 restent génériques pour ne pas révéler l'existence
d'un compte ou d'une ressource hors périmètre. Les exemples anglais de la mission
tels que « Booking not found » ne figuraient pas dans les messages API du dépôt ;
aucun nouveau message de ce type n'est inventé.

### Descriptions OpenAPI corrigées

| Source | Ancienne formulation | Nouvelle formulation |
|---|---|---|
| Tag trajets | Trajets et reservations ; CLIENT authentifie ; zones et propriete verifies en base. | Trajets et réservations ; CLIENT authentifié ; zones et propriété vérifiées en base. |
| Création | Trajet et place logique crees ; Creer un trajet et la place logique EN_ATTENTE de l'organisateur | Trajet et place logique créés ; Créer un trajet et la place logique EN_ATTENTE de l'organisateur |
| Catalogue | Consulter les trajets visibles et autorises ; Publics autorises et prives dont le client est participant | Consulter les trajets visibles et autorisés ; Trajets publics autorisés et privés organisés ou rejoints par le client |
| Réservation | Demande EN_ATTENTE creee ; Demander sa reservation publique EN_ATTENTE ; Aucun paiement ni confirmation simule dans ce parcours. | Demande EN_ATTENTE créée ; Demander sa réservation publique EN_ATTENTE ; Aucun paiement ni confirmation simulés dans ce parcours. |
| Ajout privé | Participant EN_ATTENTE ajoute ; Ajouter un participant prive EN_ATTENTE (organisateur uniquement) | Participant EN_ATTENTE ajouté ; Ajouter un participant privé EN_ATTENTE (organisateur uniquement) |
| Consultation personnelle | Consulter uniquement ses reservations | Consulter uniquement ses réservations |
| Tag paiements | Validation SIMULATION en dev uniquement, aucune integration bancaire reelle. | Validation SIMULATION en dev uniquement, aucune intégration bancaire réelle. |
| Paiement de place | Cle Idempotency-Key UUID ; confirmation atomique, aucun succes client accepte comme preuve. | Clé Idempotency-Key UUID ; confirmation atomique, aucun succès déclaré par le client accepté comme preuve. |
| Dette | Regler tout ou partie de sa dette en simulation ; Separation stricte des paiements de places ; cle UUID idempotente, aucun effet sur activite compte/client. | Régler tout ou partie de sa dette en simulation ; Séparation stricte des paiements de places ; clé UUID idempotente, aucun effet sur l'activité du compte ou du client. |

Les fins de descriptions sur le filtrage et la pagination restent conservées.

## Méthodes documentées en JavaDoc français

L'inventaire couvre 94 déclarations, dont les deux surcharges de
`SensitiveOperationJournal.record`. Les getters et helpers purement techniques
ne reçoivent pas de commentaires répétitifs.

| Fichier | Méthodes |
|---|---|
| `dto/request/CreateTrajetRequest.java` | isIntervalValid |
| `dto/request/IndisponibiliteWriteRequest.java` | isCibleValide, isPeriodeValide |
| `entity/DetteTrajet.java` | settle |
| `entity/Reservation.java` | cancel, confirm |
| `entity/Trajet.java` | becomePublic, location, markFull, processJ1 |
| `payment/ConfiguredPaymentGateway.java` | validate |
| `security/AdminAuthorization.java` | canManageZone, requireAccountReadZone, requireClientCreation, requireGlobal, requireInvitationManagement, requireZoneScope |
| `security/ClientAuthorization.java` | require |
| `service/AuthService.java` | accepterInvitation, login, resolvePrincipal |
| `service/AvailabilityService.java` | requireAvailable |
| `service/BookingPolicy.java` | eligible, open, requireCancellationPreservesJ1Debt |
| `service/ClientNumberGenerator.java` | reserve |
| `service/CompteService.java` | bootstrapGlobal, createAdmin, createAdministrateur, createAndQueue, createClient, filters, findById, renewInvitation, search, validate, withinScope |
| `service/InvitationDeliveryService.java` | dispatchNext, enqueue |
| `service/InvitationService.java` | consommer, emettre, empreinte, preparer, renouveler, revoquer |
| `service/J1ProcessingService.java` | catchUp |
| `service/JourneyLocks.java` | client, trip, vehicle, zone |
| `service/PaymentService.java` | history, mine, payDebt, paySeat |
| `service/ReferentielService.java` | clientVehicles, clientZones, createPeriod, createVehicle, createZone, deletePeriod, indisponibilite, indisponibilites, managedPeriod, periodScope, requireNoTrip, statistics, updatePeriod, updateZone, validatePeriod, validateTimezone, vehicleStatus, vehicule, vehicules, vehiculeScope, vipAccess, zone, zones, zoneScope, zoneStatus |
| `service/ReservationService.java` | addPrivate, cancel, mine, pending, reserve |
| `service/SensitiveOperationJournal.java` | record, recordByAccount |
| `service/TokenService.java` | issue |
| `service/TrajetService.java` | create, detail, list |
| `service/ZoneAccessPolicy.java` | permits, require |

## Fichiers livrés

- docs/lot-5-trajets-reservations/cycle-vie-messages-fr.md
- src/main/java/com/plateforme/covoiturage/controller/PaymentController.java
- src/main/java/com/plateforme/covoiturage/controller/TrajetController.java
- src/main/java/com/plateforme/covoiturage/dto/request/CreateTrajetRequest.java
- src/main/java/com/plateforme/covoiturage/dto/request/IndisponibiliteWriteRequest.java
- src/main/java/com/plateforme/covoiturage/entity/DetteTrajet.java
- src/main/java/com/plateforme/covoiturage/entity/Reservation.java
- src/main/java/com/plateforme/covoiturage/entity/Trajet.java
- src/main/java/com/plateforme/covoiturage/exception/BusinessRuleException.java
- src/main/java/com/plateforme/covoiturage/exception/GlobalExceptionHandler.java
- src/main/java/com/plateforme/covoiturage/payment/ConfiguredPaymentGateway.java
- src/main/java/com/plateforme/covoiturage/security/AdminAuthorization.java
- src/main/java/com/plateforme/covoiturage/security/ClientAuthorization.java
- src/main/java/com/plateforme/covoiturage/service/AuthService.java
- src/main/java/com/plateforme/covoiturage/service/AvailabilityService.java
- src/main/java/com/plateforme/covoiturage/service/BookingPolicy.java
- src/main/java/com/plateforme/covoiturage/service/ClientNumberGenerator.java
- src/main/java/com/plateforme/covoiturage/service/CompteService.java
- src/main/java/com/plateforme/covoiturage/service/InvitationDeliveryService.java
- src/main/java/com/plateforme/covoiturage/service/InvitationService.java
- src/main/java/com/plateforme/covoiturage/service/J1ProcessingService.java
- src/main/java/com/plateforme/covoiturage/service/JourneyLocks.java
- src/main/java/com/plateforme/covoiturage/service/PaymentService.java
- src/main/java/com/plateforme/covoiturage/service/ReferentielService.java
- src/main/java/com/plateforme/covoiturage/service/ReservationService.java
- src/main/java/com/plateforme/covoiturage/service/SensitiveOperationJournal.java
- src/main/java/com/plateforme/covoiturage/service/TokenService.java
- src/main/java/com/plateforme/covoiturage/service/TrajetService.java
- src/main/java/com/plateforme/covoiturage/service/ZoneAccessPolicy.java
- src/main/resources/ValidationMessages.properties
- src/test/java/com/plateforme/covoiturage/FrenchValidationMessagesTests.java
- src/test/java/com/plateforme/covoiturage/UnexpectedApiErrorIntegrationTests.java

Les scripts de travail et journaux locaux de validation ne font pas partie des commits.

## Validation complète

- Maven Wrapper, tests ciblés : 32 réussis (12 messages français et 20 cas de la garde de #44).
- `mvnw.cmd --batch-mode --no-transfer-progress clean verify` : **BUILD SUCCESS,
  634 tests réussis, 0 échec, 0 erreur, 0 ignoré** ; 16 min 36 s.
- Comparaison des rapports JUnit avec la livraison #44 : les **621 cas existants**
  sont présents et réussis ; 12 nouveaux cas unitaires et un nouveau test HTTP.
- Le test HTTP demande explicitement `Accept-Language: en` et vérifie le code
  `VALIDATION_ERROR`, le format de réponse inchangé et les messages français.
- Le test de conflit métier conserve HTTP 409 et `PAYMENT_REFUSED`, avec le texte
  français corrigé, sans exception interne exposée.
- PostgreSQL 16.6 Testcontainers uniquement. Aucun accès de test aux bases locales
  existantes, aucune modification de migration, tarif, statut ou rôle.
- Contrôle des 24 fichiers documentés : code exécutable et annotations inchangés
  hors la correction de texte explicitement prévue dans CreateTrajetRequest.
- Le serveur existant sur 8081 conserve son processus ; aucun déploiement.
- Aucune décision de transition ambiguë implémentée ; l'issue #26 reste ouverte.

La CI de la branche et celle de la PR sont vérifiées après publication. Les
références finales sont ajoutées à la description de la PR et au rapport de livraison.
