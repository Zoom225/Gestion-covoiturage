# Gestion de la flotte et des indisponibilités — issue #25

Décisions du responsable du 26 octobre 2026, complément à [D1–D5](decisions-metier-d1-d5.md).
Base : master `7e122ea36298e1c6e0968930c785028ad6fa9a90`.
Ce lot ne modifie ni les droits d'écriture des zones de D1, ni les règles de paiement.

## Droits et modèle réutilisé

GLOBAL gère la flotte et les indisponibilités sur le réseau ; LOCAL uniquement
dans sa zone. CLIENT est refusé. La visibilité est filtrée en SQL avant
l'acquisition des verrous et la mutation ; absent et hors périmètre donnent le
même 404. JWT absent/invalide : 401 ; rôle inadapté : 403.

Le véhicule existant contient un identifiant et une zone ; V15 ajoute uniquement
`actif`, obligatoire, initialement vrai, également pour les véhicules préexistants.
La modification livrée porte sur cet état. Aucun nom, immatriculation, chauffeur,
propriétaire CLIENT, capacité variable ou transfert de zone n'est inventé.
Le choix de nouveaux attributs et les transferts explicites restent à spécifier.

Une désactivation interdit de nouvelles planifications avec ce véhicule.
Elle n'annule aucun trajet existant et n'empêche pas leurs réservations ou
paiements lorsqu'ils respectent les règles existantes. Une replanification SQL
sur véhicule inactif est refusée ; les mises à jour d'état, dont J-1, restent possibles.

## API

| Opération | Corps | Résultat |
|---|---|---|
| POST /api/admin/vehicules | zoneId positif obligatoire | 201, véhicule actif, Location |
| PATCH /api/admin/vehicules/{id}/statut | actif booléen obligatoire | 200, état idempotent |
| POST /api/admin/indisponibilites | exactement zoneId ou vehiculeId, debut et fin UTC | 201, Location |
| PUT /api/admin/indisponibilites/{id} | même cible, nouvelles dates | 200 |
| DELETE /api/admin/indisponibilites/{id} | aucun | 204 |

Les GET existants conservent leur pagination et leurs filtres ; VehiculeResponse
ajoute `actif`, y compris dans la consultation CLIENT. Aucun véhicule n'est supprimé.

Les dates sont des Instant ; fin strictement après début. Les périodes sont
semi-ouvertes [début,fin) : contact exact des bornes permis. La modification d'une
période ne change pas sa cible. Une nouvelle indisponibilité ou modification
conflictuelle avec un trajet non annulé est refusée (409) sans déplacer ou annuler
ce trajet. Aucune politique supplémentaire sur les chevauchements entre périodes
d'indisponibilité n'est inventée. La pause de 15 minutes entre trajets reste V11.

## Transactions et PostgreSQL

ReferentielService, ReferentielMapper, AdminAuthorization et JourneyLocks sont
réutilisés. Les mutateurs partagent l'ordre zone → véhicule → indisponibilité ;
les valeurs sont relues après attente du verrou. Les mutations sont transactionnelles.
Les succès sensibles sont journalisés après commit, sans texte utilisateur ni secret.

V15 est additive ; V1–V14 restent inchangées. Elle ajoute le booléen actif et deux
triggers : planification de trajet disponible, mutation d'indisponibilité
compatible avec les trajets. Ils protègent aussi INSERT/UPDATE SQL directs et
sérialisent les écritures concurrentes sur la même zone/ressource.
Les contrôles FK, cible exclusive, intervalle, capacité, paiement et dette sont conservés.

La migration refuse un éventuel historique trajet/indisponibilité déjà incompatible
plutôt que le corriger silencieusement. Toute réconciliation nécessiterait une
décision et une intervention distinctes. Aucune migration n'est exécutée sur
carshare ou carshare_dev_v12.
Les tables concernées sont verrouillées avant la vérification initiale et restent
protégées jusqu'au commit transactionnel de la migration.

## Validation

Recette du 26 octobre 2026 : `mvnw.cmd --batch-mode --no-transfer-progress clean verify`,
**BUILD SUCCESS : 590 tests, 0 échec, 0 erreur, 0 ignoré ; 29 suites ; 11 min 44 s**.
Les 549 cas précédents sont conservés, avec actualisation du test de contrat
Swagger pour les nouvelles mutations explicitement autorisées ; 41 cas sont ajoutés.
La validation ciblée préalable comportait 86 tests réussis (39 flotte + 47 paiements).
Le premier passage complet a identifié l'ancienne attente Swagger limitée à la
lecture ; ce contrat a été corrigé puis la recette complète réexécutée sans erreur.
Les nouveaux tests couvrent droits GLOBAL/LOCAL, JWT 401/403, 400/404/409, mutations,
cible fixe, bornes, désactivation avec paiements existants, protection SQL et
concurrence dans les deux ordres, ainsi que migration V14→V15 avec conservation
des trajets, paiements, dettes et historique Flyway.

Le scénario défensif existant de paiement face à une indisponibilité est conservé :
il prouve désormais le rejet SQL de l'incohérence par V15, puis utilise un espion
AvailabilityService pour vérifier que le paiement revalide encore la disponibilité.
Aucun test n'est désactivé et aucun trigger n'est contourné.

## Exigences et limites

RM19/RM20/RM49/RM51/RM56/RM58–RM60 ; D1/D2 et décisions du 26 octobre.
AA1/AA2 : décisions ; AA3/AA4 : modèle et intégrité ; AA5 : réutilisation ;
AA6 : sécurité ; AA7 : REST/OpenAPI ; AA9 : validations/erreurs ; AA10 : tests ;
AA11 : documentation. AA8 Angular n'est pas modifié ni validé par ce lot.
Le backend n'est pas déclaré terminé : annulation, remboursement, réinscription,
REALISE, inscription publique et autres limites de recette restent distincts.
