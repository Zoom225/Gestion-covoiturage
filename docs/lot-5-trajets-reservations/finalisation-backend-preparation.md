# Issue #26 — audit ciblé et préparation indépendante de fin de trajet

## Base vérifiée

Master `cffd5508888c9be69f5d172d07923b6fb185b37e` : PR #45 et #46 fusionnées.
La CI post-fusion est verte :
https://github.com/Zoom225/Gestion-covoiturage/actions/runs/40512784727.
Branche dédiée : `feature/issue-26-preparation-fin-trajet`.

Issue #26 et documents D1–D5, lots 5/6, recette backend, RM21, PR #44/#45/#46
consultés. Le cahier externe référencé par README sous `../cahier des charge/`
n'est pas présent localement ; son contenu n'est pas présumé vérifié.
Les migrations courantes sont V1 à V18, dont V18 pour le catalogue indépendant.

La dernière décision explicite du responsable valide l'annulation directe par
l'organisateur, **sans validation administrative**. Elle remplace l'ancienne
question « directe ou validation administrative » des documents historiques et
de l'issue. Elle ne fixe pas le délai ni le devenir des réservations.

## Tableau de livraison et de décisions

| Lot | Déjà implémenté | Validé mais non activé | Décisions à arbitrer | Tests nécessaires / existants | Fichiers et migrations concernés |
|---|---|---|---|---|---|
| Création et réservation | JWT CLIENT effectif, organisateur issu du JWT, zones D3, véhicule disponible, horizons J-30/J-10/J-3, quatre confirmations maximum, 20 EUR par place | Aucun complément dans ce lot | Champs et fenêtres d'une modification ultérieure | Suites TrajetIntegrationTests, PaymentIntegrationTests, flotte et sécurité conservées | TrajetController, TrajetService, ReservationService, BookingPolicy, ZoneAccessPolicy, AvailabilityService ; V10–V15 |
| Annulation personnelle de réservation | Propriétaire seul avant le départ ; ANNULEE conservée, idempotence avant cette limite, réouverture de capacité, aucun remboursement | Rien à changer | Pas de nouvelle règle sur ce parcours dans cette mission | ReservationCancellationIntegrationTests, CancellationIntegrityIntegrationTests, réinscription et concurrence existantes | ReservationService.cancel, TrajetController.cancelBooking, V16/V17 inchangées |
| Annulation du trajet par l'organisateur | Contrôles JWT, propriété et JourneyLocks réutilisables ; aucune commande de trajet n'existe | Annulation directe par le propriétaire, sans approbation administrative ; dette et historique conservés | Délai, états sources, sort des réservations EN_ATTENTE/CONFIRMEE, répétition et annulation à/après J-1 avant rattrapage | À l'activation : propriétaire/non-propriétaire, JWT, bornes, rollback, confirmations/paiements conservés, courses paiement/J-1 | Futur TrajetService et contrôleur léger ; ClientAuthorization, JourneyLocks, garde #44 ; migrations seulement si invariants SQL nouveaux nécessaires |
| Protection J-1 | Privé PLANIFIE incomplet devient PUBLIC à 00:15 Europe/Brussels la veille ; dette unique 80 EUR ; garde préparatoire de #44 | Garde à appeler dans la future transaction d'annulation | Cas exact de J-1 et traitement automatique retardé ; aucune permission d'annulation déduite du retour normal de la garde | BookingCancellationPolicyTests et tests PostgreSQL/J-1 existants ; future course annulation/J-1 | BookingPolicy.requireCancellationPreservesJ1Debt, J1ProcessingService, JourneyLocks, V11/V12/V17 ; aucun changement |
| Fin du trajet / REALISE | Arrivée obligatoire, instants UTC, arrivée après départ ; enum et CHECK limités à PLANIFIE/COMPLET/ANNULE | Seulement après l'heure d'arrivée ; **précondition temporelle préparée dans ce lot**, sans transition | Déclencheur, acteur, états sources, répétition, sort des réservations et articulation avec J-1 | Nouveaux tests unitaires avant/à/après arrivée, UTC et fuseaux, absence de mutation ; futurs tests HTTP/PostgreSQL de transition après arbitrage | BookingPolicy et JourneyCompletionPolicyTests ; future évolution de StatutTrajet, CHECK V11 et projections, par nouvelle migration après validation uniquement |
| Paiements et dettes | Paiements séparés et historiques, ACTIVE après paiement partiel, REGULARISEE au règlement intégral et droits restaurés ; dette non effacée | Toute annulation doit conserver les dettes et paiements ; aucun effet financier nouveau | Politique éventuelle de remboursement : acteur, éligibilité, montant, délai et mode | PaymentIntegrationTests, PaymentIntegrityIntegrationTests et historique existants | PaymentService, DetteTrajet, PaiementPlace/PaiementDette, V11–V13/V17 ; aucun changement |
| Modification RM21 | Contrôles de création et de disponibilité réutilisables ; aucun PUT/PATCH de trajet | Revalidation nécessaire si un futur mutateur est autorisé | Liste de champs, acteurs, temps, états sources et effets sur participants/paiements | À l'activation : droits, disponibilité, quinze minutes, horizons, dettes, concurrence, historique et refus sans écriture | Futur TrajetService/DTO dédié, JourneyLocks, BookingPolicy, AvailabilityService ; V11/V14/V15 à examiner, aucune modification actuelle |

