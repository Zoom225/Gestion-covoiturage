# Décisions métier D1–D5 — validées le 25 octobre 2026

Cette décision du responsable remplace les cinq propositions non validées du premier audit.
Les instantanés antérieurs restent historiques, sans renumérotation des RM.

| Décision | Règle validée | Réalisation |
|---|---|---|
| D1 | GLOBAL crée, configure et désactive les zones ; LOCAL lit sa zone. Aucune suppression physique référencée. | Lot 4 |
| D2 | Départ/arrivée obligatoires, arrivée strictement après départ, UTC, affichage dans le fuseau de la zone ; intervalle minimal 15 min par véhicule et indisponibilités. | Socle fuseau/indisponibilités lot 4 ; trajets lot 5 |
| D3 | VIP : autorisations explicites ; LOCAL : sa zone ; OCCASIONNEL : zones explicitement ouvertes. | Politique backend commune lot 4 |
| D4 | Organisateur première place logique EN_ATTENTE. Tous les participants, y compris organisateur et privés, sont confirmés seulement après paiement validé de 20 €. EN_ATTENTE ne garantit aucune place. Premier paiement validé gagnant ; maximum 4 confirmés. | Lots 5/6 |
| D5 | Tous les jours 00:15 Europe/Brussels ; rattrapage, idempotence et concurrence ; privé incomplet devient public, dette unique fixe 80 € pour organisateur. Aucune expiration, aucune pénalité d'une semaine. | Lot 6 |

## Correction explicite RM15 / RM27 / RM40

RM15 du cahier décrivait une confirmation immédiate de l'organisateur à la création.
**D4 remplace cette confirmation immédiate** : seule la place logique est créée alors.
RM27 et RM40 demeurent applicables : aucune confirmation sans paiement validé.
Aucun paiement fictif ou place garantie n'est produit par le service de réservation du lot 5.

## Lot 4 : contrat stabilisé

- POST /api/admin/zones : nom, fuseauHoraire IANA et ouverteOccasionnels obligatoires ; GLOBAL ; 201.
- PUT /api/admin/zones/{id} : configuration complète ; GLOBAL ; 200.
- PATCH /api/admin/zones/{id}/statut : actif booléen obligatoire ; GLOBAL ; 200.
- PUT /api/admin/zones/{id}/clients-vip/{clientId} : autorisation explicite idempotente ; GLOBAL ; 204.
- DELETE sur le même chemin : retrait idempotent ; GLOBAL ; 204.
- Six GET administratifs existants conservés, SQL avant pagination/comptage.
- JWT absent : 401 ; acteur non autorisé : 403 ; ressource absente : 404 ; données invalides : 400 ; conflit métier/SQL : 409.
- V10 ajoute activité, fuseau et ouverture OCCASIONNEL, ainsi que les autorisations VIP avec PK/FK/index et contrôle de catégorie.
- Les zones existantes restent actives, sans fuseau inventé, fermées aux OCCASIONNEL faute de configuration explicite. Les futurs trajets exigent la configuration du fuseau.
- ZoneAccessPolicy est la seule politique d'accès client à une zone pour les lots suivants.
- AvailabilityService contrôle les périodes [début, fin) ; contact des bornes permis ; chevauchement strict interdit.
- Pour sérialiser les futurs trajets : verrou de zone puis de véhicule. Les futurs mutateurs d'indisponibilités devront prendre les mêmes verrous et revalider les trajets.

## Limites et décisions encore ouvertes

D1 ne définit pas les droits d'écriture des véhicules et indisponibilités : aucun POST/PUT/PATCH/DELETE correspondant n'est exposé.
Le modèle existant reste une flotte rattachée à une zone, sans propriété CLIENT inventée.
Aucun changement libre de zone/catégorie client n'est ajouté ; création LOCAL avec sa zone via CompteService conservée.
Annulation, remboursement, réinscription après annulation et clôture/REALISE restent à préciser avant leurs mutations.
La visibilité des détails privés est limitée aux participants ; aucune liste de personnes n'est rendue publique.

## Traçabilité et preuves TFE

RM01–RM04, RM07/RM12, RM19/RM20/RM56, RM49/RM51, RM58–RM60 ; D4 explicite l'évolution de RM15/RM27/RM40.
AA1/AA2 : règles et arbitrages ; AA3/AA4 : modèle et contraintes ; AA5 : couches et politique réutilisée ;
AA6 : autorisations ; AA7 : REST/Swagger ; AA9 : erreurs ; AA10 : tests PostgreSQL/HTTP/CI ; AA11 : documentation.
Contributions partielles ; AA8 Angular reste futur. Résultats réels à consigner après exécution.

## Validation du lot 4 actualisé

Exécution locale du 25 octobre 2026 : `mvnw.cmd --batch-mode --no-transfer-progress clean verify` : **BUILD SUCCESS, 349 tests, 0 échec, 0 erreur, 0 ignoré ; 4 min 41 s**. Les 334 tests antérieurs restent présents ; 15 cas supplémentaires couvrent écritures de zones, accès VIP/LOCAL/OCCASIONNEL, désactivation, 400/401/403/404/409 et bornes d’indisponibilités. V1–V9 inchangées ; V10 testée uniquement dans PostgreSQL Testcontainers. Aucune initialisation ou migration partagée. Le premier essai a échoué sur une syntaxe V10, corrigée avant cette exécution complète réussie.
