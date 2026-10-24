# Tests d'acceptation — comptes centralisés

**État courant — issue [#23](https://github.com/Zoom225/Gestion-covoiturage/issues/23), PR [#24](https://github.com/Zoom225/Gestion-covoiturage/pull/24) :** DEC-COMPTES-006 valide les créations GLOBAL/LOCAL, les activités initiales et la politique de mot de passe. Les quatre POST de création/renvoi/acceptation et le bootstrap opérateur sont implémentés. V8 additive ; V1–V7 conservées. Voir [contrats, sécurité, modèle et preuves](parcours-invitations-preparation.md). Les instantanés des lots antérieurs ci-dessous restent historiques.


**Actualisation — lot 3C.2 / issue [#21](https://github.com/Zoom225/Gestion-covoiturage/issues/21) :** DEC-COMPTES-005 livre le service interne de preuves d'invitation (32 octets, SHA-256, durée configurable, révocation/renouvellement et consommation atomique). Aucun endpoint, e-mail, mot de passe ou compte créé. Voir [modèle, concurrence, tests et limites AA](lot-3c2-invitations.md).


**Actualisation — lot 3C.1 / issue [#19](https://github.com/Zoom225/Gestion-covoiturage/issues/19) :** DEC-COMPTES-004 ajoute INVITE/INITIALISE. Compte invité sans hash, accès refusé y compris par JWT ; INITIALISE et activités sont indépendants. Les comptes préexistants restent INITIALISES. Seul le socle est livré ; jetons, e-mails et création administrative restent futurs. Voir [modèle, migration V5 et preuves](lot-3c1-comptes-invites.md).


**État courant — lot 3B / issue [#15](https://github.com/Zoom225/Gestion-covoiturage/issues/15) :** consultation administrative implémentée selon les règles validées le 24 octobre 2026. GLOBAL consulte le réseau ; LOCAL seulement les comptes CLIENT de sa zone, sans visibilité des comptes ADMIN. Pagination 0/20/100, ordre compteId croissant, recherche exacte sans casse, filtres par intersection et même 404 pour absent/invisible. Les propositions du lot 3A ci-dessous sont historiques lorsqu'elles sont remplacées par cet état. Voir [architecture, contrat et preuves TFE](lot-3b-consultation.md).

**État courant — lot 2 / #11 :** POST login accepte l'e-mail ADMIN ou le matricule CLIENT sans distinction de casse ; GET me conserve les droits issus de PostgreSQL. V4 impose stockage canonique et cohérence Client/Compte sans réécrire les anciens comptes. Les formulations antérieures décrivent l'historique. Voir [contrat livré et tests AC-AUTH](lot-2-authentification.md).


**Actualisation lot 1 / DEC-COMPTES-003 :** la cible est e-mail ADMIN et matricule CLIENT ; les descriptions antérieures d'un login client indépendant sont historiques. Ce lot livre uniquement les séquences et l'historique, sans changer login/me ou les permissions. Les tests automatisés AC-NUM01–AC-NUM12 sont décrits dans le [dossier du lot](lot-1-numerotation.md) ; ils sont distincts des scénarios documentaires ci-dessous.


[Décisions](decisions.md) · [Permissions](matrice-permissions.md) · [Contrats](contrats-api.md).
Issue [#7](https://github.com/Zoom225/Gestion-covoiturage/issues/7), 21 octobre 2026.

**Les 38 scénarios AC01–AC38 restent un référentiel documentaire de parcours partiellement futurs ; ils ne constituent pas 38 tests exécutés.** Depuis le lot 3B, AC-CONS01–AC-CONS08 correspondent à des tests de consultation réellement exécutables. Création administrative, renvoi et acceptation sont désormais implémentés avec DEC-COMPTES-006 et les preuves HTTP en fin de document. Inscription publique, transitions de catégorie et modifications de statut restent futures.

**Actualisation documentaire — lot 3A / issue [#13](https://github.com/Zoom225/Gestion-covoiturage/issues/13), 24 octobre 2026 :** base origin/master 39a7b6b (PR #10 et #12 fusionnées). Aucun endpoint administratif livré par ce lot. Le matricule CLIENT est le login généré ; l'e-mail de connexion ADMIN est distinct du contact CLIENT. Les droits de lecture confirmés et les modalités encore ouvertes sont séparés dans les [contrats de consultation](contrats-api.md#5-consultation-administrative--implémentée-lot-3b).

## 1. Preuves existantes consultées

| Source | Couverture lue dans le code | Écart pour la cible |
|---|---|---|
| [CovoiturageApplicationTests](../../src/test/java/com/plateforme/covoiturage/CovoiturageApplicationTests.java) | V1 vers V2 conservant C0001, Flyway, Hibernate/JPA, UNIQUE numéro, LOCAL sans zone, FK et suppression d'une zone référencée | Conserver les preuves d'intégrité lors des futures lectures |
| [ClientNumberingIntegrationTests](../../src/test/java/com/plateforme/covoiturage/ClientNumberingIntegrationTests.java) et [AccountIdentifierIntegrationTests](../../src/test/java/com/plateforme/covoiturage/AccountIdentifierIntegrationTests.java) | Lots 1/2 : génération V/C/O, concurrence, historique, migration et intégrité des identifiants V4 | Ne prouvent pas les futurs GET administratifs ; compteurs et détails filtrés restent à tester |
| [SecurityIntegrationTests](../../src/test/java/com/plateforme/covoiturage/SecurityIntegrationTests.java) | Login/me des cinq profils par e-mail ADMIN ou matricule CLIENT, casse, BCrypt, désactivation, JWT, droits relus en base, sondes de zone, CORS et Swagger dev | Pas d'inscription/invitation/consultation administrative métier ; les sondes ne remplacent pas les vrais endpoints |
| [JwtConfigurationTests](../../src/test/java/com/plateforme/covoiturage/security/JwtConfigurationTests.java) | Refus des secrets JWT manquants/mal formés/trop courts et TTL non positif | Paramètres de session métier et nouveaux parcours à définir |
| [SwaggerProductionIntegrationTests](../../src/test/java/com/plateforme/covoiturage/SwaggerProductionIntegrationTests.java) | Documentation désactivée en production même avec dev | Conserver cette preuve lors des futurs changements |

Le README et la PR #6 rapportent historiquement 36 tests réussis ; les dossiers [lot 1](lot-1-numerotation.md) et [lot 2](lot-2-authentification.md) décrivent les compléments numérotation et authentification. Le lot 3A n'avait exécuté aucun Maven local. Le lot 3B ajoute AdminCompteIntegrationTests : 35 tests PostgreSQL/HTTP ciblés réussis ; résultat de la suite complète et de la CI consigné dans le [dossier du lot](lot-3b-consultation.md). Aucun accès aux données de développement.

Les sondes /api/admin/probe sont uniquement des fixtures de test. Les futurs tests vérifieront les véritables API et ne considéreront pas ces sondes comme une fonctionnalité livrée.

## 2. Scénarios Given / When / Then

Pour les parcours non implémentés, les résultats HTTP restent proposés. Ceux des GET de consultation sont livrés au lot 3B et vérifiés par AC-CONS. Les Pxx sont définis dans les [décisions](decisions.md).

### Inscription, privilèges et identité

| ID | Given (Étant donné) | When (Quand) | Then (Alors) | Référence / dépendance |
|---|---|---|---|---|
| AC01 | Un visiteur avec e-mail de contact valide et mot de passe conforme, sans login client libre | Il soumet le DTO public autorisé | 201 proposé ; exactement un Client OCCASIONNEL et un Compte CLIENT associés ; numéro O généré ; BCrypt en base ; aucun accès avant preuve e-mail et aucun JWT de connexion | DEC/A1/A3/A7 ; RM04/RM11/RM58 ; P01–P04/P08/P09/P11 avant implémentation |
| AC02 | Un visiteur prépare une inscription | Il ajoute categorie VIP puis LOCAL | 400 selon le contrat strict proposé ; aucun objet persisté ; jamais la catégorie demandée ; l'omission de categorie ne change pas l'imposition OCCASIONNEL | DEC/A3 ; RM02/RM04 |
| AC03 | Un visiteur non authentifié | a) Il soumet POST /api/auth/register avec role ADMIN, perimetreAdmin GLOBAL ou un profil administratif imbriqué ; b) il appelle directement une API administrative | a) 400 proposé pour le payload d'inscription contenant des champs interdits ; b) 401 pour l'accès anonyme à l'API administrative ; aucune création ni attribution de privilège dans les deux cas | DEC/A3/A5 ; RM01/RM02 ; cohérent avec AC17 |
| AC04 | L'inscription publique ne possède pas encore de règle de zone manuelle | Une requête ajoute zoneClientId, zoneAdminId, clientId, numero ou actif | Rejet 400 proposé sans modification ; aucune zone ou activation choisie par l'appelant ; aucune attribution automatique de zone | A1/A3/A6 ; P03 pour un futur choix client |
| AC05 | Le formulaire manque un champ requis ou contient un e-mail invalide/JSON malformé | Le visiteur s'inscrit | 400 contrôlé ; aucune création et aucune valeur sensible dans validationErrors | A3 ; AA9 ; autres champs et longueurs P03/P08 |
| AC06 | Un identifiant existe déjà ; deux variantes ne différant que par casse sont incluses dans la cible | Une création utilise le même identifiant | 409 proposé ; aucun Client orphelin ; unicité PostgreSQL cohérente avec la connexion ; aucun changement de l'identifiant existant | A2 ; RM58/RM59 ; casse dépend de P02 et future migration |
| AC07 | Plusieurs transactions créent simultanément des comptes avec identifiants distincts et même catégorie | Elles génèrent leurs numéros et valident | Tous les numéros sont uniques et préfixés selon la catégorie ; pas de count()+1 ; contrainte SQL active ; vérifier le résultat après commits réels | A1 ; RM09–RM11/RM59 ; choix de séquence P01 |
| AC08 | Le prochain numéro atteint 9999, puis dépasse quatre chiffres | Deux créations successives consomment les valeurs | Selon la proposition de format, O9999 puis O10000 ; jamais troncature ou réutilisation ; un ancien numéro historisé reste indisponible | A1 ; P01 pour maximum, histoire et choix de séquence |
| AC09 | Une défaillance est injectée après insertion Client ou après génération du numéro | La création Compte/Client échoue | Rollback de tous les objets associés ; zéro orphelin et pas d'invitation utilisable ; un trou de séquence est accepté sans réutilisation | DEC/A1 ; RM58 ; tester également deux créations concurrentes du même login : un succès, un 409 |
| AC10 | Un compte public est en attente d'e-mail et possède une preuve valide | Il tente login avant preuve, puis valide la preuve et se connecte | Avant preuve : 401 et aucun JWT ; après preuve : activation selon préconditions, connexion commune possible si les deux états l'autorisent ; aucun auto-login avant validation | A3/A8 ; RM13 ; durées, protocole et livraison P08/P09/P11 |

### Authentification et périmètres

| ID | Given | When | Then | Référence / dépendance |
|---|---|---|---|---|
| AC11 | Cinq comptes actifs préparés dans une base de test : ADMIN GLOBAL, ADMIN LOCAL, CLIENT VIP, CLIENT LOCAL, CLIENT OCCASIONNEL ; inclure des comptes préexistants sans nouvelles métadonnées d'e-mail ou d'invitation | Chaque compte appelle login puis me | 200 puis 200 ; rôle ADMIN/CLIENT seulement ; périmètre/catégorie/zone corrects ; aucun privilège déduit du préfixe ; aucun refus rétroactif ni modification de compte fondés sur l'absence de ces métadonnées ; test paramétré des cinq profils | A7/A9 ; RM01–RM04/RM07/RM13 ; toute application rétroactive future exige décision et migration adaptées |
| AC12 | Un utilisateur inconnu, un mot de passe incorrect et un Compte inactif | Chaque cas appelle login ; un ancien JWT du compte désactivé appelle me | Réponses 401 génériques, pas de distinction révélant le mot de passe ou l'existence ; JWT de compte désactivé inutilisable | A8 ; RM01/RM13 |
| AC13 | Un Compte CLIENT actif lié à un Client inactif et un ancien JWT valide | Il tente login puis me | 401 dans les deux cas ; aucun contournement du statut métier par le statut technique | A8 ; RM13 |
| AC14 | Un LOCAL de zone A, des clients A/B, sans zone et des ADMIN ; un GLOBAL | Ils consultent les GET livrés avec pagination et filtres validés | LOCAL : seulement CLIENT de A ; aucun total hors périmètre ni compte ADMIN. GLOBAL : réseau incluant sans-zone | A6 ; RM49/RM51/RM57 ; lot 3B, AC-CONS01–AC-CONS03 |
| AC15 | Un LOCAL connaît l'id d'un compte de B ou sans zone ; un autre id est inexistant | Il demande les détails livrés, sans mutation | Même 404 NOT_FOUND / Ressource introuvable pour absent/invisible ; aucune recherche globale de secours. CLIENT authentifié : 403 indépendamment de la cible | A6 ; RM49/RM51 ; lot 3B, AC-CONS04/AC-CONS05 |
| AC16 | Un GLOBAL actif et une demande valide d'administrateur LOCAL puis GLOBAL | Il crée les comptes administratifs | 201 proposé, profil cohérent avec V2 ; LOCAL avec zone, GLOBAL sans zone ; invitation sans mot de passe par défaut ni JWT ; pas de mot de passe en e-mail | A5/A7 ; RM02/RM58 ; représentation d'un invité et livraison P06/P11 |
| AC17 | Un visiteur, un CLIENT de chaque catégorie et un ADMIN LOCAL | Ils appellent la création d'administrateur, y compris avec role/perimetre manipulés | Visiteur : 401 ; CLIENT/LOCAL : 403 ; aucun administrateur créé, y compris LOCAL ; modifier le JSON ne change pas les droits | A5 ; RM01/RM02/RM49 |
| AC18 | Un LOCAL habilité de zone A et un GLOBAL | Ils tentent une attribution VIP puis LOCAL | VIP : GLOBAL seul ; LOCAL : GLOBAL ou LOCAL habilité avec source/cible autorisées ; LOCAL refusé hors zone et pour client sans zone ; aucun droit hérité du frontend | A4/A6 ; RM04/RM07/RM49/RM51 ; matrice exacte des transitions et habilitation P05 |
| AC19 | Un GLOBAL ou LOCAL habilité autorisé à créer un client LOCAL | La zone est absente, invalide ou hors périmètre | 400 si absente ; 404 si zone autorisée inexistante ; 403 si hors périmètre ; aucune paire Client/Compte partielle | A4/A6 ; RM07/RM58 ; P05/P07 |
| AC20 | Un changement autorisé de catégorie ou zone et un client avec historique/réservations | Le changement est effectué, puis deux changements concurrents sont tentés | Client.id, réservations et historique conservés ; auteur/date/motif tracés ; aucun ancien numéro réutilisé ; une transition concurrente incohérente ne passe pas | A1/A4 ; RM38/RM39/RM60 ; À COMPLÉTER P01/P05 pour numéro courant et effet sur réservations futures |
| AC21 | Un LOCAL de A et un client dont source ou cible est B, ou une ressource zone hors A | Une mutation de zone est demandée | Contrôle source ET cible, refus hors périmètre même si l'un des deux ids est A ; aucun transfert partiel ; aucun droit CRUD zone inventé | A6 ; RM49 ; opérations de zone et transitions À COMPLÉTER P05/P07 |

### Cycle de vie, concurrence et secrets

| ID | Given | When | Then | Référence / dépendance |
|---|---|---|---|---|
| AC22 | Un seul GLOBAL actif ; droits de statut finalisés | Une désactivation de ce compte est demandée | 409 proposé pour l'invariant ; il reste actif ; aucun client/compte annexe modifié | A5 ; RM51/RM60 ; droits P09 |
| AC23 | Deux GLOBAL actifs, connexions distinctes et transactions indépendantes | Deux désactivations concurrentes sont déclenchées avec départ synchronisé ; chaque transaction acquiert le même verrou commun PostgreSQL avant relecture des GLOBAL actifs et décision | Les transactions sont sérialisées au point de décision ; au moins un GLOBAL reste actif après les commits et une désactivation incompatible échoue ; verrou conservé jusqu'au commit ou rollback ; test PostgreSQL réel, pas une simulation séquentielle | A5 ; RM51/RM58 ; stratégie P06 et droits P09 |
| AC24 | Mot de passe de 72 octets UTF-8, puis de 73 octets, puis multioctets sous la limite de caractères | Inscription/invitation/changement et login utilisent ces entrées | Aux limites autorisées, BCrypt vérifie exactement ; dépassement refusé sans troncature ; login conserve ses résultats actuels 400 caractères/401 octets, nouveaux parcours 400 proposé ; jamais 500 ou secret journalisé | A7 ; AA6/AA9 ; politique minimale P08 |
| AC25 | Une personne autorisée change seulement Compte.actif ; Client.actif a une valeur connue | La modification est validée et l'ancien JWT est réutilisé | Client.actif inchangé ; compte désactivé sans accès ; tableau affiche deux statuts et accès effectif ; auteur/date et changement tracés ; réactivation ne contourne pas l'activité métier ni, pour les nouveaux inscrits publics concernés, la preuve d'e-mail ; aucune exigence rétroactive sur les comptes préexistants sans décision et migration adaptées | A8 ; RM13/RM60 ; droits et réactivation P09 |
| AC26 | JWT absent, malformé, expiré, autre signature/algorithme, mauvais issuer/audience/dates ou sujet inconnu | Une API protégée est appelée ; un JWT valide porte aussi de faux claims de rôle/zone | Les JWT invalides donnent 401 ; claims de permission non déterminants ; avec JWT valide et rôle insuffisant : 403 ; droits relus en base même après changement de rôle/zone | A6/A9 ; RM01/RM02/RM49 |
| AC27 | Une invitation ou preuve d'e-mail personnelle, puis expirée, consommée ou ciblant un autre compte | Elle est consommée, y compris simultanément ; un envoi échoue puis est repris | Une seule consommation réussit ; aucune activation sur mauvaise preuve ; aucun jeton réutilisable après consommation ; pas de mot de passe en e-mail ; aucun compte incohérent après échec d'envoi | A3/A7 ; RM58/RM60 ; À COMPLÉTER P06/P08/P11 pour protocoles, durées, codes et livraison |
| AC28 | Les cinq profils de AC11, frontend connecté à l'API et jeton en mémoire | Login puis me réussissent ; ensuite surviennent 401, 403 ou rechargement | Cinq redirections A9 exactes ; 401 protégé efface session ; 403 affiche refus sans déconnexion systématique ; profil incohérent bloqué ; rechargement sans session persistante nécessite connexion | A9 ; AA8/AA9 ; durées P08 |
| AC29 | Un état V1/V2 avec identifiants et numéros existants, dont des logins qui entreraient en collision après normalisation | Une future migration de normalisation/numérotation est préparée sur copie de test | Collisions détectées avant ajout de contrainte ; aucune suppression/renumérotation silencieuse ; données et V1/V2 conservées ; reprise de séquences au-delà des numéros utilisés | A1/A2 ; RM58/RM59 ; À COMPLÉTER P01/P02/P04 ; aucune migration exécutée ici |
| AC30 | Tableau Angular des comptes avec filtres/pagination serveur, principal LOCAL, tentative de faux rôle dans le navigateur | Navigation directe et requêtes HTTP tentent d'élargir les droits ; un Bearer pourrait être envoyé hors API | Guards et affichage cohérents ; backend refuse tout élargissement ; Bearer limité à l'API prévue ; actions autorisées seulement ; tableau responsive, erreurs/chargement et formulaires utilisables | A6/A9 ; AA8/AA9 ; filtres et interface P02/P07/P10 |
| AC31 | Réponses login/me/liste/détail/création/erreur, journaux capturés en test et audits métier | Parcours valides et refus 400/401/403/404/409 sont exercés | Aucun mot de passe, hash, secret JWT, jeton de preuve, stack trace ou SQL dans réponses/logs ; seul login retourne son accessToken prévu ; audit utile sans secret ; pas d'exposition d'entité ; no-store sur réponses sensibles | A7/A8 ; RM60 ; 404 de consultation livré au lot 3B ; 409 et parcours de mutation futurs |

### Comptes fictifs isolés — DEC-COMPTES-002

Ces scénarios sont documentaires et non exécutés. Ils décrivent les garanties attendues d'un éventuel futur outillage de données fictives ; ils n'autorisent aucune création dans la base de développement actuelle. La couverture existante citée plus haut ne démontre pas encore ces garanties.

| ID | Given | When | Then | Référence / dépendance |
|---|---|---|---|---|
| AC32 | Un jeu exclusivement fictif dans un environnement de développement ou de démonstration dont l'isolation est vérifiée | Un futur outillage explicitement activé prépare un compte fictif | Le mot de passe initial suit la règle fictive applicable de DEC-COMPTES-002/003 : convention depuis le matricule CLIENT si retenue, exception ADMIN expressément conservée uniquement dans la fiche privée ; seul son hash BCrypt est stocké en PostgreSQL via le PasswordEncoder existant ; les valeurs fictives de consultation restent uniquement dans le registre Markdown privé principal ; aucune valeur calculée en clair dans les logs ; aucun compte réel modifié | DEC-COMPTES-002/A7 ; AA6/AA10 ; outillage et preuve d'isolation à spécifier |
| AC33 | Un contexte production et une base éphémère de test avec état initial connu | L'application démarre, y compris avec une demande d'activation de données fictives | Aucun compte de démonstration et aucun mot de passe initial prévisible créés ; aucune insertion ou modification de compte par un initialiseur de démonstration ; les comptes préexistants et leurs hashes restent inchangés | DEC-COMPTES-002 ; AA6/AA10 ; test de contexte production isolé, jamais contre la production réelle |
| AC34 | Un contexte où prod est combiné avec dev ou avec un éventuel profil de démonstration | Le contexte démarre et les points d'initialisation sont sollicités | La présence de prod interdit toute initialisation fictive ; aucun composant de démonstration n'exécute d'écriture de compte, même si son activation est demandée ; même résultat qu'AC33 | DEC-COMPTES-002 ; matrice de combinaisons à adapter aux profils effectivement retenus |
| AC35 | Un environnement non isolé ou contenant un compte réel, même si dev est actif | Une préparation de données fictives tente d'appliquer la convention | Aucune création fictive dans cet environnement et aucune attribution ou réinitialisation prévisible d'un compte réel ; un simple profil dev ne suffit pas à autoriser le traitement ; état des comptes réels inchangé | DEC-COMPTES-002 ; contrôle technique d'isolation à spécifier, refus avant toute écriture |
| AC36 | Une inscription publique OCCASIONNEL avec un mot de passe choisi par le visiteur différent de la convention, y compris en dev | Le parcours d'inscription normal est appelé | Le hash BCrypt correspond au mot de passe choisi, jamais à une substitution par la convention ; CLIENT/OCCASIONNEL imposés, e-mail à vérifier et aucune connexion automatique avant validation | DEC-COMPTES-001/A3/A7 et DEC-COMPTES-002 ; complète AC01/AC10 |
| AC37 | Une création administrative normale autorisée, en développement comme en production | Le compte client ou administrateur est créé puis l'invitation est traitée | Invitation sécurisée limitée et à usage unique ; aucun mot de passe calculé par la convention ni envoyé en clair ; le destinataire choisit son mot de passe via le parcours prévu ; aucun contournement par un paramètre de requête | A7/DEC-COMPTES-002 ; complète AC16/AC27 ; P06/P08/P11 demeurent ouverts |
| AC38 | Des valeurs fictives de mot de passe de test atteignant 72 puis 73 octets UTF-8, y compris multioctets | Le composant de validation du futur outillage est testé isolément, sans créer de compte | À 72 octets, BCrypt vérifie la valeur exacte ; au-delà, refus sans troncature ni écriture partielle. Ne pas fabriquer pour cette limite un matricule incompatible avec V3/V4 ; la convention CLIENT et l'exception ADMIN restent DEC-COMPTES-003 | A7/DEC-COMPTES-002/003 ; complète AC24 ; cas documentaire adapté à V3/V4 |

Le registre privé principal est comptes-demo.local.md. Aucun CSV n'est requis ou recréé ; les éventuels fichiers Markdown/CSV privés doivent rester ignorés et absents des fichiers publiables. Les parcours AC10/AC36 concernent les nouvelles inscriptions publiques ; AC16/AC27/AC37 couvrent les nouvelles invitations administratives normales. Leur articulation exacte reste P06/P08/P11 ; AC11 conserve la compatibilité des comptes préexistants sans application rétroactive implicite.

Pour AC33–AC35, contrôler les composants d'initialisation et comparer l'état des comptes avant/après démarrage et tentative d'activation : l'absence d'une seule valeur en clair dans les fichiers ne prouve pas l'absence de création avec un hash prévisible. Une revue des futurs scripts et migrations doit aussi rechercher les initialisations de comptes fictifs et les valeurs de secours ; ne pas modifier V1/V2.


## 2 bis. Consultation administrative — preuves du lot 3B

Les 38 scénarios AC01–AC38 sont conservés. Les huit compléments AC-CONS sont maintenant associés aux tests de [AdminCompteIntegrationTests](../../src/test/java/com/plateforme/covoiturage/AdminCompteIntegrationTests.java). Cette classe comporte 19 tests simples et 16 cas paramétrés, soit 35 tests ; ne pas confondre scénarios de spécification et nombre d'exécutions JUnit.

| ID | Given | When | Then vérifié | Tests principaux |
|---|---|---|---|---|
| AC-CONS01 | GLOBAL actif ; cinq profils, comptes sans zone et comptes inactifs | Liste et détails réseau | Tous les comptes autorisés sont lisibles ; DTO et totaux filtrés | globalReadsAllProfilesIncludingUnzonedAndDisabledAccounts |
| AC-CONS02 | LOCAL A ; clients A/B/sans zone et ADMIN | Liste et détails, y compris son propre compte ADMIN | Seulement CLIENT de A ; aucun ADMIN ; aucune donnée hors zone | localReadsOnlyClientsOfItsZoneIncludingTheirDistinctStatuses ; localCannotReadAdministratorsEvenItselfOrOneInTheSameZone |
| AC-CONS03 | Plusieurs pages ; ajout de lignes invisibles | Pagination et comptages répétés | Prédicat appliqué avant pagination/count ; ajout hors périmètre sans effet ; page vide conserve le total autorisé | paginationAndCountsUseTheSameLocalPredicateOnEveryPage ; addingHiddenRowsCannotChangeTheLocalPageOrItsCounters |
| AC-CONS04 | LOCAL et ids autorisé, hors zone, sans zone, ADMIN et inexistant | GET détail | Autorisé 200 ; absent/invisible : même 404, code/message/structure, sans existence globale de secours | hiddenAndAbsentDetailsHaveIdenticalGenericErrors |
| AC-CONS05 | Visiteur, CLIENT des trois catégories, JWT invalide/expiré, acteur désactivé | Deux GET protégés ; service appelé directement | 401 d'authentification ; 403 CLIENT indépendant de l'existence de la cible ; service ne dépend pas du seul contrôle HTTP | everyClientCategoryIsDeniedBeforeTargetExistenceIsLookedUp ; absentMalformedExpiredJwtAndDisabledActorProduce401 ; serviceEnforcesPermissionsEvenWithoutTheHttpRoleGate |
| AC-CONS06 | GLOBAL lit ADMIN/CLIENT ; statuts distincts | Liste/détail sérialisés | ADMIN login e-mail ; CLIENT login = numéro ; deux statuts et accès dérivé ; aucun hash/mot de passe/jeton/contact ; no-store | safeDtoNoStoreAndEffectiveStatusAreConsistentInListAndDetail |
| AC-CONS07 | Contrat validé : index 0, défaut 20, max 100, ordre id croissant, recherche exacte sans casse et filtres intersectés | Valeurs valides/invalides, recherche partielle/joker, combinaisons et limites | Contrat respecté, 400 contrôlés, jamais d'élargissement ; rôle ADMIN pour LOCAL et filtres incompatibles produisent une page vide | exactIdentifierSearchIgnoresCaseWithoutPartialMatchingOrWildcards ; filtersIntersectEachOtherAndNeverWidenLocalScope ; globalZoneFilterUsesTheApplicableAdministrativeOrClientZone ; accountAndClientStatusFiltersRemainIndependent ; defaultPageActuallyLimitsTwentyRowsInIncreasingAccountIdOrder ; cas paramétrés invalides |
| AC-CONS08 | JWT déjà émis ; zone puis rôle modifiés dans PostgreSQL de test | Nouvelles requêtes | Principal relu ; ancien périmètre non conservé ; GLOBAL accède aux sans-zone ; pas de N+1 | anExistingJwtUsesTheCurrentZoneAndRoleFromPostgres ; listAndDetailLoadTheirRelationsWithoutNPlusOneQueries |

Contrôles complémentaires : OpenAPI des deux GET, schémas DTO sans entités ni credentials ; dépassement de longueur d'identifiant sans écho ; borne de décalage JPA ; relecture du rôle au niveau service avant toute requête. Aucun de ces tests ne développe ni ne valide les futurs parcours de création/invitation/statut.

Fixtures uniquement dans PostgreSQL 16.6 Testcontainers ; aucune écriture dans la base de développement. Les suites antérieures conservent V1–V4, BCrypt/JWT, Flyway/Hibernate et les règles Swagger dev/prod. Voir [commandes et résultats effectifs](lot-3b-consultation.md).

## 3. Compléments de sécurité nécessaires

- Créer les fixtures exclusivement dans PostgreSQL éphémère de test ; jamais dans la base de développement. Conserver V1/V2 sans modification.
- Tester les UNIQUE et FK réels, pas seulement un contrôle préalable en Java. Pour les courses, utiliser connexions/transactions indépendantes, synchronisation explicite et assertion après commit.
- Vérifier exactement une création pour deux demandes concurrentes du même identifiant ; perdant en conflit contrôlé et aucun orphelin.
- Inclure dans les tests de filtre la dernière page, une recherche sans résultat et les totaux afin de détecter les fuites par pagination.
- Tester les mutations directes du rôle, de la catégorie et de la zone envoyées au serveur et les modifications de la base appliquées aux JWT déjà émis.
- Valider les durées et consommations avec une horloge contrôlée ; aucune durée numérique inventée avant P08.
- Ajouter des scénarios anti-automatisation, limitation des essais et réinitialisation anti-énumération quand les mécanismes P08/P11 seront arrêtés.
- Réinitialisation/changement de mot de passe : autorisation du propriétaire ou preuve personnelle, mot de passe non conservé côté navigateur ; décider l'invalidation des JWT préexistants avant le test correspondant.
- Tester le refus du second amorçage GLOBAL après définition de la procédure P06 ; aucune création réelle dans cette mission.
- Définir les scénarios de réactivation, accès CRUD zones et réservations futures après P05/P07/P09. Les interdictions hors périmètre restent testables et obligatoires.

## 4. Non-régression et critères de fin des futurs lots

Conserver les tests login/me, statuts, JWT, rôle/catégorie, CORS, contraintes PostgreSQL, migration V1 vers V2, validation Flyway et Hibernate, Swagger public uniquement en dev et désactivé en prod/dev+prod.

Après chaque futur lot : compiler et exécuter les tests pertinents contre une base isolée, consigner les commandes et résultats réellement obtenus, puis exécuter la suite appropriée avant publication autorisée. Un Tomcat démarré ou une compilation seule ne prouve ni transactions ni sécurité.

Les nouveaux tests de numéros ne doivent pas supprimer le test où les trois catégories restent CLIENT. Adapter sa fixture si une nouvelle contrainte préfixe/catégorie est retenue, tout en conservant ses assertions de sécurité.

## 5. Correspondance et limites

- Analyse/scénarios : AA1/AA2/AA11.
- Contraintes, transactions et modèle : AA3/AA4/AA5.
- JWT, rôles et périmètres : AA6 ; contrats REST : AA7 ; Angular : AA8 ; validations/erreurs : AA9 ; automatisation future, Git et CI : AA10.
- Les RM directement concernées et toutes les RM hors implémentation sont classées dans les [décisions](decisions.md). AC20 ne prétend pas implémenter RM38/RM39 à lui seul : les modules réservation devront conserver leurs propres revalidations.
- Une case À COMPLÉTER ne permet pas de choisir implicitement une règle métier. La décision correspondante doit précéder le code et le résultat de test.

## Lot 3C.1 — AC-INIT : socle livré, parcours d'invitation futurs

Ces huit regroupements de scénarios ne s'ajoutent pas au décompte historique des 38 AC01–AC38.
La classe InvitedAccountIntegrationTests comporte des tests simples et paramétrés distincts ;
le nombre réellement exécuté figure dans le [dossier du lot](lot-3c1-comptes-invites.md).
AC16/AC27/AC37 restent partiellement futurs : aucun jeton, e-mail ou endpoint de création livré.

| Scénario | Given | When | Then | Preuve automatisée |
|---|---|---|---|---|
| AC-INIT01 | Installation V1 puis V4 avec cinq profils préexistants | V5 migre sur PostgreSQL Testcontainers | Comptes existants INITIALISES ; IDs, hashes, relations, activités et historique conservés ; aucun compte généré | upgradeV1ToV5PreservesReferentialsAndDoesNotSeedAccounts ; upgradeV4PreservesAllFiveProfilesIdsHashesRelationsAndIndependentActivityFlags |
| AC-INIT02 | INVITE/INITIALISE, état inconnu ou NULL, hashes absents ou incompatibles | INSERT de contrainte | Seuls INVITE/NULL et INITIALISE/BCrypt compatible sont admis ; refus sans exposer la ligne dans les assertions | postgresRejectsInconsistentCredentialStates ; defaultPreservesInitializedWritersButNeverCreatesAHashlessInitializedAccount |
| AC-INIT03 | Cinq profils INVITE sans hash | Login, puis JWT signé sub=Compte.id vers me/admin | Même 401 générique que l'identifiant inconnu ; aucun JWT de login ni principal authentifié | invitedProfilesHaveNoPasswordAndCannotLoginOrUseASignedJwt |
| AC-INIT04 | Quatre combinaisons des activités compte/client | Persistance/relecture de l'invité | Activités inchangées ; accès toujours faux | invitedCredentialStateDoesNotChangeEitherActivityFlag |
| AC-INIT05 | Invité inactif | Transaction incohérente puis mise à jour SQL atomique état/hash, seulement en test | Rollback complet ; une initialisation conforme ne réactive rien | stateAndHashMustChangeTogetherAndFailedTransactionPreservesActivityAndHash |
| AC-INIT06 | Compte initialisé avec ancien JWT | Son état de test devient INVITE et le hash NULL | JWT refusé à la requête suivante, sans changement d'activité ; constructeur historique et JWT INITIALISE restent utilisables | databaseReadinessChangeInvalidatesAnAlreadyIssuedJwtWithoutChangingActivity ; initializedDomainConstructorStillSupportsLoginAndIdBasedJwt |
| AC-INIT07 | GLOBAL/LOCAL et cibles invitées de zone/sans zone/ADMIN | Consultation liste, count et détail | DTO état sans secret ; périmètre avant pagination conservé, 404 pour cible invisible | adminReadExposesOnlySafeReadinessAndRetainsLocalScope ; tests AdminCompteIntegrationTests conservés |
| AC-INIT08 | Ancien hash V4 au coût non pris en charge | Tentative V5 isolée | Arrêt générique sans secret, pas de réécriture ou migration partielle | unsupportedLegacyBcryptCostStopsMigrationWithoutRewritingOrLeakingHash |

La preuve de non-divulgation inclut constraintFailureDoesNotExposeHashInExceptionOrApplicationLogs :
violation JPA réelle, exception complète et journaux capturés sans mot de passe ni hash.

RM01–RM03/RM13/RM49/RM51/RM58–RM60 ; contributions partielles AA3–AA7/AA9–AA11.
Aucun test ne touche la base locale de développement. TTL, consommation concurrente de jetons,
e-mails, invitation expirée/utilisée, politique minimale et anti-abus attendent le lot suivant.


Résultat local final du lot 3C.1 (24 octobre 2026) : mvnw.cmd clean verify, **121 tests réussis,
0 échec, 0 erreur, 0 ignoré**, dont 26 tests du nouveau socle et les 95 antérieurs.
Aucune validation du parcours d'invitation complet n'en est déduite.

## Lot 3C.2 — preuves d'invitation internes

Ces regroupements AC-INV ne sont pas des comptes de tests exécutés. Aucun endpoint d'acceptation
ni workflow complet d'invitation n'est annoncé livré par ces scénarios.

| Référence | Given / When | Then | Preuve |
|---|---|---|---|
| AC-INV01 | Compte INVITE, émission/renouvellement répétés | Jetons distincts de 32 octets ; SHA-256 seul en base ; transport interne expurgé | cryptographicTokensAre32BytesDistinctAndOnlyDigestsArePersisted |
| AC-INV02 | Clock contrôlée avant/à/après expiration | Fenêtre exacte, durée configurable ; aucune suppression de compte/matricule | developmentTtlIs24HoursAndTokenWorksImmediatelyBeforeExpiry ; exactExpiryAndLaterAreRejectedWithoutDeletingAccountOrNumber |
| AC-INV03 | Jeton révoqué, remplacé, inconnu, mal formé, non canonique ou utilisé | Même refus générique, aucune réactivation | revocationIsIdempotentAndDoesNotReactivateAnything ; renewalRevokesPreviousProofAndKeepsHistoryAndAccount ; invalidUnknownOrNoncanonicalTokensHaveTheSameSafeFailure ; consumedTokenIsNeverUsableAgain |
| AC-INV04 | Deux connexions PostgreSQL, départ synchronisé | Une consommation ; une émission ouverte ; renouvellement/consommation sérialisés | twoConcurrentPostgresTransactionsConsumeExactlyOnce ; twoConcurrentIssuancesHaveExactlyOneOpenInvitation ; renewalAndConsumptionShareTheSameAccountLock |
| AC-INV05 | Contexte JPA préchargé avant un autre commit | Impossible de réutiliser un ancien état pour consommer ou émettre | preloadedJpaInvitationCannotBeConsumedTwiceAfterAnotherCommit ; preloadedJpaAccountCannotBypassTheCurrentInitializedState |
| AC-INV06 | Émission, consommation ou renouvellement suivi d'échec dans une transaction englobante | Rollback ; preuve précédente valide et aucune preuve fantôme | issuanceRollbackDoesNotLeaveAProofOrLoseTheAccount ; consumptionRollbackAllowsARetryInTheFutureAcceptanceTransaction ; renewalRollbackKeepsTheOldProofAndRejectsTheRolledBackNewOne |
| AC-INV07 | Migration V5→V6 et écritures incohérentes | Conservation des comptes/références ; FK, UNIQUE, dates et états terminaux garantis | v5ToV6PreservesExistingInitializedAndInvitedAccountsAndNumberHistory ; postgresEnforcesV6Constraints ; invitationForeignKeyDoesNotCascadeDeleteAnAccount |
| AC-INV08 | INITIALISE, durée absente/invalide, API/OpenAPI et erreurs | Pas d'émission incompatible, de jeton brut exposé ou de nouvelle route ; 400/409 contrôlés préparés | initializedAccountCannotReceiveOrConsumeInvitations ; missingDurationOutsideDevFailsClosedBeforeAnyEmission ; zeroAndNegativeConfiguredDurationsAreRejected ; noInvitationEndpointOrRawTokenSchemaIsExposed ; InvitationExceptionHandlerTests |

Les 121 tests antérieurs sont conservés. Résultats réellement exécutés et contributions AA partielles
dans le [dossier du lot 3C.2](lot-3c2-invitations.md). SMTP, politique de mot de passe, vérification
d'e-mail et acceptation utilisateur restent des lots futurs.

## Historique de la préparation issue #23 — socle interne et livraison chiffrée

Ces scénarios ne valident pas des endpoints non implémentés.

| Scénario | Given / When | Then / preuve |
|---|---|---|
| AC-PREP01 | Cinq profils INVITE actifs, preuve valide ; acceptation interne | BCrypt et INITIALISE dans le même commit, IDs/matricule conservés, login/me JWT utilisables : atomicAcceptanceInitializesBcryptAndKeepsIdLoginAndNormalJwt |
| AC-PREP02 | Compte ou Client désactivé ; acceptation | Refus sans réactivation ni consommation : acceptanceNeverReactivatesDisabledAccountsOrClientsAndLeavesProofUsable |
| AC-PREP03 | Deux acceptations concurrentes ou rollback après acceptation | Une initialisation ; après rollback preuve réutilisable : concurrentAcceptanceHasOnePasswordInitialization ; acceptanceAndPasswordInitializationRollbackTogether |
| AC-PREP04 | Preuve et outbox dans une transaction puis échec | Aucune preuve ou ligne fantôme : failedCreationRollsBackBothProofAndEncryptedOutbox |
| AC-PREP05 | Deux workers ou fournisseur SMTP en échec | Une livraison par commit ; reprise sans message externe en base/log : concurrentDispatchesDoNotSendTheSameCommittedMessageTwice ; unsafeSmtpFailureIsRetriedWithoutPersistingOrLoggingItsMessage |
| AC-PREP06 | Preuve obsolète ou contenu altéré | Annulation avec retrait du ciphertext, ou reprise sans envoi : obsoleteInvitationsAreCancelledAndTheirCiphertextIsRemoved ; modifiedCiphertextNeverReachesTheTransport |
| AC-PREP07 | AES-GCM avec AAD altérée ou mauvaise clé | Échec générique ; aucune clé de secours : InvitationPayloadCipherTests |
| AC-PREP08 | Configuration SMTP/URL dangereuse, envoi simulé | TLS et identité serveur, fragment, pas de fuite d'exception : SmtpInvitationTransportTests |
| AC-PREP09 | Écritures SQL incohérentes et migration V6→V7 | Contraintes et conservation des comptes : outboxConstraintsRejectInconsistentRows ; v6ToV7PreservesExistingAccountsAndDoesNotQueueEmails |

À cette étape historique, D1/D2/D3 bloquaient les tests HTTP ; DEC-COMPTES-006 les a résolus. Les preuves de réalisation figurent ci-dessous.
Les tests internes ne sont pas présentés comme une validation de ces permissions.

## Parcours REST complet — DEC-COMPTES-006 / issue #23

Les 224 tests précédents restent conservés. Les classes AccountWorkflowHttpIntegrationTests
(69 cas) et BootstrapContextIntegrationTests (1 cas) ont passé le lancement ciblé : 70 succès,
0 échec, 0 erreur, 0 ignoré. Résultat complet et CI consignés dans le
[dossier de réalisation](parcours-invitations-preparation.md#preuves-de-validation).

| Scénario Given / When / Then | Preuve automatisée |
|---|---|
| GLOBAL demande les trois catégories → 201, matricule généré, activités vraies, INVITE sans accès | globalCreatesEveryClientCategoryAtomically |
| LOCAL demande LOCAL dans sa zone → 201 ; VIP/OCCASIONNEL/hors zone → 403 | localCreatesOnlyLocalInOwnZone, localCannotCreateOtherCategoriesEvenInOwnZone, localCannotCreateOutsideItsZoneWithoutDisclosingExistence |
| GLOBAL crée GLOBAL/LOCAL → 201 ; LOCAL tente l'un des deux → 403 | globalCreatesAdministratorsWithCanonicalEmail, localCannotCreateAnyAdministrator |
| Anonyme/CLIENT appelle chaque route admin → 401/403 | administrativeEndpointsRequireAuthenticationAndAdminRole |
| DTO incomplet, zone invalide ou champs de sécurité ajoutés → 400/404, aucune écriture partielle | invalidClientRequestsAreRejectedWithoutPartialWrites, invalidAdministratorShapesAreRejected |
| E-mails ne différant que par casse, y compris en concurrence → un succès, un 409 générique | duplicateAdministratorEmailIsCaseInsensitiveAndGeneric, concurrentDuplicateCreationIsOne201AndOne409 |
| Échec après création et preuve → rollback Client/Compte/réservation/preuve/outbox | creationRollsBackClientNumberAccountProofAndOutboxOnFailure |
| Cinq profils invités acceptent puis login/me → BCrypt, JWT et IDs/matricules conservés | acceptanceEnablesNormalLoginForAllFiveProfiles |
| Preuve expirée/révoquée/utilisée/invalide → même 400 sans secret | unavailableInvitationsReturnSameGeneric400 |
| Minimum 15 points de code / maximum 72 octets ; UTF-8, emoji et absence de composition | acceptanceValidatesCodePointsBytesAndForbiddenFields, passwordBoundariesAreAcceptedWithoutCompositionRequirement |
| Compte ou Client désactivé → refus, preuve non consommée, aucune réactivation | acceptanceNeverReactivatesDisabledAccountOrClient |
| Renvoi autorisé → 202, ancienne preuve révoquée, ancien ciphertext supprimé | renewRevokesPreviousTokenAndUsesOnlyStoredRecipient, renewalCancelsPendingCiphertextAndKeepsNumber |
| Renvoi tente de changer le destinataire → 400 ; hors zone/absent → même 404 ; catégorie interdite → 403 | renewalCannotRedirectDeliveryOrModifySecurityFields, renewalIsInvisibleOutsideScopeAndForbiddenForVipInScope |
| Renvoi sur compte initialisé/inactif/client inactif/contact absent → 409 sans nouvelle preuve | incompatibleRenewalIs409AndDoesNotIssueAnotherToken |
| Deux POST accept simultanés → exactement un 204 et un 400 | concurrentAcceptanceThroughHttpSucceedsExactlyOnce |
| Ancien compte avec password court → login inchangé | acceptedPasswordMinimumIsNotRetroactiveForExistingAccounts |
| Bootstrap concurrent → un GLOBAL INVITE ; tout GLOBAL préexistant bloque le second amorçage | bootstrapIsSerializedAndNeverCreatesASecondGlobal, bootstrapRefusesEvenInactiveOrInvitedExistingGlobal |
| Migration V7→V8 → ID/hash/activités conservés, contact NULL sans backfill | v7ToV8PreservesExistingIdentityHashActivitiesAndRelations |
| Swagger expose les quatre POST ; contexte opérateur sans HTTP et sans création automatique | swaggerDescribesAllFourEndpointsAndNoPublicAdministrativeAccess, operatorContextStartsWithoutHttpOrAutomaticAccountCreation |

Ces regroupements ne renumérotent pas les 38 AC historiques. Les conditions D1/D2/D3 de la préparation
sont désormais résolues par DEC-COMPTES-006. Les autres transitions, statuts et anti-abus restent hors lot.
