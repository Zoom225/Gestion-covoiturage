# Lot 6 — paiements simulés, dettes et J-1

Issue #27 ; dépend du contrat stabilisé du lot 5, lui-même dépendant du lot 4.
Les propositions du fichier preparation.md sont historiques ; D4 et D5 les remplacent.
La simulation est explicitement prévue par le cahier, section périmètre et parcours nominal. Aucun paiement bancaire réel n'est annoncé.

## Endpoints et sécurité

| Endpoint | Règle |
|---|---|
| POST /api/reservations/{id}/paiements | CLIENT propriétaire actif ; montant fixé à 20,00 EUR par le serveur ; Idempotency-Key UUID obligatoire ; réponse 200 contenant nature PLACE et mode SIMULATION |
| GET /api/dettes/me | CLIENT authentifié, seulement ses dettes ; pagination 0/20/100, ID croissant |
| POST /api/dettes/{id}/paiements | CLIENT propriétaire actif ; montant décimal strictement positif, au plus deux décimales, sans dépasser le restant dû ; Idempotency-Key UUID |

Les réponses n'exposent ni clé d'idempotence, ni mot de passe, hash, JWT, preuve d'invitation ou contenu d'outbox.
401 anonyme/inactif ; 403 rôle/zone ; 404 propriétaire absent/invisible ; 400 syntaxe/validation ;
409 état, capacité, dette, montant incompatible ou conflit de rejeu ; 503 validation de paiement indisponible.
Un booléen accepted/valide transmis par le navigateur n'est jamais une preuve.

## Paiement de place

1. Identifier le client en base depuis le JWT, contrôler la propriété de la réservation.
2. Verrouiller zone, véhicule, trajet puis client ; relire l'état après attente.
3. Retourner le résultat déjà enregistré pour un rejeu identique. Une autre clé sur une réservation payée est refusée.
4. Recontrôler activité, zone autorisée, horizon, dette, disponibilité, état PLANIFIE et moins de quatre CONFIRMEE.
5. Demander la validation au PaymentGateway serveur.
6. Enregistrer 20 EUR et confirmer dans la même transaction ; quatrième confirmation → COMPLET.
7. PostgreSQL contrôle au commit l'équivalence paiement enregistré / réservation CONFIRMEE.

L'organisateur reste EN_ATTENTE tant qu'il n'a pas payé. Un participant peut payer avant lui ; aucune place confirmée ne lui est réservée par priorité d'inscription. Même règle pour les participants privés.
Le verrou du trajet protège le premier paiement validé et la dernière place ; aucune validation retenue sans attribution.

ConfiguredPaymentGateway ne simule qu'avec dev actif, prod absent ET driveconnect.payments.simulation-enabled=true.
dev+prod est refusé. En dehors de ce mode, réponse 503 ; aucun prestataire inventé.
Une intégration bancaire future devra traiter autorisation/capture, webhooks, idempotence et compensation externe ; la transaction actuelle ne prétend pas englober un prestataire distant.

## Dettes et règlements

DetteTrajet du lot 5 est conservée : montant fixe 80 EUR, UNIQUE(trajet), organisateur vérifié par FK composite.
PaiementDette et PaiementPlace sont séparés, avec montants décimaux, UUID uniques et horodatage UTC.
Les tentatives refusées ne deviennent jamais des paiements persistés ; le modèle conserve les validations finales.
Les clés sont uniques par nature de paiement ; une même clé ne peut être réaffectée à une autre ressource de cette nature.

Règlement partiel : ACTIVE ; somme 80 EUR : REGULARISEE ; dépassement refusé. Aucun délai d'expiration.
Le verrou client puis dette sérialise les règlements et les contrôles de dette associés aux réservations.
Une contrainte différée rapproche le cumul des règlements du montant regle.
La régularisation supprime seulement le blocage de dette ; elle ne réactive jamais Compte ou Client.

## J-1 : D5

Cron quotidien 0 15 0 * * *, zone Europe/Brussels ; activation configurable par driveconnect.scheduling.j1.enabled.
L'échéance d'un trajet est 00:15 Europe/Brussels la veille de sa date de départ exprimée dans ce fuseau de traitement.
Les trajets PRIVE/PLANIFIE dont l'échéance est échue sont rattrapés, y compris après une interruption prolongée.
Le traitement relit l'état courant sous verrou ; quatre confirmés, PUBLIC, COMPLET ou ANNULE sont exclus.

