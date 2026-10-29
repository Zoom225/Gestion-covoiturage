# Rapport de validation finale du backend DriveConnect

Date : 29 octobre 2026. Périmètre : backend existant avant Angular.
Cette synthèse actualise les anciens rapports de lots, qui décrivent leur état à leur date.

## Architecture et présentation EPHEC

Application monolithique Java 21, Spring Boot 4.1.1, PostgreSQL 16.6 et Maven.
Un Controller reçoit la requête HTTP et valide son DTO. Le DTO transporte uniquement les champs autorisés.
Le Service applique les règles métier dans une transaction. Le Repository lit et écrit PostgreSQL via JPA.
Les mappers construisent les réponses sans exposer les entités ni les secrets.

JWT identifie le compte ; signature HS256, expiration, émetteur et audience sont vérifiés.
Le compte et ses droits sont relus en base à chaque authentification : un rôle fourni par le navigateur n'accorde aucun droit.
Flyway fait évoluer le schéma ; Hibernate le valide, sans le recréer.
Les schedulers exécutent J-1 et REALISE. Les tests vérifient les réponses, les écritures, les refus et la concurrence sur PostgreSQL réel.

Les écritures partagent JourneyLocks : zone, véhicules triés, trajet, clients triés.
La relecture après attente évite de décider sur un état périmé. Les contraintes et triggers PostgreSQL complètent les transactions Java.
SensitiveOperationJournal ne publie les réussites qu'après commit.

## Fonctionnalités terminées dans le périmètre existant

| Domaine | Comportements examinés | Preuves principales |
|---|---|---|
| Comptes | Création administrative, matricules, activation par invitation, connexion, profil, droits courants | AccountWorkflowHttpIntegrationTests, AccountIdentifierIntegrationTests, InvitedAccountIntegrationTests, InvitationIntegrationTests |
| Zones/flotte | Lecture filtrée, configuration GLOBAL, accès VIP, flotte GLOBAL/LOCAL, activation, indisponibilités exclusives | ReferentielIntegrationTests, FleetManagementIntegrationTests, FleetMigrationIntegrationTests |
| Trajets | Création, lieux, visibilité publique/privée, pagination/zone, modification organisateur | TrajetIntegrationTests, TrajetGeographyIntegrationTests, TripUpdateIntegrationTests |
| Réservations | EN_ATTENTE puis CONFIRMEE après paiement, capacité quatre, droits, annulation individuelle, nouvelle réservation historique | TrajetIntegrationTests, RebookingIntegrationTests, ReservationCancellationIntegrationTests |
| Paiements/dettes | 20 EUR par place, UUID idempotent, historique propriétaire, dette 80 EUR, règlements partiels/intégraux | PaymentIntegrationTests, PaymentIntegrityIntegrationTests, PaymentHistoryIntegrationTests |
| Annulation/remboursement | Avant départ, 20 EUR à H-24 inclus ou avant, aucun après, date conservée, pas de double remboursement | ReservationCancellationIntegrationTests, CancellationIntegrityIntegrationTests |
| J-1 | 00:15 Europe/Brussels, quatre confirmations actives à l'échéance, contrôle historisé, conversion PUBLIC et dette unique sinon | J1HistoryIntegrationTests, PaymentIntegrationTests |
| REALISE | À l'arrivée incluse pour PLANIFIE/COMPLET avec confirmation active, J-1 rattrapé avant clôture, demandes résiduelles annulées et conservées | TripCompletionIntegrationTests, TripCompletionSchedulerTests, JourneyCompletionPolicyTests |
| Administration | Comptes/invitations, zones/VIP, flotte/indisponibilités, statistiques | AdminCompteIntegrationTests, AdminStatisticsIntegrationTests, FleetManagementIntegrationTests |

La place de l'organisateur compte parmi les quatre et n'est jamais confirmée gratuitement.
L'éligibilité reprend les catégories VIP/LOCAL/OCCASIONNEL, les zones autorisées, l'activité, les dettes actives et les horizons J-30/J-10/J-3.
Un véhicule doit être actif, de la bonne zone, disponible et sans chevauchement. La pause de quinze minutes est une borne de 900 secondes, testée à 899 et 900.
Réservation et paiement sont refusés dès le départ. Les courses concurrentes ne dépassent pas quatre confirmations.
Les paiements restent conservés après annulation, remboursée ou non. Le remboursement partage la transaction d'annulation.
Les règlements d'une dette ne réactivent pas un compte désactivé.

