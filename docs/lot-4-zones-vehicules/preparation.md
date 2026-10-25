> Historique du socle initial. D1-D5 sont maintenant valides : voir [decisions-metier-d1-d5.md](decisions-metier-d1-d5.md). Les limites et resultats ci-dessous decrivent le commit 586cef0 ; le contrat courant figure dans ce nouveau registre.

# Préparation initiale du lot 4 — historique

Issue : [#25](https://github.com/Zoom225/Gestion-covoiturage/issues/25).
Branche : feature/zones-vehicles-unavailability.

Base commune : origin/master 653e9a0c044bdc2fd7151498ff9d9192c4269384.
PR #24 fusionnée le 25 octobre 2026 ; issue #23 fermée. 294 tests antérieurs conservés.
Les contrôles GitHub de la PR #24 sont réussis ; les rapports locaux existants et la CI confirment
294 tests, 0 échec, 0 erreur, 0 ignoré. Aucun nouveau lancement Maven pour les lots ci-dessous à ce stade.

Références lues : énoncé ../Enonce_CarShare_Network.docx ; cahier
../cahier des charge/FIN-DriveConnect_Cahier_des_charges_FINAL_25P_DIAGRAMMES_UML_EA.docx,
registre RM01–RM60, sections 5/7/14/16/19/25, et docs/gestion-comptes/.
Les chemins externes sont relatifs à la racine du dépôt et restent hors Git.

Cet instantané initial reste historique. Le socle de consultation autorisé ensuite par le responsable est décrit dans [consultation.md](consultation.md). Les cinq nouvelles propositions restent non validées ; aucune permission de mutation n'est ajoutée.
Aucune migration, aucun compte réel, aucune donnée PostgreSQL partagée n'est modifié.
Les trois branches ont été créées depuis ce master. Les lots 5 et 6 devront intégrer les commits
de leurs dépendances et être présentés en PR dépendantes, sans fusion automatique dans master.

## Existant à réutiliser

- Zone : id, nom ; ZoneRepository.
- Vehicule : id, zone obligatoire ; VehiculeRepository. L'énoncé décrit les véhicules d'une zone
  (flotte avec chauffeur), sans relation de propriété véhicule–CLIENT définie.
- Client : zone obligatoire pour LOCAL, contrôlée en V1 ; création administrative déjà livrée via
  POST /api/admin/clients, sans second parcours d'association libre ou changement de catégorie.
- AdminAuthorization, JWT relu en PostgreSQL, CompteService, DTO/mapper et GlobalExceptionHandler.
- Migrations V1–V8 et les 294 tests : conservation intégrale.

## Règles confirmées

RM07 : rattachement LOCAL ; RM19/RM56 : indisponibilités véhicule/zone à contrôler ;
RM49/RM51 : périmètres LOCAL/GLOBAL ; RM58–RM60 : FK, unicité adaptée et traçabilité.
Les indisponibilités ciblent soit une zone soit un véhicule, avec cohérence exclusive en base.
Le modèle du cahier exige début < fin. Ne pas inventer de propriété CLIENT pour les véhicules.

## Décision bloquante D4-01 — droits CRUD des zones

docs/gestion-comptes/decisions.md, P07, et matrice-permissions.md maintiennent création,
modification et suppression des zones À PRÉCISER. RM49 limite un périmètre, sans préciser
à elle seule chaque opération de mutation.

Proposition soumise, non validée : GLOBAL seul crée/renomme/supprime les zones ;
GLOBAL et LOCAL gèrent véhicules/indisponibilités dans leur périmètre ; suppression d'une
ressource référencée refusée. Alternative soumise : LOCAL peut aussi renommer sa zone.
Ne pas transformer cette proposition en permission sans réponse du responsable.

L'ajout d'une indisponibilité en conflit avec un futur trajet existant devra préserver les invariants,
sans annulation ou remboursement automatique inventé. Les bornes temporelles et la politique de
chevauchement des indisponibilités seront explicites dans le contrat retenu avant les tests.

## Contrats REST à stabiliser — non implémentés

- GET /api/admin/zones et GET /api/admin/zones/{id}.
- POST /api/admin/zones ; modification/suppression selon D4-01.
- GET /api/admin/vehicules et GET /api/admin/vehicules/{id}.
- Création/gestion des véhicules dans le périmètre de leur zone, selon le contrat confirmé.
- Gestion des indisponibilités avec une cible exclusive zone/véhicule et période validée.

Controller → service métier → repositories → PostgreSQL ; mapper explicite, DTO sans secrets.
Filtrage du périmètre dans la requête avant pagination/comptage ; détail absent/invisible homogène.
AdminAuthorization demeure la politique unique, sans dupliquer les contrôles dans les controllers.
Une éventuelle V9 additive sera créée après arbitrage ; aucun numéro de migration n'est réservé en base.

## Tests à développer après arbitrage

- 401/403, GLOBAL réseau, LOCAL uniquement sa zone, aucun accès ADMIN par CLIENT.
- Association LOCAL/zone existante préservée ; zone obligatoire et FK.
- Dates invalides, cible absente/double, périodes et chevauchements aux bornes.
- Préservation des références ; refus de suppression incompatible ; rollback.
- Requêtes sans N+1, totaux avant pagination, DTO sans données sensibles.
- Testcontainers PostgreSQL, migration additive, non-régression des 294 tests, clean verify et CI.

AA concernés : AA1/AA2 clarification, AA3 modèle, AA4 contraintes, AA5 couches, AA6 sécurité,
AA7 REST, AA9 erreurs, AA10 tests/Git, AA11 documentation. AA8 seulement préparé ; aucun acquis
n'est annoncé intégralement couvert.