Chaque candidat utilise une transaction indépendante : conversion PUBLIC, horodatage traite_j1_le et dette 80 EUR atomiques.
Le verrou commun avec les paiements, la relecture et l'unicité SQL empêchent double dette ou dépassement de capacité.
Une panne sur un candidat annule cette conversion et produit uniquement un avertissement fixe avec son ID ; les autres candidats continuent. Le candidat sera repris au prochain passage.
Un curseur sur les IDs évite les omissions liées au changement de type pendant une pagination.

## V12

- Ajout nullable de l'horodatage J-1, cohérent avec PUBLIC.
- Tables paiement_place et paiement_dette, FK, CHECK, UNIQUE et index.
- Contraintes différées paiement/confirmation et cumul/règlements.
- Aucun changement V1–V11 ni conversion de données existantes.
- Précontrôle bloquant si V11 contient déjà des CONFIRMEE sans preuve ou des dettes réglées sans historique : plan de rapprochement explicite requis, aucun paiement inventé.
- Migration testée seulement dans PostgreSQL Testcontainers, jamais appliquée à la base partagée.

## Preuves et limites TFE

Les contrats d'annulation, remboursement, réinscription et REALISE restent hors implémentation tant que non définis.
Les droits d'écriture des véhicules/indisponibilités demeurent ouverts au lot 4.
Aucune donnée fictive initialisée au démarrage, aucun compte réel ou e-mail créé.

RM25–RM45, RM48/RM50–RM55 et RM58–RM60 ; correction RM15/RM27/RM40 tracée dans D4.
AA1/AA2 : décisions et analyse ; AA3/AA4 : modèle et intégrité ; AA5 : responsabilités ;
AA6 : permissions ; AA7 : API ; AA9 : erreurs ; AA10 : concurrence/Testcontainers/CI ; AA11 : preuves.
AA8 Angular futur ; contributions partielles, aucune validation complète des AA revendiquée.
Résultats exécutés à consigner après clean verify.

```mermaid
sequenceDiagram
    participant C as Client JWT
    participant P as PaymentService
    participant G as Validation serveur
    participant DB as PostgreSQL
    C->>P: Payer sa reservation + cle UUID
    P->>DB: Verrous et relecture des regles
    P->>G: Valider 20 EUR (simulation explicite)
    G-->>P: Validation serveur
    P->>DB: Paiement + CONFIRMEE + eventuel COMPLET
    DB->>DB: Contraintes differees au commit
    P-->>C: Resultat mode SIMULATION
```

```mermaid
flowchart TD
    A[00:15 Europe/Brussels ou rattrapage] --> B[Candidats par ID]
    B --> C[Transaction et verrous communs]
    C --> D{Prive planifie incomplet et echeance echue ?}
    D -- Non --> E[Ignorer]
    D -- Oui --> F[PUBLIC + date de traitement + dette unique 80 EUR]
    F --> G[Commit atomique]
    F -- Echec --> H[Rollback et reprise future]
```

Modèle : Reservation 1—0..1 PaiementPlace ; DetteTrajet 1—N PaiementDette ; Trajet 1—0..1 DetteTrajet.

## Preparation du parcours client integre

GET /api/zones et GET /api/vehicules fournissent au CLIENT actif les seules zones D3 autorisees et leur flotte. Le filtre zoneId est intersecte ; SQL avant pagination et total, conventions 0/20/100. Service/DTO/mapper de referentiels reutilises, aucun droit d'ecriture ajoute. Ces lectures permettent le choix zone/vehicule avant POST /api/trajets sans utiliser les API administratives.

## Résultats réellement exécutés — 25 octobre 2026

