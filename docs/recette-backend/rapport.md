# Recette backend DriveConnect — état réel et arbitrages

État constaté le 26 octobre 2026, avant publication du correctif #39. Les mentions de correctif local ou non publié décrivent cette photographie de recette ; sa publication autorisée est suivie dans la Pull Request associée à #39.

## Conclusion et références

**Le backend n’est pas terminé et sa conformité globale AA1–AA11 n’est pas acquise.**
Un test réussi sur le périmètre livré ne valide pas un parcours absent ni une
permission restée ouverte.

Références relues : énoncé Enonce_CarShare_Network.docx (24 octobre 2026),
cahiers DriveConnect final/harmonisé/enrichi, registre RM01–RM60,
[décisions D1–D5](../lot-4-zones-vehicules/decisions-metier-d1-d5.md) et
[décisions de comptes](../gestion-comptes/decisions.md). Les documents Word
externes sont lus sans écriture ; aucune règle de PadelAout n’est importée.

## Publication du lot terminé

- PR [#38](https://github.com/Zoom225/Gestion-covoiturage/pull/38) :
  Compléter historique des paiements, statistiques et traçabilité du backend.
- Commit publié : ba866357b7484517ec8ddf35a4a3afecf0d8c48a.
- Fusion autorisée après contrôle d’un commit et de 24 fichiers, deux CI vertes
  et absence de conflit ; SHA master : 4120359fba5e31b43f31a5fa16e058a478e06dd1.
- Issues #35/#36/#37 fermées par cette fusion. Branches et worktrees conservés.
- Les journaux GitHub de la PR attestent 541 tests, 0 échec/erreur/ignoré.
  La CI du nouveau master est également réussie.
- Historique : GET /api/paiements/me, CLIENT propriétaire en SQL avant pagination.
- Statistiques : GET /api/admin/statistiques, intersection du filtre avec GLOBAL/LOCAL.
- Journalisation : événements métier après commit, sans secret ni faux succès après rollback.

## Phase 2 : flotte et indisponibilités (#25)

D1 autorise les mutations des zones par GLOBAL et leur lecture par LOCAL.
Il ne donne expressément aucun droit d’écriture sur les véhicules ou périodes.
Le périmètre réseau/zone fixé dans l’énoncé ne complète pas les modalités
laissées ouvertes dans le registre validé.

Déjà livré : six GET administratifs filtrés avant pagination/comptage,
gestion GLOBAL des zones/activité/fuseau/ouverture OCCASIONNEL et habilitations VIP,
AvailabilityService pour les périodes [début,fin), JourneyLocks zone→véhicule,
FK véhicule/zone et interdiction des chevauchements avec intervalle de 15 minutes.

Manquant : POST/PUT/PATCH véhicules, activité des véhicules et mutations
d’indisponibilités zone/véhicule. Aucun propriétaire CLIENT de véhicule n’est inventé.
Le véhicule courant possède id et zone ; la période possède cible exclusive et dates.

Décisions exactes demandées :

1. GLOBAL et LOCAL gèrent-ils tous deux création, modification et activité des
   véhicules, respectivement réseau/zone, ou GLOBAL seul ?
2. Mêmes droits pour créer/modifier/retirer une période de zone ou véhicule ?
3. Quels champs de véhicule sont modifiables et quels transferts de zone sont
   permis ? Quel sort des trajets existants lors d’une désactivation ?
4. Valider le refus d’une mutation incompatible avec un trajet existant,
   sans annulation/remboursement automatique. Les futurs mutateurs devront
   prendre les mêmes verrous et revalider sous transaction.

Aucun endpoint de mutation de flotte n’est ajouté en attendant ces décisions.
Les opérations déjà autorisées par D1 sont toutes présentes.

## Phase 3 : cycle de vie (#26)

| Fonction | Règle documentée | Droits et validation établis | Décisions manquantes / transition non livrée |
|---|---|---|---|
| Annulation de réservation | RM46 : préserver capacité et états | Propriété personnelle ; contrôle backend, transaction et verrou à conserver | Acteurs administratifs, délai, EN_ATTENTE/CONFIRMEE→ANNULEE, sort des 20 EUR et réouverture de COMPLET |
| Annulation de trajet | ANNULE interdit nouvelle réservation/confirmation (RM23/RM34) | Organisateur lié au trajet ; état SQL prévu et refus testés | Qui annule, jusqu’à quand, effet sur participants/paiements et dette J-1 existante |
| Remboursement | RM47 et section 25 : aucun remboursement automatique supposé | V13 impose paiement validé ⇔ réservation CONFIRMEE | Éligibilité, montant, acteur, idempotence et historique ; ne pas supprimer un paiement pour simuler une restitution |
| Réinscription | RM24 interdit deux participations actives | UNIQUE(trajet,client) et recherche de doublon sur tous les états | Réactivation ou nouvelle ligne ; sort du paiement/remboursement antérieur et nouveau paiement |
| REALISE | État indicatif, déclenchement explicitement à préciser | Heure d’arrivée UTC saisie selon D2 | Automatique ou acteur manuel, états sources, ordre avec rattrapage J-1 ; enum/CHECK/API actuellement absents |
| Modification de trajet | RM21 impose revalidation de date/heure/zone/véhicule/capacité | Contrôles de création et verrous réutilisables | Champs/états autorisés et effets sur réservations ou paiements existants |

Transitions actuellement réalisées :
création → PLANIFIE et première réservation EN_ATTENTE ;
EN_ATTENTE → CONFIRMEE après 20 EUR validés ;
PLANIFIE → COMPLET au quatrième confirmé ;
PRIVE incomplet → PUBLIC et dette ACTIVE de 80 EUR à l’échéance D5 ;
dette ACTIVE → REGULARISEE au paiement intégral seulement.

La présence de ANNULE/ANNULEE dans un enum ou un CHECK n’autorise pas à inventer
un endpoint ou une politique de remboursement. REALISE n’est pas implémenté.
Les droits d’administration de ces mutations restent à décider.

## Autres limites découvertes dans la relecture complète

DEC-COMPTES-001 valide une future inscription publique CLIENT/OCCASIONNEL avec
matricule généré et vérification d’e-mail. Ce parcours et la vérification publique
ne sont pas livrés ; l’invitation administrative ne les remplace pas.
Les points P03/P08/P11 laissent encore des champs, l’unicité e-mail et la politique
de vérification ouverts. Aucun endpoint public n’est ajouté sans ces décisions.

Les transitions de catégorie/zone et les bascules/réactivations de comptes
restent à préciser (P01/P05/P09) ; DEC-COMPTES-006 ne valide que création et invitation.
L’espace de supervision détaillée des trajets/réservations/paiements n’est pas
une gestion administrative complète : seuls les endpoints effectivement présents
et les agrégats #36 sont annoncés. Les droits sur les détails privés ne sont pas inventés.

## Correctif technique distinct — issue #39

[Corriger les erreurs techniques renvoyées en 403 et protéger le contrat HTTP](https://github.com/Zoom225/Gestion-covoiturage/issues/39).

Branche locale : fix/erreurs-api-inattendues, base master 4120359.
Reproduction sur vrai HTTP/PostgreSQL jetable : exception inattendue pendant
la validation du paiement → réponse 403 au lieu de 500 et détail fictif dans les
journaux du conteneur web. Réservation toujours EN_ATTENTE, aucun paiement conservé.
Le premier test de régression échoue donc comme prévu (1 échec, 0 erreur).

Cause observée : absence de traitement des exceptions non mappées dans le
gestionnaire central. Le correctif reste dans GlobalExceptionHandler :
réponse ApiErrorResponse générique, référence UUID X-Error-Id et journal limité
au type d’exception et à la référence, sans message, cause ni pile sensibles.
Les statuts du framework restent respectés ; seuls Allow/Accept/Accept-Patch/
Retry-After peuvent être propagés, jamais Authorization ou Set-Cookie.
Les handlers spécifiques et la chaîne JWT ne sont pas affaiblis ; /error n’est pas rendu public.

Huit cas PostgreSQL/HTTP couvrent 500/502 sûrs, rollback, référence de diagnostic,
absence de fuite dans les journaux applicatifs, 400, JWT 401/403, 405 avec Allow,
409, 415 et 503 existants. Une construction de fixture à en-têtes non modifiables
a été corrigée dans le test sans changer le comportement applicatif attendu.

**Recette complète réussie le 26 octobre 2026 à 15:41:35 (+01:00).**
Commande : `./mvnw.cmd clean verify`, sans sélection ni désactivation de tests,
avec PostgreSQL 16.6 Testcontainers et sans connexion aux bases existantes.
Résultat : **549 tests, 0 échec, 0 erreur, 0 ignoré**, 27 suites, BUILD SUCCESS,
durée Maven 11 min 09 s. La comparaison des noms des cas de test confirme la
conservation des **541 tests précédents**, auxquels s’ajoutent huit tests HTTP.
Les 14 migrations V1–V14 sont identiques à celles du master de référence.
Ce correctif reste local, non commité, non poussé et sans Pull Request ; seule
la publication du lot de 24 fichiers a été effectuée dans cette mission.
Aucun commit, push ou PR du correctif ne sera annoncé comme réalisé sans publication autorisée.

## Matrice RM01–RM60

COMPLET signifie que le critère est vérifié dans les opérations livrées.
PARTIEL signale une partie prouvée mais une fonction ou décision manquante.
ABSENT désigne un parcours non livré ; aucune réussite de test n’est inventée.

| Exigence | Intitulé | État | Code / API | Preuve et limites |
|---|---|---|---|---|
| RM01 | Authentification obligatoire | COMPLET sur périmètre livré | SecurityConfiguration, AuthService, AdminAuthorization, ClientAuthorization ; POST /api/auth/login ; GET /api/auth/me | SecurityIntegrationTests, AdminCompteIntegrationTests |
| RM02 | Autorisation selon le rôle | COMPLET sur périmètre livré | SecurityConfiguration, AuthService, AdminAuthorization, ClientAuthorization ; POST /api/auth/login ; GET /api/auth/me | SecurityIntegrationTests, AdminCompteIntegrationTests |
| RM03 | Propriété des données personnelles | COMPLET sur périmètre livré | SecurityConfiguration, AuthService, AdminAuthorization, ClientAuthorization ; POST /api/auth/login ; GET /api/auth/me | SecurityIntegrationTests, AdminCompteIntegrationTests |
| RM04 | Trois catégories de clients | COMPLET sur périmètre livré | SecurityConfiguration, AuthService, AdminAuthorization, ClientAuthorization ; POST /api/auth/login ; GET /api/auth/me | SecurityIntegrationTests, AdminCompteIntegrationTests |
| RM05 | Horizon VIP | COMPLET sur périmètre livré | BookingPolicy, ZoneAccessPolicy, Client ; POST /api/trajets ; /reservations ; /participants ; /paiements | TrajetIntegrationTests, PaymentIntegrationTests |
| RM06 | Horizon LOCAL | COMPLET sur périmètre livré | BookingPolicy, ZoneAccessPolicy, Client ; POST /api/trajets ; /reservations ; /participants ; /paiements | TrajetIntegrationTests, PaymentIntegrationTests |
| RM07 | Périmètre LOCAL | COMPLET sur périmètre livré | BookingPolicy, ZoneAccessPolicy, Client ; POST /api/trajets ; /reservations ; /participants ; /paiements | TrajetIntegrationTests, PaymentIntegrationTests |
| RM08 | Horizon OCCASIONNEL | COMPLET sur périmètre livré | BookingPolicy, ZoneAccessPolicy, Client ; POST /api/trajets ; /reservations ; /participants ; /paiements | TrajetIntegrationTests, PaymentIntegrationTests |
| RM09 | Identifiant VIP | COMPLET sur périmètre livré | ClientNumberGenerator, ClientNumberRepository, V3/V4 ; POST /api/admin/clients | ClientNumberingIntegrationTests, AccountIdentifierIntegrationTests |
| RM10 | Identifiant LOCAL | COMPLET sur périmètre livré | ClientNumberGenerator, ClientNumberRepository, V3/V4 ; POST /api/admin/clients | ClientNumberingIntegrationTests, AccountIdentifierIntegrationTests |
| RM11 | Identifiant OCCASIONNEL | COMPLET sur périmètre livré | ClientNumberGenerator, ClientNumberRepository, V3/V4 ; POST /api/admin/clients | ClientNumberingIntegrationTests, AccountIdentifierIntegrationTests |
| RM12 | Zones autorisées VIP | COMPLET sur périmètre livré | ZoneAccessRepository, ZoneAccessPolicy, ReferentielService ; PUT/DELETE /api/admin/zones/{id}/clients-vip/{clientId} | ReferentielIntegrationTests, TrajetIntegrationTests |
| RM13 | Compte actif | COMPLET sur périmètre livré | AuthService, DatabaseJwtAuthenticationConverter ; Toutes les API protégées | SecurityIntegrationTests, AccountWorkflowHttpIntegrationTests |
| RM14 | Création de trajet | COMPLET sur périmètre livré | TrajetService, TrajetMapper, V11/V14, BookingPolicy, PaymentService ; POST/GET /api/trajets ; POST /api/reservations/{id}/paiements | TrajetGeographyTests, TrajetGeographyIntegrationTests, TrajetGeographyMigrationTests, PaymentIntegrationTests |
| RM15 | Première place organisateur — amendée par D4 | COMPLET sur périmètre livré | TrajetService, TrajetMapper, V11/V14, BookingPolicy, PaymentService ; POST/GET /api/trajets ; POST /api/reservations/{id}/paiements | TrajetGeographyTests, TrajetGeographyIntegrationTests, TrajetGeographyMigrationTests, PaymentIntegrationTests — D4 remplace expressément la confirmation immédiate : place logique EN_ATTENTE, puis paiement de 20 EUR. |
| RM16 | Capacité maximale | COMPLET sur périmètre livré | TrajetService, TrajetMapper, V11/V14, BookingPolicy, PaymentService ; POST/GET /api/trajets ; POST /api/reservations/{id}/paiements | TrajetGeographyTests, TrajetGeographyIntegrationTests, TrajetGeographyMigrationTests, PaymentIntegrationTests |
| RM17 | PUBLIC / PRIVE | COMPLET sur périmètre livré | TrajetService, TrajetMapper, V11/V14, BookingPolicy, PaymentService ; POST/GET /api/trajets ; POST /api/reservations/{id}/paiements | TrajetGeographyTests, TrajetGeographyIntegrationTests, TrajetGeographyMigrationTests, PaymentIntegrationTests |
| RM18 | Gestion du privé par l’organisateur | PARTIEL | ReservationService.addPrivate, ClientAuthorization ; POST /api/trajets/{id}/participants | TrajetIntegrationTests — Ajout livré ; retrait/annulation non arbitré (#26). |
| RM19 | Véhicule disponible | PARTIEL | AvailabilityService, IndisponibiliteRepository ; Création de trajet et paiement | ReferentielIntegrationTests, TrajetIntegrationTests — Périodes contrôlées ; mutation des indisponibilités et activité du véhicule absentes (#25). |
| RM20 | Chevauchement et intervalle de 15 min | COMPLET sur périmètre livré | JourneyLocks, TrajetRepository.overlaps, trg_trajet_interval (V11) ; POST /api/trajets ; contraintes SQL | TrajetIntegrationTests.vehicleNeedsFifteenMinutesBetweenTrips, concurrentVehicleCreationsAndDuplicateBookingsRemainAtomic |
| RM21 | Modification avec revalidation | ABSENT | Contrôles de création réutilisables ; aucun mutateur de trajet ; Aucun PUT/PATCH /api/trajets/{id} | Aucun scénario de mutation livré — Droits/états et effets sur participants/paiements à valider (#26). |
| RM22 | Réservation personnelle | COMPLET sur périmètre livré | ReservationService, BookingPolicy, PaymentService, V11–V13 ; POST /api/trajets/{id}/reservations ; /participants ; POST /api/reservations/{id}/paiements | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM23 | Trajet réservable | PARTIEL | ReservationService, BookingPolicy, PaymentService, V11–V13 ; POST /api/trajets/{id}/reservations ; /participants ; POST /api/reservations/{id}/paiements | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests — PLANIFIE/COMPLET/ANNULE contrôlés ; REALISE et clôture non livrés. |
| RM24 | Absence de réservation en double | PARTIEL | ReservationService, BookingPolicy, PaymentService, V11–V13 ; POST /api/trajets/{id}/reservations ; /participants ; POST /api/reservations/{id}/paiements | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests — UNIQUE(trajet_id,client_id) empêche les doublons de tout état ; réinscription non arbitrée. |
| RM25 | Place disponible à la confirmation | COMPLET sur périmètre livré | ReservationService, BookingPolicy, PaymentService, V11–V13 ; POST /api/trajets/{id}/reservations ; /participants ; POST /api/reservations/{id}/paiements | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM26 | EN_ATTENTE sans garantie | COMPLET sur périmètre livré | ReservationService, BookingPolicy, PaymentService, V11–V13 ; POST /api/trajets/{id}/reservations ; /participants ; POST /api/reservations/{id}/paiements | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM27 | Confirmation par paiement | COMPLET sur périmètre livré | ReservationService, BookingPolicy, PaymentService, V11–V13 ; POST /api/trajets/{id}/reservations ; /participants ; POST /api/reservations/{id}/paiements | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM28 | Priorité au premier paiement | COMPLET sur périmètre livré | ReservationService, BookingPolicy, PaymentService, V11–V13 ; POST /api/trajets/{id}/reservations ; /participants ; POST /api/reservations/{id}/paiements | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM29 | Prix de 20 EUR | COMPLET sur périmètre livré | PaymentService, JourneyLocks, BookingPolicy, V11/V12/V13 ; POST /api/reservations/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM30 | Référence totale de 80 EUR | COMPLET sur périmètre livré | PaymentService, JourneyLocks, BookingPolicy, V11/V12/V13 ; POST /api/reservations/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests — Référence 20 EUR × 4 = 80 EUR ; aucune tarification dynamique. |
| RM31 | Atomicité paiement / confirmation | COMPLET sur périmètre livré | PaymentService, JourneyLocks, BookingPolicy, V11/V12/V13 ; POST /api/reservations/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM32 | Concurrence sur la dernière place | COMPLET sur périmètre livré | PaymentService, JourneyLocks, BookingPolicy, V11/V12/V13 ; POST /api/reservations/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM33 | Trajet complet | COMPLET sur périmètre livré | PaymentService, JourneyLocks, BookingPolicy, V11/V12/V13 ; POST /api/reservations/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM34 | Trajet annulé | PARTIEL | PaymentService, JourneyLocks, BookingPolicy, V11/V12/V13 ; POST /api/reservations/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests — Refus des réservations/paiements sur état ANNULE testé ; endpoint d’annulation absent. |
| RM35 | Paiement sans place refusé | COMPLET sur périmètre livré | PaymentService, JourneyLocks, BookingPolicy, V11/V12/V13 ; POST /api/reservations/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM36 | Dette contrôlée à la réservation | COMPLET sur périmètre livré | BookingPolicy, ReservationService, PaymentService, V13 ; Création, réservation, ajout privé et confirmation | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM37 | Dette contrôlée à l’ajout | COMPLET sur périmètre livré | BookingPolicy, ReservationService, PaymentService, V13 ; Création, réservation, ajout privé et confirmation | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM38 | Zone contrôlée à la réservation | COMPLET sur périmètre livré | BookingPolicy, ReservationService, PaymentService, V13 ; Création, réservation, ajout privé et confirmation | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM39 | Revalidation à la confirmation | COMPLET sur périmètre livré | BookingPolicy, ReservationService, PaymentService, V13 ; Création, réservation, ajout privé et confirmation | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM40 | Transitions de réservation autorisées | PARTIEL | BookingPolicy, ReservationService, PaymentService, V13 ; Création, réservation, ajout privé et confirmation | TrajetIntegrationTests, PaymentIntegrationTests, PaymentIntegrityIntegrationTests — EN_ATTENTE→CONFIRMEE atomique livré ; annulation/remboursement/réinscription non arbitrés. |
| RM41 | Contrôle la veille | COMPLET sur périmètre livré | J1Scheduler, J1ProcessingService, DetteTrajet, BookingPolicy, V11/V12/V13 ; Traitement 00:15 Europe/Brussels, rattrapage ; GET /api/dettes/me | PaymentIntegrationTests, TrajetIntegrationTests |
| RM42 | Conversion privé vers public | COMPLET sur périmètre livré | J1Scheduler, J1ProcessingService, DetteTrajet, BookingPolicy, V11/V12/V13 ; Traitement 00:15 Europe/Brussels, rattrapage ; GET /api/dettes/me | PaymentIntegrationTests, TrajetIntegrationTests |
| RM43 | Dette de 80 EUR à J-1 | COMPLET sur périmètre livré | J1Scheduler, J1ProcessingService, DetteTrajet, BookingPolicy, V11/V12/V13 ; Traitement 00:15 Europe/Brussels, rattrapage ; GET /api/dettes/me | PaymentIntegrationTests, TrajetIntegrationTests |
| RM44 | Blocage par dette ACTIVE | COMPLET sur périmètre livré | J1Scheduler, J1ProcessingService, DetteTrajet, BookingPolicy, V11/V12/V13 ; Traitement 00:15 Europe/Brussels, rattrapage ; GET /api/dettes/me | PaymentIntegrationTests, TrajetIntegrationTests |
| RM45 | Dette sans expiration | COMPLET sur périmètre livré | J1Scheduler, J1ProcessingService, DetteTrajet, BookingPolicy, V11/V12/V13 ; Traitement 00:15 Europe/Brussels, rattrapage ; GET /api/dettes/me | PaymentIntegrationTests, TrajetIntegrationTests |
| RM46 | Annulation de réservation | ABSENT | Valeur ANNULEE et preuve paiement/confirmation existantes ; Aucun endpoint d’annulation/remboursement | Aucun test d’un parcours non défini — Acteurs, échéance, réservations payées et réouverture d’un COMPLET à décider. |
| RM47 | Traitement après annulation / remboursement | ABSENT | Valeur ANNULEE et preuve paiement/confirmation existantes ; Aucun endpoint d’annulation/remboursement | Aucun test d’un parcours non défini — Politique et modèle de restitution manquants ; ne pas effacer les paiements validés. |
| RM48 | Paiement partiel de dette | COMPLET sur périmètre livré | PaymentService.payDebt, DetteTrajet.settle, V13 ; POST /api/dettes/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM49 | Administration locale | PARTIEL | AdminAuthorization, spécifications SQL des services ; GET /api/admin/** et mutations de zones/comptes autorisées | AdminCompteIntegrationTests, ReferentielIntegrationTests, AdminStatisticsIntegrationTests — Périmètre prouvé sur l’existant ; droits de mutation de flotte toujours ouverts. |
| RM50 | Paiement intégral de dette | COMPLET sur périmètre livré | DetteTrajet.settle, PaymentService.payDebt, V13 ; POST /api/dettes/{id}/paiements | PaymentIntegrationTests, PaymentIntegrityIntegrationTests |
| RM51 | Administration globale | PARTIEL | AdminAuthorization.requireGlobal/requireZoneScope ; GET /api/admin/** ; POST/PUT/PATCH zones et comptes autorisés | SecurityIntegrationTests, ReferentielIntegrationTests, AdminStatisticsIntegrationTests — Flotte, cycle de vie et autres mutations non autorisées absents. |
| RM52 | Restauration des droits | COMPLET sur périmètre livré | PaymentService, DetteTrajet, J1ProcessingService, V12/V13 ; Paiements de dette et scheduler J-1 | PaymentIntegrationTests, PaymentIntegrityIntegrationTests, PaymentHistoryIntegrationTests |
| RM53 | Dette unique par fait générateur | COMPLET sur périmètre livré | PaymentService, DetteTrajet, J1ProcessingService, V12/V13 ; Paiements de dette et scheduler J-1 | PaymentIntegrationTests, PaymentIntegrityIntegrationTests, PaymentHistoryIntegrationTests |
| RM54 | Séparation des paiements | COMPLET sur périmètre livré | PaymentService, DetteTrajet, J1ProcessingService, V12/V13 ; Paiements de dette et scheduler J-1 | PaymentIntegrationTests, PaymentIntegrityIntegrationTests, PaymentHistoryIntegrationTests |
| RM55 | Traitement automatique idempotent | COMPLET sur périmètre livré | PaymentService, DetteTrajet, J1ProcessingService, V12/V13 ; Paiements de dette et scheduler J-1 | PaymentIntegrationTests, PaymentIntegrityIntegrationTests, PaymentHistoryIntegrationTests |
| RM56 | Indisponibilité des zones | PARTIEL | AvailabilityService, IndisponibiliteRepository ; POST /api/trajets et paiement | ReferentielIntegrationTests, TrajetIntegrationTests — Création contrôlée ; gestion des périodes et modification des trajets non livrées. |
| RM57 | Statistiques cohérentes | COMPLET sur périmètre livré | TrajetRepository.statistics, ReferentielService.statistics ; GET /api/admin/statistiques | AdminStatisticsIntegrationTests |
| RM58 | Intégrité référentielle | COMPLET sur périmètre livré | Migrations V1–V14, FK/CHECK/UNIQUE, triggers différés V13 ; Intégrité SQL et API existantes | CovoiturageApplicationTests, PaymentIntegrityIntegrationTests, TrajetGeographyMigrationTests, ClientNumberingIntegrationTests |
| RM59 | Unicité des identifiants | COMPLET sur périmètre livré | Migrations V1–V14, FK/CHECK/UNIQUE, triggers différés V13 ; Intégrité SQL et API existantes | CovoiturageApplicationTests, PaymentIntegrityIntegrationTests, TrajetGeographyMigrationTests, ClientNumberingIntegrationTests |
| RM60 | Journalisation et traçabilité | PARTIEL | SensitiveOperationJournal ; correctif local GlobalExceptionHandler ; Opérations sensibles existantes ; erreurs API | SensitiveOperationJournalIntegrationTests, UnexpectedApiErrorIntegrationTests — Journal métier livré par #38 ; correction des erreurs inattendues locale (#39). Les fonctions absentes ne sont pas traçables. |

## Contrôle des AA1–AA11

| AA | Éléments effectivement prouvés | Ce qui empêche une validation globale |
|---|---|---|
| AA1 | Acteurs CLIENT/ADMIN, catégories, périmètres et besoin analysés | Arbitrages restants et recette complète des parcours manquants |
| AA2 | Cahiers, RM01–RM60, D1–D5, DEC-COMPTES et contrats tracés | Spécifications flotte/cycle de vie/inscription publique incomplètes |
| AA3 | Entities/DTO/mappers et relations documentés | Modèle d’activité flotte, remboursement, réinscription et REALISE à finaliser |
| AA4 | V1–V14 et contraintes testées sur PostgreSQL 16.6 jetable | Migrations futures selon décisions ; privilèges du serveur existant non réévalués dans cette mission |
| AA5 | Controllers→Services→Repositories, injection et composants réutilisés | Services des opérations absentes ; pas de réécriture du socle |
| AA6 | BCrypt, JWT stateless, relecture PostgreSQL, propriétaire, GLOBAL/LOCAL, 401/403 et CORS testés | Matrice de permissions des futures mutations ; correction #39 non publiée |
| AA7 | API REST/OpenAPI des fonctions livrées et historique/statistiques | Mutations obligatoires non livrées ; serveur 8081 encore ancien |
| AA8 | Contrats backend prêts pour intégration | Angular hors modification/recette de cette mission ; #31 reste distinct |
| AA9 | Bean Validation et erreurs métier/sécurité testées | Erreur inattendue corrigée localement (#39), à publier après validation |
| AA10 | Suites métier/HTTP/JPA/PostgreSQL, concurrence, rollback et CI | Tests de tous les parcours encore absents impossibles avant leurs décisions |
| AA11 | Dossiers et preuves par lot, procédure d’installation | Démonstration intégrée de tous les parcours et environnement récent à recevoir |

## Swagger, Flyway et environnement

Les tests de sécurité vérifient Swagger/OpenAPI en dev et leur fermeture en
prod ; CovoiturageApplicationTests attend Flyway V14. Les tests de migrations
ciblées et de concurrence restent actifs. Les corrections n’ajoutent aucune migration.

Observation du serveur existant : /swagger-ui/index.html et /v3/api-docs répondent
HTTP 200, mais le document ne présente ni /api/paiements/me, ni /api/admin/statistiques,
ni le champ origine du DTO courant. Ce serveur ne constitue pas la recette du code
récent. Il n’est ni redémarré ni migré ; le premier appel UI avait dépassé son délai,
puis le contrôle a confirmé HTTP 200.

La recette récente utilise exclusivement des ports aléatoires et PostgreSQL Testcontainers.
Aucune donnée de carshare/carshare_dev_v12 ni historique Flyway existant n’est modifié.
Aucun déploiement, suppression de branche/worktree, force push, rebase ou reset.
Les 14 anciens worktrees et l’index Angular de 44 fichiers sont comparés à l’inventaire.
