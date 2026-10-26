# Finalisation backend — historique, statistiques et traçabilité

Base : master après fusion de la PR #34, `456cbe4a47e01d06b70c6edefa752c66c43187d2`.
Branche locale : `feature/backend-historique-statistiques-tracabilite`.
Issues : #35 (historique), #36 (statistiques), #37 (journalisation).

## Références et décisions

Le cahier harmonisé, sections 4.10/4.11, 17, 24.2/24.7, et le registre final
RM49/RM51/RM54/RM57/RM60 imposent les périmètres administratifs, la séparation
des paiements et la traçabilité. D1–D5 demeurent applicables.
L'historique personnel ne donne accès qu'aux paiements du client authentifié.
Les statistiques portent sur les états réellement persistés ; aucun indicateur
de remboursement ou de REALISE n'est annoncé.

| Priorité auditée | Existant et résultat | Décision restante |
|---|---|---|
| Véhicules / indisponibilités (#25) | Lecture par périmètre, AvailabilityService, JourneyLocks, contraintes de dates/zone/15 min | Droits d'écriture explicitement ouverts dans D1–D5 ; proposition GLOBAL réseau / LOCAL sa zone soumise au responsable |
| Annulation trajet/réservation (#26) | États SQL prévus, aucun endpoint de mutation | Acteurs, échéance, effets sur organisateur/participants et paiements |
| Remboursement (#26) | Preuve différée V13 lie paiement validé et confirmation | Politique, montant, droits et modèle de restitution ; aucune suppression de paiement proposée |
| Réinscription (#26) | UNIQUE(trajet,client), absence de réinscription automatique | Réactivation ou nouvelle réservation, traitement du paiement antérieur |
| REALISE (#26) | État absent de l'enum et du CHECK courant | Déclencheur et acteur/scheduler ; le cahier final le dit indicatif |
| Historique (#35) | Lecture personnelle paginée ajoutée | Historique des seules validations, pas des tentatives non persistées |
| Statistiques (#36) | Compteurs et sommes persistés ajoutés | Graphiques et statistiques avancées hors lot |
| Traçabilité (#37) | Réussites après commit ajoutées | Exploitation, rétention et centralisation des logs à configurer ultérieurement |

Les mutations de flotte ne sont pas déduites des seuls droits de lecture.
Les conflits de planning/15 min restent couverts par les tests existants.
La gestion d'une indisponibilité future devra verrouiller zone puis véhicule et
refuser une incompatibilité, sans annulation/remboursement implicite.

## Contrats API

`GET /api/paiements/me` : CLIENT uniquement ; ReferencePageRequest (0/20, max 100).
Réponse ReferencePageResponse de PaymentResponse existants : paiementId, nature
PLACE/DETTE, ressourceId, montant EUR, mode et valideLe. La nature distingue
les deux espaces d'identifiants. Tri date décroissante, nature croissante puis
identifiant décroissant. Le SQL filtre le propriétaire avant UNION ALL,
pagination et total ; transaction REPEATABLE_READ pour la page et son comptage.
Un paramètre clientId ne permet jamais de choisir un autre propriétaire.

`GET /api/admin/statistiques?zoneId=...` : ADMIN GLOBAL réseau ou zone filtrée ;
LOCAL exclusivement sa zone. Le filtre est une intersection et un périmètre
vide renvoie des zéros. Tous les indicateurs dérivent de la zone du trajet,
y compris les réservations VIP/OCCASIONNEL et les dettes de l'organisateur.

- trajetsPlanifies / trajetsComplets / trajetsAnnules ;
- reservationsEnAttente / reservationsConfirmees / reservationsAnnulees ;
- paiementsPlaces / paiementsDettes / dettesRestantes : montants EUR décimaux.

Les cumuls couvrent toutes les lignes persistées autorisées, sans filtre de
date implicite. Une unique instruction PostgreSQL avec CTE partage le même
snapshot MVCC et évite la multiplication des montants par jointures.
Les endpoints renvoient Cache-Control: no-store ; JWT absent/invalide : 401,
rôle interdit : 403, pagination/zone invalide : 400.
Aucune route de mutation ni lecture d'un autre client n'est ouverte.

## Journal des réussites

SensitiveOperationJournal enregistre une synchronisation de transaction et
écrit après commit uniquement. Le rollback, y compris un refus des preuves
PostgreSQL différées au commit, n'émet aucune réussite. Un rejeu de paiement
renvoie la validation existante sans nouveau paiement ni événement.

Les traces contiennent des actions et types de ressources énumérés, des
identifiants techniques, la référence liée si nécessaire et l'horodatage.
L'acteur provient du principal relu en base ou de l'identité établie après
consommation de l'invitation. SYSTEME distingue bootstrap et traitement J-1.
Aucun objet de requête, nom, adresse email, mot de passe, JWT, jeton d'invitation
ou clé d'idempotence n'est transmis au journal.

Opérations couvertes : paiement de place, confirmation et passage COMPLET ;
règlement partiel/intégral de dette ; conversion J-1 et création de dette ;
création/configuration/activité de zone ; habilitation VIP avec références
zone/client ; création de comptes, renouvellement d'invitation et activation.
Les bascules d'activité et habilitations sans changement ne sont pas retracées.
Les fonctions administratives encore absentes devront utiliser ce composant.

Les logs applicatifs ne constituent pas un journal d'audit persistant ou
infalsifiable. Aucune politique de rétention ni garantie de livraison externe
n'est inventée ; leur exploitation relève de la configuration de déploiement.

## Intégrité et validation

Aucune migration supplémentaire : V1–V14 sont conservées intégralement.
Controller → Service → Repository, DTO et mappers existants sont réutilisés.
La politique CORS, BCrypt et JWT stateless sont conservés.
Les tests utilisent PostgreSQL 16.6 Testcontainers et des ports HTTP aléatoires.
Aucune base carshare/carshare_dev_v12 ni serveur 8081 n'est utilisé ou redémarré.
Angular et ses 44 fichiers indexés restent intacts.

Validation finale locale du 26 octobre 2026 :

- ciblés : BUILD SUCCESS, 31 tests (11 historique, 9 statistiques, 11 journalisation),
  aucun échec/erreur/ignoré, 2 min 22 s ;
- dernier clean verify : BUILD SUCCESS, 541 tests dans 26 suites, zéro échec,
  zéro erreur et zéro ignoré ; terminé à 14:47:04 CET en 9 min 31 s, JAR construit ;
- comparaison des cas Surefire : les 510 tests antérieurs sont tous conservés,
  31 nouveaux cas et aucun ancien cas manquant ;
- aucune erreur Docker HTTP 500 ni sortie forcée Surefire sur ce dernier passage.

Les sources validées sont reportées par empreintes SHA256 dans le worktree dédié.
Les tests sont exécutés sur copie temporaire isolée ; les logs et rapports
Surefire sont conservés hors dépôt. Aucun secret privé n'est inclus.
Les 14 worktrees préexistants et l'index Angular sont comparés à l'inventaire initial.
Aucun commit, push ou PR n'est effectué pour ce lot ; la proposition française
figure dans [proposition-pr.md](proposition-pr.md). La CI distante de ce lot
ne pourra être lancée qu'après autorisation de publication.

### Cycle de vie des tests PostgreSQL

Le premier clean verify exécute bien les 541 cas avec BUILD SUCCESS, mais le
nettoyage des contextes en cache dépasse les 30 secondes de sortie de Surefire.
Une première reprise rencontre ensuite une réponse HTTP 500 du moteur Docker
pendant la création de deux conteneurs : 513 cas recensés, 48 erreurs de
chargement des contextes, aucune assertion métier échouée, BUILD FAILURE.
Cette reprise ne constitue pas une validation réussie.

La configuration finale borne le cache de contextes Spring à deux dans
`src/test/resources/spring.properties` et ferme les nouveaux contextes après
chaque classe. Cela libère les pools et conteneurs en cours de suite, sans
augmenter le délai Surefire, désactiver Flyway ou filtrer les tests.
La fabrique TestcontainersConfiguration.disposablePostgres est réutilisée
par le conteneur Spring et PaymentIntegrityIntegrationTests : PostgreSQL 16.6,
trois tentatives de création/démarrage au maximum, aucune répétition
d'assertion métier. Aucun réglage n'est ajouté à l'application de production.

Le lot contient 24 fichiers : 15 sources backend, 7 fichiers de tests/ressources
de test et 2 documents. L'état final est validé intégralement par les résultats
ci-dessus. Les adaptations de support de test ne changent aucune assertion
antérieure et ne sont pas livrées dans le JAR applicatif.

Commandes de validation dans la copie temporaire isolée :

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=PaymentHistoryIntegrationTests,AdminStatisticsIntegrationTests,SensitiveOperationJournalIntegrationTests' test
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
```

## Contributions aux acquis

AA1/AA2 : analyse et arbitrages explicites ; AA3/AA4 : requêtes et invariants
persistés ; AA5 : couches réutilisées ; AA6 : contrôle des périmètres et secrets ;
AA7 : REST et OpenAPI ; AA9 : erreurs et traces ; AA10 : HTTP/PostgreSQL et
non-régression ; AA11 : contrats et preuves. Contributions partielles : AA8
nécessite le lot Angular, sans modification du frontend pendant cette mission.