- Validation ciblée initiale : deux échecs réels (syntaxe du test de panne et HTTP 400 d'une clé absente), corrigés sans désactiver de scénario.
- Paiements/J-1 ciblés après correction : 33 tests, aucun échec/erreur/ignoré.
- Première suite complète du cœur : 421 tests, aucun échec/erreur/ignoré, BUILD SUCCESS en 10 min 45 s.
- **Livraison finale intégrée : `mvnw.cmd --batch-mode --no-transfer-progress clean verify` → BUILD SUCCESS ; 425 tests, 0 échec, 0 erreur, 0 ignoré ; 7 min 44 s. JAR Spring Boot produit.**
- Les 383 tests du lot 5 sont conservés ; 37 cas PaymentIntegrationTests et 5 cas SimulationGatewayTests s'ajoutent.
- PostgreSQL 16.6 Testcontainers et HTTP réels : dernière place, rejeux, refus/rollback, J-1 à l'instant limite, rattrapage, concurrence J-1/paiement, dette unique, règlements concurrents, solde partiel/complet, FK/CHECK/preuves différées et migration V11→V12.
- Le test de migration refuse explicitement des CONFIRMEE historiques sans preuve, conserve ces lignes puis migre le scénario EN_ATTENTE approuvé ; aucune conversion silencieuse.
- Le mode simulé est refusé en prod, dev+prod, hors dev et lorsque désactivé.
- Catalogue CLIENT : zones/catégories, SQL avant total, filtre par intersection, désactivation, refus de mutation et 401/403.
- JWT, BCrypt, invitations chiffrées et Swagger non régressés ; liens internes et absence de fichiers privés suivis contrôlés.
- Aucune V12 appliquée à la base partagée : lecture seule confirme V4 ; volume driveconnect-dev_postgres-data conservé, PostgreSQL healthy.

| AA | Preuve livrée | Limite |
|---|---|---|
| AA1–AA2 | Règles D1–D5, lecture du cahier, RM et limites explicites | Mutations non définies isolées |
| AA3–AA4 | Modèle relationnel et V12 avec contraintes testées | EA complet à maintenir dans le dossier TFE |
| AA5 | Services, politiques, repositories, DTO et mappers | Aucun second mécanisme d'authentification |
| AA6 | JWT, propriété, scopes et prod exclu de la simulation | Aucun prestataire bancaire réel |
| AA7 | Paiement, dette, catalogue client et Swagger | Annulations/remboursements non spécifiés |
| AA8 | Contrats préparant Angular | Frontend non développé |
| AA9 | 400/401/403/404/409/503 et rollback | Pas de saisie Angular |
| AA10 | 425 tests locaux, Git et CI vérifiée séparément | Résultat CI à consulter sur le commit publié |
| AA11 | Contrats, séquence, activité, preuves et PR | Contribution partielle au TFE |

## Ordre d'intégration Git proposé

1. Revoir la PR #28 du lot 4 vers master ; aucune fusion par cette mission.
2. Après sa fusion autorisée, actualiser les références, intégrer le master actualisé dans la branche du lot 5 en préservant les fichiers locaux, puis recibler #29 vers master et revérifier la CI.
3. Après fusion autorisée du lot 5, faire de même pour la PR du lot 6 actuellement basée sur feature/trips-reservations.
Les worktrees sont conservés. Ne pas réinitialiser une branche ni forcer un push pour simplifier cette chaîne.

## Finalisation backend — mission 1, 26 octobre 2026

Références : #27 (paiements, issue déjà clôturée) et #31 (intégration Angular).
Le worktree existant `feature/payments-debts` est réutilisé : son backend est identique
au master distant `4a1d1a3`, sans merge, rebase ou changement de branche.

### Intégrité PostgreSQL : migration additive V13

V12 ne contrôlait que la nouvelle référence lors d'un UPDATE de paiement. Les deux
contre-exemples sont reproduits sur PostgreSQL Testcontainers : une ancienne
réservation CONFIRMEE sans paiement et une ancienne dette dont le solde ne correspond
plus aux règlements. Aucune réaffectation n'est exposée par les API actuelles.

`V13__validate_old_and_new_payment_references.sql` renforce les deux fonctions
existantes pour vérifier OLD et NEW, sans modifier V1–V12. Les quatre triggers de
contrainte restent `DEFERRABLE INITIALLY DEFERRED` : INSERT, UPDATE et DELETE sont
contrôlés sur l'état final de la transaction. Les parents concernés sont verrouillés
en ordre d'identifiant pour relire leur état ; un parent supprimé avec ses paiements
dans une même transaction cohérente ne provoque pas de faux refus.

La migration exclut les écritures pendant le précontrôle et le remplacement des
fonctions. Elle refuse un historique déjà incohérent, sans le réécrire ni fabriquer
de paiement. Une réconciliation explicitement autorisée serait alors nécessaire.
Les FK, UNIQUE, CHECK des montants, clés UUID, paiements de 20 EUR et dette de 80 EUR
restent conservés. Les réaffectations SQL qui préservent réellement les deux côtés
au commit ne sont pas interdites arbitrairement ; aucun droit d'annulation ou de
remboursement n'est ajouté.

### CORS : header Idempotency-Key

`Idempotency-Key` est ajouté à la liste explicite des headers CORS autorisés de
`/api/**`, avec Authorization et Content-Type. Les origines configurées, méthodes,
JWT stateless, contrôles de rôle et `allowCredentials=false` restent conservés.
Un simple HTTP 200 de prévol ne suffit pas : les tests vérifient aussi que
`Access-Control-Allow-Headers` autorise effectivement la clé d'idempotence.

### Validation et limites

`PaymentIntegrityIntegrationTests` couvre la reproduction V12, le refus d'une
reprise incohérente, la conservation des lignes/checksums lors de V12→V13, les
anciennes et nouvelles références, INSERT/UPDATE/DELETE, les transitions SQL
cohérentes différées et les contraintes existantes. `PaymentIntegrationTests`
réutilise le backend HTTP/PostgreSQL réel pour les prévols, POST authentifiés et
rejeux des deux types de paiement ; 401, 403, JWT invalide, origines et headers
non autorisés sont contrôlés. Les tests existants de dernière place, J-1 et
concurrence restent actifs.
L'assertion de version de `CovoiturageApplicationTests.contextLoads` attend
désormais V13 ; la validation Flyway et l'absence de migration rejouée restent
vérifiées, sans désactiver de test.

Commandes PowerShell exécutées depuis la racine de la copie temporaire :

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=PaymentIntegrityIntegrationTests,PaymentIntegrationTests,SimulationGatewayTests,SecurityIntegrationTests' test
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
```

Toutes les exécutions utilisent une copie temporaire et PostgreSQL Testcontainers.
Aucune V13 n'est appliquée à `carshare` ni `carshare_dev_v12` ; le backend local
déjà démarré n'est pas redémarré par cette mission. Aucun frontend n'est modifié.
Validation finale du 26 octobre 2026 : `clean verify` terminé à 11:34:04 CET,
`BUILD SUCCESS` en 10 min 40 s, JAR Spring Boot construit. Les 464 tests passent,
sans échec, erreur ou test ignoré : les 425 tests existants sont conservés,
avec 29 nouveaux cas d'intégrité SQL et 10 cas CORS. Les suites paiements
(47 tests), intégrité SQL (29), sécurité (26) et simulation (5) sont vertes.
Les tests ciblés initiaux étaient également verts. Aucune CI distante n'est
annoncée pour ces changements non publiés.

Fichiers concernés : la migration V13, `security/SecurityConfiguration.java`,
`PaymentIntegrityIntegrationTests.java`, `PaymentIntegrationTests.java`,
`CovoiturageApplicationTests.java` et ce document. Les fichiers Java sont situés
sous `src/main/java/com/plateforme/covoiturage` ou
`src/test/java/com/plateforme/covoiturage` selon leur rôle.

Contrôles de conservation : les 14 branches/HEAD et index Git sont identiques
à l'inventaire initial, dont les 44 fichiers Angular indexés. Les empreintes
des sauvegardes PostgreSQL en lecture seule de `carshare` (V4) et
`carshare_dev_v12` (V12) sont identiques avant et après les tests. Les douze
migrations V1–V12 restent inchangées. Aucun commit, push, merge ou rebase.

Exigences renforcées : RM27/RM31/RM40/RM50/RM58, contrats REST de paiement et
AA4/AA6/AA7/AA9/AA10/AA11. Les droits d'écriture des véhicules/indisponibilités,
annulations, remboursements, réinscriptions, REALISE et statistiques restent à
traiter séparément après les arbitrages nécessaires.
