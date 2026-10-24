# Matrice des permissions — gestion des comptes

**Préparation du parcours complet — issue [#23](https://github.com/Zoom225/Gestion-covoiturage/issues/23) :** socle interne d'initialisation transactionnelle et outbox chiffrée en préparation. Les nouveaux endpoints, créations administratives et bootstrap ne sont pas livrés : droits LOCAL (P05/P07), activités initiales (P09) et politique minimale du mot de passe (P08) attendent un arbitrage. Voir [état réel, sécurité et limites](parcours-invitations-preparation.md).


**Actualisation — lot 3C.2 / issue [#21](https://github.com/Zoom225/Gestion-covoiturage/issues/21) :** DEC-COMPTES-005 livre le service interne de preuves d'invitation (32 octets, SHA-256, durée configurable, révocation/renouvellement et consommation atomique). Aucun endpoint, e-mail, mot de passe ou compte créé. Voir [modèle, concurrence, tests et limites AA](lot-3c2-invitations.md).


**Actualisation — lot 3C.1 / issue [#19](https://github.com/Zoom225/Gestion-covoiturage/issues/19) :** DEC-COMPTES-004 ajoute INVITE/INITIALISE. Compte invité sans hash, accès refusé y compris par JWT ; INITIALISE et activités sont indépendants. Les comptes préexistants restent INITIALISES. Seul le socle est livré ; jetons, e-mails et création administrative restent futurs. Voir [modèle, migration V5 et preuves](lot-3c1-comptes-invites.md).


**État courant — lot 3B / issue [#15](https://github.com/Zoom225/Gestion-covoiturage/issues/15) :** consultation administrative implémentée selon les règles validées le 24 octobre 2026. GLOBAL consulte le réseau ; LOCAL seulement les comptes CLIENT de sa zone, sans visibilité des comptes ADMIN. Pagination 0/20/100, ordre compteId croissant, recherche exacte sans casse, filtres par intersection et même 404 pour absent/invisible. Les propositions du lot 3A ci-dessous sont historiques lorsqu'elles sont remplacées par cet état. Voir [architecture, contrat et preuves TFE](lot-3b-consultation.md).

**État courant — lot 2 / #11 :** POST login accepte l'e-mail ADMIN ou le matricule CLIENT sans distinction de casse ; GET me conserve les droits issus de PostgreSQL. V4 impose stockage canonique et cohérence Client/Compte sans réécrire les anciens comptes. Les formulations antérieures décrivent l'historique. Voir [contrat livré et tests AC-AUTH](lot-2-authentification.md).


**Actualisation lot 1 / DEC-COMPTES-003 :** la cible est e-mail ADMIN et matricule CLIENT ; les descriptions antérieures d'un login client indépendant sont historiques. Ce lot livre uniquement les séquences et l'historique, sans changer login/me ou les permissions. Les tests automatisés AC-NUM01–AC-NUM12 sont décrits dans le [dossier du lot](lot-1-numerotation.md) ; ils sont distincts des scénarios documentaires ci-dessous.


[Décisions et références](decisions.md) · [Contrats proposés](contrats-api.md) · [Tests](tests-acceptation.md).
Issue [#7](https://github.com/Zoom225/Gestion-covoiturage/issues/7), 21 octobre 2026.

Cette matrice distingue droits validés et parcours futurs. Login/me et les deux GET administratifs de consultation sont livrés au lot 3B ; aucune création ou mutation administrative ne l'est.

**Actualisation documentaire — lot 3A / issue [#13](https://github.com/Zoom225/Gestion-covoiturage/issues/13), 24 octobre 2026 :** base origin/master 39a7b6b (PR #10 et #12 fusionnées). Aucun endpoint administratif livré par ce lot. Le matricule CLIENT est le login généré ; l'e-mail de connexion ADMIN est distinct du contact CLIENT. Les droits de lecture confirmés et les modalités encore ouvertes sont séparés dans les [contrats de consultation](contrats-api.md#5-consultation-administrative--implémentée-lot-3b).

## 1. Légende et conditions

- **AUTORISÉ** : droit confirmé par l'énoncé, le cahier ou DEC-COMPTES-001/A1–A9, sous les conditions de la ligne.
- **INTERDIT** : droit exclu par les règles de rôle, périmètre ou décision explicite.
- **À PRÉCISER** : aucune règle assez détaillée ; aucun droit à implémenter avant arbitrage.
- **ZONE** : même zone que l'administrateur LOCAL, lue dans PostgreSQL. L'absence de zone ne satisfait jamais cette condition.
- **HABILITATION** : condition validée pour attribuer LOCAL, dont la définition opérationnelle reste P05.
- Chaque rôle authentifié suppose accès effectif actif et JWT valide. Un droit de gestion n'autorise ni accès au mot de passe/hash ni modification arbitraire de rôle.

## 2. Matrice

| Opération | Visiteur | CLIENT OCCASIONNEL | CLIENT LOCAL | CLIENT VIP | ADMIN LOCAL | ADMIN GLOBAL | Source / précision |
|---|---|---|---|---|---|---|---|
| Inscription autonome initiale OCCASIONNEL | AUTORISÉ | À PRÉCISER : autre compte | À PRÉCISER : autre compte | À PRÉCISER : autre compte | À PRÉCISER : autre compte | À PRÉCISER : autre compte | DEC/A3 ; pluralité P04 |
| Choisir ADMIN, VIP, LOCAL ou un périmètre via inscription publique | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | DEC ; un GLOBAL utilise une opération administrative protégée |
| Imposer publiquement un numéro, un état actif ou une zone administrative | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | A1/A3 ; champs serveur |
| Choisir une zone client dans l'inscription publique | À PRÉCISER | À PRÉCISER | À PRÉCISER | À PRÉCISER | À PRÉCISER | À PRÉCISER | A3 interdit seulement l'attribution automatique ; P03 ; contrat proposé exclut ce champ tant que non arbitré |
| Connexion avec ses identifiants actifs | AUTORISÉ | AUTORISÉ | AUTORISÉ | AUTORISÉ | AUTORISÉ | AUTORISÉ | RM01/RM13 ; visiteur devient authentifié |
| Consulter son profil via me | INTERDIT | AUTORISÉ | AUTORISÉ | AUTORISÉ | AUTORISÉ | AUTORISÉ | RM03 ; identité imposée par JWT puis base |
| Consulter les comptes clients administrés | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ : ZONE | AUTORISÉ : réseau | RM49/RM51/A6 ; filtres et totaux dans le périmètre |
| Consulter les comptes administrateurs via liste ou détail administratifs | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ | Lot 3B validé ; inclut l'exclusion de son propre compte ADMIN pour LOCAL, profil personnel via me |
| Consulter un compte hors de sa zone administrative | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ : réseau | A6 ; profil personnel reste disponible via me |
| Gestion administrative d'un VIP/OCCASIONNEL sans zone | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ pour opérations validées | A6 ; ne définit pas les opérations encore ouvertes |
| Création administrative d'un client OCCASIONNEL | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER pour client zoné ; INTERDIT sans zone | AUTORISÉ | BF02/RM49/RM51/A6 ; P03/P07 pour rattachement |
| Création administrative d'un client LOCAL | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER pour la création ; attribution conditionnée par HABILITATION et ZONE | AUTORISÉ | A4 valide l'attribution, pas tous les détails du droit de création ; P05/P06/P07 |
| Création administrative d'un client VIP | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ | A4 ; conditions VIP P05 non inventées |
| Attribution de catégorie VIP | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ | A4 ; trace auteur/date/motif |
| Attribution de catégorie LOCAL | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ : HABILITATION, source et cible ZONE | AUTORISÉ | A4/A6 ; transitions détaillées P05 ; client sans zone exclu du LOCAL |
| Autres changements de catégorie / rétrogradations | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER dans ZONE | À PRÉCISER | P05 ; pas de droit dérivé d'une simple cible LOCAL |
| Changement de zone d'un client déjà zoné | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER ; source ET cible dans ZONE obligatoires | AUTORISÉ sous règles métier | A4/A6 ; droits fins et réservations futures P05/P07 |
| Transfert d'un client vers/depuis une autre zone | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ sous règles métier | A6 ; un filtre ne remplace pas les deux contrôles |
| Modification d'activité Compte | INTERDIT | INTERDIT pour API admin | INTERDIT pour API admin | INTERDIT pour API admin | À PRÉCISER dans ZONE | À PRÉCISER | A8 fixe le sens, pas tous les droits ; P09 |
| Modification d'activité Client | INTERDIT | INTERDIT pour API admin | INTERDIT pour API admin | INTERDIT pour API admin | À PRÉCISER dans ZONE | À PRÉCISER | P09 ; opération distincte de Compte.actif |
| Réactivation d'un compte | INTERDIT | À PRÉCISER pour parcours propre | À PRÉCISER pour parcours propre | À PRÉCISER pour parcours propre | À PRÉCISER dans ZONE | À PRÉCISER | P09 ; preuve d'e-mail et état métier à respecter |
| Désactiver le dernier GLOBAL actif | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | A5 ; invariant même sous concurrence |
| Créer un administrateur LOCAL | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ | A5 ; zone existante obligatoire |
| Créer un administrateur GLOBAL après amorçage | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | AUTORISÉ | A5 ; invitation A7 |
| Amorcer le premier GLOBAL par API publique | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | Procédure opérateur ponctuelle distincte, P06 |
| Attribuer un périmètre GLOBAL par un LOCAL | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER pour modification d'un existant | A5 pour création ; changement de périmètre P06 |
| Autre modification de périmètre/zone d'administrateur existant | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER, jamais élévation GLOBAL | À PRÉCISER | P06/P07 ; création n'est pas modification |
| Choisir son mot de passe lors de l'inscription initiale | AUTORISÉ | À PRÉCISER : autre compte | À PRÉCISER : autre compte | À PRÉCISER : autre compte | À PRÉCISER : autre compte | À PRÉCISER : autre compte | A3/A7/P04 |
| Définir son premier mot de passe via invitation valide | AUTORISÉ avec preuve d'invitation | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | A7 ; jeton personnel limité et unique, pas de preuve par rôle déclaré |
| Changer son mot de passe | INTERDIT sans preuve/session | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | A7 ; protocole et politique P08 |
| Demander une réinitialisation | AUTORISÉ selon preuve à définir | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | AUTORISÉ pour soi | A7 ; réponse anti-énumération recommandée ; P08 |
| Choisir directement le mot de passe d'un tiers ou l'envoyer en clair | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | A7 impose invitation |
| Consulter mot de passe/hash/jeton de vérification d'autrui via API | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | INTERDIT | RM60/A7 ; aucune lecture administrative |
| Modifier son identifiant de connexion | INTERDIT sans authentification | À PRÉCISER | À PRÉCISER | À PRÉCISER | À PRÉCISER | À PRÉCISER | P02 |
| Consultation publique ou client des zones | À PRÉCISER | À PRÉCISER | À PRÉCISER | À PRÉCISER | AUTORISÉ : ZONE pour administration | AUTORISÉ : réseau | Cahier permet consultation réseau lorsque prévue ; liste publique non définie |
| Créer une zone | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER | À PRÉCISER | BF03 ; droits détaillés P07 |
| Modifier une zone | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER dans ZONE | À PRÉCISER | P07 |
| Supprimer une zone | INTERDIT | INTERDIT | INTERDIT | INTERDIT | À PRÉCISER dans ZONE | À PRÉCISER | P07 ; FK et historique à préserver |

Une ligne AUTORISÉ n'annule pas les préconditions ouvertes : aucun parcours dépendant de P05/P06/P09 ne doit être développé comme entièrement spécifié.

## 3. Application du périmètre

1. Valider le JWT avec les composants existants ; relire rôle, activité et rattachements en PostgreSQL.
2. Réserver les opérations administratives au rôle ADMIN. Une catégorie VIP n'est jamais ROLE_ADMIN.
3. Déterminer la zone réelle à partir des données persistées, pas du filtre ou du corps HTTP.
4. Intersecter périmètre et filtres dans la requête avant pagination ; appliquer la même restriction au total et aux compteurs.
5. Contrôler les détails et modifications avec la même politique ; contrôler source et cible d'une mutation de zone.
6. Pour la consultation du lot 3B, LOCAL exclut les clients sans zone et tous les comptes ADMIN ; GLOBAL inclut les sans-zone. Les autres opérations restent soumises à leurs droits propres.
7. Lot 3B validé : 403 pour l'acteur authentifié sans permission administrative, indépendamment de la cible. Même 404 NOT_FOUND / Ressource introuvable pour absent ou invisible ; recherche déjà filtrée, sans vérification globale d'existence. 401 si authentification indisponible. Les anciennes propositions 403 hors zone sont remplacées pour ces GET.

### Lecture administrative — portée confirmée

| Acteur | Lignes consultables dans la cible | Limites |
|---|---|---|
| Visiteur | Aucune | 401 sur API protégée |
| CLIENT, toute catégorie | Aucune via API administrative | 403 avec authentification valide ; me reste disponible pour soi |
| ADMIN LOCAL | Comptes CLIENT dont Client.zone.id correspond à sa zone administrative persistée | Sans-zone et hors-zone exclus des lignes, totaux et détails ; comptes ADMIN exclus de la lecture administrative, même de sa propre zone |
| ADMIN GLOBAL | Comptes CLIENT et ADMIN du réseau | Inclut les comptes sans zone ; cette lecture n'autorise aucune mutation non spécifiée |

Les statuts des comptes consultés sont des données de lecture A8 ; ils ne donnent pas de droit de modification. L'acteur doit lui-même disposer d'un accès effectif valide. Dans le DTO proposé, l'identifiant d'une ligne ADMIN est son e-mail de connexion ; l'e-mail de contact CLIENT est exclu du DTO commun. Les règles exactes de recherche, tri, pagination et réponses de détail ont été validées pour le lot 3B ; voir la source contractuelle unique en [section 5](contrats-api.md#5-consultation-administrative--implémentée-lot-3b).

## 4. Invariants indépendants de l'interface

- Lot 3C.1 / DEC-COMPTES-004 : INVITE ne peut ni se connecter ni utiliser un JWT ; INITIALISE ne dispense pas des activités et permissions. Aucun changement implicite de Compte.actif/Client.actif. Les cibles invitées restent consultables selon le même périmètre GLOBAL/LOCAL, sans nouveau droit de mutation.

- Rôle CLIENT et catégorie OCCASIONNEL imposés à l'inscription publique.
- LOCAL toujours rattaché à une zone ; ADMIN LOCAL toujours lié à sa zone administrative.
- ADMIN GLOBAL sans zone administrative ; aucun compte à la fois CLIENT et ADMIN dans le modèle actuel.
- Jamais de permission depuis le préfixe V/C/O, localStorage ou un rôle envoyé dans une requête.
- VIP/OCCASIONNEL sans zone gérés uniquement par GLOBAL ; pas de rattachement automatique pour contourner cette restriction.
- Dernier GLOBAL actif protégé par des transactions indépendantes, sérialisées par un même verrou commun PostgreSQL acquis avant relecture et décision, puis conservé jusqu'au commit ou rollback.
- Statut technique et statut métier indépendants ; les nouvelles inscriptions publiques exigent une vérification d'e-mail avant activation, les nouvelles créations administratives normales une invitation sécurisée avant accès.
- Historique et réservations préservés lors des changements autorisés.

Les comptes préexistants conservent les contrôles actuels. L'absence de métadonnées de vérification d'e-mail ou d'invitation ne devient pas un motif rétroactif de refus ; une telle évolution exige une décision et une migration adaptées. L'articulation précise invitation/vérification d'e-mail pour les nouveaux comptes administratifs reste ouverte en P06/P11. Le jeu fictif isolé relève exclusivement de DEC-COMPTES-002.

Pour les tests conditionnels, voir AC18–AC25 et les références P01–P11 dans les [décisions](decisions.md).