## Parcours d'annulation futur : préparation, pas commande

Il n'existe aucune méthode qui passe actuellement un trajet à ANNULE ni endpoint
d'annulation de trajet. Ne pas réutiliser l'annulation personnelle de réservation
pour cette mutation : elle ne doit pas être interdite par la garde de trajet.

Lorsque les décisions restantes seront fixées, le service devra, dans une même
transaction : vérifier le JWT et l'accès effectif avec ClientAuthorization ;
charger et verrouiller via JourneyLocks dans l'ordre zone, véhicule, trajet,
client ; relire après attente ; vérifier le propriétaire et les états/délais
validés ; réutiliser `requireCancellationPreservesJ1Debt` ; appliquer seulement
les conséquences approuvées, sans supprimer les historiques ni les dettes.
La trace sensible devra rester publiée après commit, comme les autres mutations.

Le contrôle J-1 actuel ne prend que les privés PLANIFIE. Le passer à ANNULE
avant son traitement peut supprimer sa candidature. À/après l'échéance, une
attente de verrou ou un rattrapage retardé impose donc encore un arbitrage : ni
dette anticipée, ni exemption, ni nouvelle pénalité ne sont supposées.

## Statuts Java et PostgreSQL

- StatutTrajet : PLANIFIE, COMPLET, ANNULE ; REALISE n'existe pas dans l'enum.
- V11 : CHECK sur ces trois états ; intervalle arrivée après départ, cohérence
  zone/véhicule et contrôles de conflits au changement de statut.
- V12 : nouvelle confirmation seulement sur PLANIFIE, maximum quatre, preuve de
  20 EUR et dette fixe de 80 EUR ; conversion J-1 cohérente avec le type PUBLIC.
- V15 : protections de flotte et disponibilités, dont le traitement particulier
  d'ANNULE. Un futur statut doit être revu avec ces triggers, pas ajouté isolément.
- V17 : réservation ANNULEE non réactivable, preuve de paiement historique
  conservée ; libération de confirmation rouvre COMPLET vers PLANIFIE.
- Les statistiques actuelles comptent explicitement les trois états connus ;
  les DTO et leurs indicateurs devront aussi être examinés avant REALISE.

Aucun enum, CHECK, trigger, projection, contrat JSON ni migration n'est modifié
dans ce lot. Un futur REALISE nécessitera une nouvelle migration validée ; V11
ne sera pas réécrite. Les états des réservations ne sont pas complétés par
supposition pour accompagner cette future transition.

## Précondition temporelle préparée

`BookingPolicy.requireAfterArrivalForCompletion` vérifie seulement que l'instant
courant fourni par Clock est **strictement après** l'arrivée enregistrée. Avant
et à l'instant exact, elle refuse avec le code `TRIP_ARRIVAL_NOT_PASSED` et le
message métier français générique existant. Après cet instant, son retour normal
n'autorise ni acteur ni état source, notamment pas ANNULE : ces décisions restent
à appliquer séparément par un futur service.

Il s'agit d'une comparaison d'Instant UTC, sans nouvelle heure de scheduler,
durée moyenne, conversion de calendrier local ou automatisation. La garde ne
change aucun état et n'appelle aucun repository. Aucun service ou contrôleur
actuel ne l'appelle. L'heure passée ne rend donc aucun trajet automatiquement
réalisé. Le futur appelant devra relire l'arrivée sous les verrous partagés.