J-1 reconstitue l'état à l'échéance à partir des paiements et dates d'annulation, plutôt que du compteur courant.
Un contrôle réussi reste enregistré et n'engendre aucune dette lors d'une annulation ultérieure.
Le scheduler REALISE appelle d'abord le rattrapage J-1 et s'exécute par défaut toutes les 60 secondes ; le scheduler J-1 quotidien garde son horaire 00:15.
Les traitements sont idempotents, transactionnels par trajet et repris après erreur. Les anciens cas financièrement invérifiables sont signalés ; aucune date ou dette n'est inventée.

## Modification de trajet et traitement des PR

PR #55 : correction c50c0cd1a6b875932890132d98c7037aee90333a.
La règle manquante était déjà préparée dans le travail local initial ; elle a été relue, testée, commitée et poussée.
Une réservation active EN_ATTENTE, organisateur inclus, fige les lieux. Les valeurs identiques normalisées restent autorisées ; les annulations sont exclues de cette garde.
Les horaires/véhicules restent modifiables sous les autres règles, sans annulation automatique d'une demande.
Les refus conservent un instantané identique des données métier et financières. Les confirmations et paiements historiques continuent de verrouiller la modification.
42 tests ciblés réussis, dont concurrence réservation/paiement.

Fusionnée dans master au commit 38df2367f46dfaac3cc49ac434f998d64557e329 après réussite locale et CI ; aucun fil de revue non résolu.

PR #47 : comparaison du commit 2f12ebb avec master et de ses cinq fichiers.
La garde temporelle, ses tests et son repositionnement de JavaDoc sont remplacés par la PR #53.
Ses documents préparatoires décrivent comme ouvertes des règles désormais livrées ; ils ne constituent pas une fonctionnalité manquante.
Sa garde strictement après arrivée contredirait la borne incluse actuelle. Clôturée sans fusion, avec commentaire justificatif.

## Sécurité et audit technique

- Controller → Service → Repository conservé ; DTO validés, pas de refonte générale ni de nouvelle dépendance.
- BCrypt, JWT stateless, rôles CLIENT/ADMIN, périmètres GLOBAL/LOCAL, filtrage avant pagination et vérification de propriété conservés.
- Erreurs centralisées : 400 validation, 401 authentification, 403 autorisation, 404 ressource absente/masquée, 409 conflit, 503 simulation/livraison indisponible, 500 technique générique.
- AuthService ne distingue publiquement ni compte inconnu, ni mot de passe erroné, ni compte désactivé. Les mots de passe, clés et preuves ne figurent pas dans les DTO de sortie.
- Swagger/OpenAPI accessibles en dev seulement ; tests de fermeture prod et dev+prod. Aucun DELETE trajet ajouté.
- CORS est configuré explicitement via CORS_ALLOWED_ORIGINS ; seuls les en-têtes et méthodes prévus sont acceptés, sans cookies.
- Transactions et verrous existants réutilisés. Les tests d'intégrité vérifient aussi les preuves financières différées et les écritures SQL concurrentes.
- Anciennes migrations conservées ; aucune migration nouvelle requise pour cette mission.
- Recherche ciblée de motifs de clés privées/jetons dans les fichiers du projet : aucun motif trouvé ; fichiers locaux de secrets ignorés, exemples seuls versionnés. Cette recherche ne remplace pas une analyse exhaustive de tout l'historique Git.
- Les dépendances principales correspondent aux usages observés : HTTP, validation, sécurité, JPA, migrations, mail des invitations, OpenAPI et tests. Aucun retrait spéculatif.
- Le workflow existant teste le backend ; aucun scan automatisé de vulnérabilités des dépendances n'y est configuré. La CI verte n'est donc pas présentée comme un certificat de sécurité exhaustif.

## Tests et résultat Maven

`mvnw.cmd clean verify` exécuté le 29 octobre : **BUILD SUCCESS, 792 tests, 0 échec, 0 erreur, 0 ignoré**, durée 22 min 34 s, JAR généré.
Paramètres propres à ce poste : MAVEN_OPTS=-Xmx384m et `-DargLine="-Xmx640m -Dspring.test.context.cache.maxSize=2"`. Ils limitent la mémoire et le cache des contextes ; aucun test ni assertion retiré. Les anciennes tentatives locales interrompues par la JVM ne sont pas comptées comme réussies.
Les sources, tests et pom de master actualisé sont identiques au commit testé (diff vide). Cette validation complète reste applicable à la branche finale, qui ne contient que les documents de recette.

