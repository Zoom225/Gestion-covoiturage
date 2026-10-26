# Origine et destination géographiques des trajets

Issue [#33](https://github.com/Zoom225/Gestion-covoiturage/issues/33).
Branche dédiée : feature/trajets-origine-destination, créée depuis origin/master
570ed8192ee5c77bc98796f47ff8948a6c904cc6 après fusion de la PR #32.

## Exigences vérifiées

Les quatre documents du dossier local « cahier des charge » ont été examinés.
Les versions REFERENCE_HARMONISE2, REFERENCE_ENRICHIE et 35_pages_illustre
précisent les exigences suivantes :

- section 13.1 : la création comporte au minimum un point de départ et une destination ;
- section 5.1 : la consultation affiche les lieux, distinctement de la date et de l'heure ;
- section 20.1 : le DTO de création porte origine et destination, avec l'organisateur
  déterminé depuis la session.

Le document FIN-DriveConnect_Cahier_des_charges_FINAL_25P_DIAGRAMMES_UML_EA
abrège la création en RM14 et donne priorité au cahier d'origine et à l'énoncé.
Les décisions D1–D5 restent appliquées. D2 concerne les horaires et leur fuseau :
les lieux géographiques constituent deux données supplémentaires.

Le cahier n'impose pas de latitude/longitude, d'adresse structurée ou de fournisseur
de géocodage. Deux libellés sont donc ajoutés, sans calcul d'itinéraire, distance,
conversion automatique d'adresse en zone ou contrôle géométrique inventé.
Les filtres géographiques avancés sont recommandés et restent hors de ce lot minimal.

## Réutilisation de l'existant

Trajet, CreateTrajetRequest, TrajetResponse, TrajetService et TrajetMapper sont
complétés. TrajetController, TrajetRepository, JourneyLocks, BookingPolicy,
ZoneAccessPolicy et les routes existantes sont réutilisés.
Aucun nouveau contrôleur, service concurrent, rôle ou propriétaire n'est ajouté.
Les listes restent filtrées en SQL avant pagination et comptage ; les lieux d'un
trajet privé ne sont visibles que dans le périmètre existant des participants.

## Contrat REST

POST /api/trajets exige origine et destination, en plus des champs existants.
Ce sont des chaînes de 1 à 255 caractères après retrait des espaces périphériques.
Les accents sont conservés. Les valeurs absentes, nulles, blanches ou dépassant
la limite renvoient 400 via la validation et la gestion d'erreurs existantes,
sans créer de trajet ni de réservation partielle.
La limite 255 est un choix technique de stockage et de validation.
Aucune interdiction de libellés identiques n'est déduite du cahier.

```json
{
  "zoneId": 1,
  "vehiculeId": 2,
  "depart": "2026-10-28T10:00:00Z",
  "arrivee": "2026-10-28T11:00:00Z",
  "type": "PUBLIC",
  "origine": "Gare de Bruxelles-Midi",
  "destination": "Gare de Liège-Guillemins"
}
```

Exemple de forme : adapter les identifiants, disponibilités et dates aux droits
et horizons du client. Origine/destination ne remplacent jamais depart/arrivee.

La réponse de création, GET /api/trajets et GET /api/trajets/{id} ajoutent les
deux propriétés. Pour les anciens trajets non renseignés, elles valent null :
aucune adresse fictive, aucune substitution par le nom de zone.
Swagger expose les champs obligatoires et leur limite.

Compatibilité : les clients V13 doivent fournir les deux nouveaux champs pour
créer un trajet. Une ancienne requête sans lieux renvoie désormais 400.
Le frontend Angular n'est pas modifié ; son adaptation relève de #31.
Les règles JWT, propriété, zones, paiement de 20 EUR, capacité, dette de 80 EUR,
concurrence et traitement J-1 restent conservées.

## Migration additive V14

V14__add_trip_geographical_locations.sql ajoute deux colonnes varchar(255).
V1–V13 restent inchangées. Les lignes et leurs identifiants sont conservés,
ainsi que les réservations, paiements, dettes et checksums antérieurs.

Le CHECK ck_trajet_lieux accepte soit deux valeurs inconnues sur les lignes
historiques, soit deux libellés non blancs. Le trigger trg_trajet_lieux impose
les deux valeurs pour tout INSERT. Les UPDATE ne peuvent conserver deux null
que sur une ligne déjà dans cet état ; ils ne peuvent effacer des lieux renseignés
ou fournir un seul lieu. Les mises à jour historiques de statut/type/J-1 restent
possibles. La fonction ne fabrique pas de géographie et ne modifie aucune autre table.

Le constructeur de Trajet protège aussi les nouvelles créations internes.
Le constructeur JPA permet de relire les anciennes valeurs null.
Les quatre preuves différées de paiement de V12/V13 sont conservées.
Aucun script de reprise automatique, endpoint de modification géographique,
annulation ou remboursement n'est ajouté.

V14 est exécutée uniquement sur PostgreSQL Testcontainers pendant cette mission.
Aucune migration n'est appliquée à carshare ou carshare_dev_v12.
Le serveur 8081 n'est pas redémarré. Un déploiement futur doit associer le backend
compatible à V14 ; une ancienne version ne peut plus insérer de trajet sans lieux.

## Validation

- TrajetGeographyTests : invariants du constructeur, normalisation, accents,
  valeurs invalides et limite, sans règle supplémentaire sur les libellés identiques.
- TrajetGeographyIntegrationTests : création/liste/détail, persistance,
  400 sans effet partiel, 401/403/404, confidentialité par zone et du privé,
  accès après ajout valide d'un participant et contrat OpenAPI.
- TrajetGeographyMigrationTests : V13→V14, conservation des données et checksums,
  migration rejouée sans effet, lecture JPA/sérialisation des lieux historiques null,
  contraintes SQL, refus/rollback, enrichissement complet et maintien des anciens
  traitements de statut/type/J-1.

JourneyIntegrationSupport fournit les deux lieux aux créations de test ;
les scénarios existants restent conservés. L'assertion Flyway attend V14.
Les INSERT de TrajetIntegrationTests destinés à tester dates et véhicule/zone
fournissent des lieux valides pour continuer à vérifier leurs contraintes initiales.
Les anciens tests de migrations ciblées V10/V11/V12/V13 restent inchangés.

Commandes PowerShell depuis la copie temporaire isolée :

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=TrajetGeographyTests,TrajetGeographyIntegrationTests,TrajetGeographyMigrationTests,TrajetIntegrationTests,PaymentIntegrationTests,CovoiturageApplicationTests' test
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
```

Validation locale du 26 octobre 2026 :

- tests ciblés : BUILD SUCCESS, 136 tests, zéro échec/erreur/ignoré, 5 min 18 s ;
- clean verify : BUILD SUCCESS, 510 tests dans 23 suites, zéro échec/erreur/ignoré,
  terminé à 13:10:02 CET en 11 min 09 s ; JAR Spring Boot construit ;
- les 464 tests du master de départ restent présents, avec 46 nouveaux cas :
  14 métier, 20 API et 12 migration/contraintes PostgreSQL ;
- les scénarios existants de concurrence, paiements et J-1 passent avec V14.

Les rapports Surefire sont contrôlés, sans test désactivé. La copie de validation
ne contient aucun fichier d'environnement privé. Aucune CI distante n'est annoncée
pour ces modifications non publiées.

Les 14 worktrees préexistants conservent branches, HEAD, index et modifications.
Le nouveau worktree porte les changements non indexés, avec HEAD toujours au
master de départ. Les 44 fichiers Angular indexés sont conservés.
Aucun commit, push, création de PR, fusion, déploiement ou migration sur les bases
existantes n'est effectué. Seule l'issue #33 a été créée, conformément à la mission.

## Finalisation restant à arbitrer

La modification d'un trajet déjà réservé doit respecter RM21 et nécessite de
préciser les effets sur ses participants/paiements. RM46/RM47 et la section 25
laissent ouverts les délais d'annulation, remboursements et réinscriptions.
Le déclenchement de REALISE reste indicatif dans le document final.
Ces opérations relèvent toujours de #26 ; les droits de mutation des véhicules
et indisponibilités restent dans #25. Aucun comportement correspondant n'est inventé.

## Contributions au TFE

RM01–RM03/RM14/RM58/RM60, sections 5.1/13.1/20.1 et décisions D1–D5.
AA1/AA2 : analyse et périmètre ; AA3/AA4 : modèle et intégrité ; AA5 : couches
réutilisées ; AA6 : confidentialité et autorisations ; AA7 : API/Swagger ;
AA9 : validation/erreurs ; AA10 : tests PostgreSQL et non-régression ;
AA11 : documentation et preuves. Contributions partielles ; AA8 reste au lot Angular.