## RM21 : candidats à examiner, aucune autorisation de modification

Le backend actuel n'autorise **aucun champ de trajet en modification**, même si
un champ apparaît dans le DTO de création. La liste ci-dessous est une analyse,
pas une liste blanche de mutation validée.

| Champ candidat | Contrôles disponibles | Restrictions et effets restant à définir |
|---|---|---|
| origine, destination | Libellés non vides de 255 caractères, normalisation existante V14 | Délai, information/accord des participants et sort des paiements si changement significatif |
| depart, arrivee | UTC, arrivée après départ, horizons, disponibilités et pause véhicule | Avant/après départ, trajet payé ou réservé, recalcul J-1, consentement et conséquences de réservations |
| vehiculeId | Véhicule actif, zone cohérente, indisponibilités, conflits et marge de quinze minutes | Acteur et période autorisés, incidence sur les participants et traces |
| zoneId | Droits D3 et cohérence avec le véhicule | Transfert de zone, droits des participants déjà inscrits, invitations, réservations et paiements |
| type PUBLIC/PRIVE | Propriété et règles existantes de visibilité et d'ajout privé | Public vers privé ou inverse, participants existants et dette J-1 ; aucune conversion libre validée |
| organisateurId, statut, traiteJ1Le, identifiants | Identité backend et transitions contrôlées | Aucun champ libre de formulaire ; transfert de propriété ou édition arbitraire non validés |

Une réservation EN_ATTENTE existe dès la création pour l'organisateur. « Sans
réservation » ne doit donc pas être présumé à partir de l'absence de participants
supplémentaires. Aucun trajet réservé n'est modifié dans ce lot.

## Qualité, périmètre et tests

Un seul lot de code est proposé : précondition temporelle non active de fin de
trajet. Annulation directe et RM21 restent des analyses indépendantes. Aucun
nouveau service inutile, endpoint, tarif, remboursement ou migration.

Les tests ciblés couvrent les quinze cas de la nouvelle garde et les vingt cas
existants de #44. La validation Maven complète utilise PostgreSQL Testcontainers.
Les résultats effectifs seront consignés avant commit et PR. Les worktrees,
bases locales, Angular, configurations, secrets et services lancés sont conservés.
La JavaDoc de becomePublic est replacée au-dessus de sa méthode, sans changement
de code, au lieu d'être attachée au champ de date J-1 voisin.

Fichiers de ce lot :

- `BookingPolicy.java` : ajout de la seule précondition temporelle préparatoire.
- `JourneyCompletionPolicyTests.java` : quinze nouveaux cas unitaires.
- `Trajet.java` : correction de placement de JavaDoc uniquement.
- `cycle-vie-messages-fr.md` : actualisation de la décision sur l'organisateur.
- `finalisation-backend-preparation.md` : audit, tableaux et limites de livraison.

Aucune migration n'est nécessaire à cette préparation. Les commandes actives
d'annulation, de modification et de réalisation restent à livrer dans des lots
séparés après arbitrage, avec leurs propres tests et PR.

## Résultats de validation du 27 octobre 2026

- Tests ciblés Maven Wrapper : **35 réussis**, 0 échec, erreur ou ignoré
  (15 nouveaux cas et les 20 de la garde J-1 existante).
- `mvnw.cmd --batch-mode --no-transfer-progress clean verify` : **BUILD SUCCESS,
  702 tests réussis, 0 échec, 0 erreur, 0 ignoré**, 17 min 22 s.
- Les **687 cas précédents** ont été retrouvés par classe et nom dans les rapports
  JUnit et sont tous verts. Aucun test existant supprimé, renommé ou désactivé.
- PostgreSQL 16.6 Testcontainers uniquement. Aucun changement des migrations
  V1–V18, enums, contrôleurs et services actifs de trajet/réservation/paiement.
- La seule occurrence de la nouvelle garde dans le code principal est sa
  déclaration. Aucun appel actif, aucune transition ni automatisation ajoutée.
- Le code exécutable de Trajet reste identique après retrait des JavaDoc ;
  la correction concerne seulement le commentaire de becomePublic.
- Le serveur 8081 conserve son processus existant. Aucun déploiement, aucune
  modification Angular ou des bases locales, secrets et configurations.

Les CI GitHub de branche et de PR sont contrôlées après publication ; les liens
finaux sont consignés dans la PR et le rapport de livraison. L'issue #26 reste ouverte.