Les contrôles HTTP métier utilisent JWT, serveur Spring réel sur port aléatoire et PostgreSQL Testcontainers, sans toucher aux données de développement.
Ils couvrent activation/connexion, référentiels, flotte, création/recherche par zone, réservation, paiement, annulation/remboursement, historique, modification et administration autorisée/interdite.
Les migrations de mise à niveau et les bornes temporelles sont également testées. Aucun test désactivé pour obtenir le résultat.

## Démarrage local et CORS

JAR démarré en profil dev sur le port libre 18081, en chargeant l'environnement local sans l'afficher. L'instance existante 8081 n'est pas arrêtée.
Flyway : 21 migrations validées, mise à niveau locale V18 → V21 par les trois migrations existantes ; aucune ligne supprimée par ces migrations additives.
`Started CovoiturageApplication` confirmé. Contrôles HTTP : OpenAPI et Swagger 200 ; /api/auth/me et /api/admin/comptes sans JWT 401.
Prérequête OPTIONS /api/trajets depuis http://localhost:4200 : 200, origine et en-têtes Authorization/Content-Type/Idempotency-Key autorisés. Origine étrangère : 403.
La configuration privée locale ne déclarait pas l'origine Angular : CORS_ALLOWED_ORIGINS=http://localhost:4200 a été fourni au processus de validation et doit être conservé dans la configuration locale du futur lancement. Aucun défaut du mécanisme CORS n'a été constaté.

Le port PostgreSQL 15432 et compose.yaml personnalisé sont conservés. Aucun volume supprimé, aucune donnée réinitialisée, aucun secret affiché.
Les scénarios métier avec écritures sont exécutés sur les bases éphémères de tests ; le contrôle de démarrage local n'ajoute pas de compte ou de paiement de démonstration à la base existante.

## GitHub Actions et livraison

CI de la PR #55 sur le dernier commit : [Backend #99](https://github.com/Zoom225/Gestion-covoiturage/actions/runs/40915366961), réussie.
Journal : 792 tests, 0 échec, 0 erreur, 0 ignoré, BUILD SUCCESS ; rapports publiés.
CI de la branche finale : en attente du push documentaire ; les preuves seront liées dans la PR avant remise.

Issue : [#56](https://github.com/Zoom225/Gestion-covoiturage/issues/56). Branche : chore/final-backend-validation.
La dernière PR est laissée ouverte pour examen ; aucune fusion autorisée par cette mission.

## Fonctionnalités partielles et limites pour Angular

- Inscription par invitation uniquement : pas d'inscription publique libre.
- Recherche de trajets par zone et pagination ; pas de filtre serveur date/lieu/statut/organisateur.
- « Mes trajets » nécessite actuellement de filtrer les trajets visibles récupérés sur toutes les pages. L'extension minimale éventuelle est un filtre serveur sur l'organisateur connecté, à valider avant ajout.
- Ajout privé par clientId existant, sans annuaire public. Une sélection conviviale par matricule nécessiterait une décision de confidentialité.
- Les véhicules appartiennent à la flotte des zones ; aucun véhicule personnel client.
- Le détail ne contient ni liste des participants ni compteur de places disponibles.
- Les statistiques existantes ne comptent pas REALISE et ne déduisent pas les remboursements des encaissements bruts.
- L'administration expose les statistiques financières agrégées, pas une liste détaillée des paiements/dettes d'autrui.
- Les traces sont des logs après commit, sans endpoint de consultation, rétention garantie ni journal d'audit infalsifiable.
- Les anciens historiques J-1 incertains sont protégés ; aucune résolution administrative exposée.
- La création de compte invité nécessite la configuration de livraison existante ; sans elle, le backend refuse avec 503 plutôt que prétendre envoyer une invitation.

Ces limites sont documentées dans [le contrat API](../frontend/contrat-api-angular.md) et [le plan des pages](../frontend/plan-angular.md). Elles ne sont pas déclarées comme des fonctionnalités terminées.

## Exclusions volontaires

Annulation complète d'un trajet par l'organisateur, intégration bancaire réelle, Angular, microservices, Kafka, Redis et refactoring général.
Aucun endpoint inventé pour compléter artificiellement la liste de pages.

## Anomalies et conclusion

La garde sur les lieux EN_ATTENTE est finalisée dans #55. Aucun autre défaut reproductible nécessitant une correction applicative n'a été établi pendant cet audit.
Aucun blocage applicatif essentiel établi pour commencer Angular sur le contrat existant. **BACKEND PRÊT POUR ANGULAR**, pour les parcours documentés et la simulation en dev ; aucune disponibilité de paiement bancaire en production n'est annoncée. La configuration de livraison des invitations reste une condition d'exploitation du parcours d'inscription.
