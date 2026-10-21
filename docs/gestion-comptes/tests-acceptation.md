# Tests d'acceptation — comptes centralisés

**Actualisation lot 1 / DEC-COMPTES-003 :** la cible est e-mail ADMIN et matricule CLIENT ; les descriptions antérieures d'un login client indépendant sont historiques. Ce lot livre uniquement les séquences et l'historique, sans changer login/me ou les permissions. Les tests automatisés AC-NUM01–AC-NUM12 sont décrits dans le [dossier du lot](lot-1-numerotation.md) ; ils sont distincts des scénarios documentaires ci-dessous.


[Décisions](decisions.md) · [Permissions](matrice-permissions.md) · [Contrats](contrats-api.md).
Issue [#7](https://github.com/Zoom225/Gestion-covoiturage/issues/7), 21 octobre 2026.

**Scénarios spécifiés, non exécutés dans cette mission documentaire.** Les nouvelles API ne sont pas implémentées. « À COMPLÉTER » désigne une attente dépendant d'un détail P01–P11 ; aucune valeur fonctionnelle n'est inventée pour rendre le test exécutable.

## 1. Preuves existantes consultées

| Source | Couverture lue dans le code | Écart pour la cible |
|---|---|---|
| [CovoiturageApplicationTests](../../src/test/java/com/plateforme/covoiturage/CovoiturageApplicationTests.java) | V1 vers V2 conservant C0001, Flyway, Hibernate/JPA, UNIQUE numéro, LOCAL sans zone, FK et suppression d'une zone référencée | Nouvelles séquences, reprise et historiques à tester après conception |
| [SecurityIntegrationTests](../../src/test/java/com/plateforme/covoiturage/SecurityIntegrationTests.java) | Login/me, BCrypt salé, désactivation compte/client, JWT, droits depuis PostgreSQL, rôle/catégorie distincts, sonde administrative de zone, CORS, JSON/validation, Swagger dev | Pas d'inscription/invitation/CRUD comptes ; compléter login explicite des cinq profils et vraies opérations administratives |
| [JwtConfigurationTests](../../src/test/java/com/plateforme/covoiturage/security/JwtConfigurationTests.java) | Refus des secrets JWT manquants/mal formés/trop courts et TTL non positif | Paramètres de session métier et nouveaux parcours à définir |
| [SwaggerProductionIntegrationTests](../../src/test/java/com/plateforme/covoiturage/SwaggerProductionIntegrationTests.java) | Documentation désactivée en production même avec dev | Conserver cette preuve lors des futurs changements |

Le README et la PR #6 rapportent 36 tests réussis lors du lot précédent. Ce dossier consulte ces preuves sans annoncer un nouveau passage Maven. Aucun test applicatif ou accès aux données de développement n'est exécuté ici.

Les sondes /api/admin/probe sont uniquement des fixtures de test. Les futurs tests vérifieront les véritables API et ne considéreront pas ces sondes comme une fonctionnalité livrée.

## 2. Scénarios Given / When / Then

Les résultats HTTP nouveaux sont ceux des contrats proposés. Les Pxx sont définis dans les [décisions](decisions.md).

### Inscription, privilèges et identité

| ID | Given (Étant donné) | When (Quand) | Then (Alors) | Référence / dépendance |
|---|---|---|---|---|
| AC01 | Un visiteur avec identifiant disponible, e-mail valide et mot de passe conforme | Il soumet le DTO public autorisé | 201 proposé ; exactement un Client OCCASIONNEL et un Compte CLIENT associés ; numéro O généré ; BCrypt en base ; aucun accès avant preuve e-mail et aucun JWT de connexion | DEC/A1/A3/A7 ; RM04/RM11/RM58 ; P01–P04/P08/P09/P11 avant implémentation |
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
| AC14 | Un LOCAL de zone A, des clients A/B, des clients sans zone et des comptes non consultables ; un GLOBAL | Ils consultent plusieurs pages avec recherche/filtres et totaux | LOCAL ne reçoit que les types de comptes autorisés dans A ; aucun total/compteur hors zone ; filtre zone B n'élargit jamais ; GLOBAL peut consulter le réseau autorisé | A6 ; RM49/RM51/RM57 ; visibilité des comptes ADMIN P07 |
| AC15 | Un LOCAL connaît l'id d'un compte de B ou d'un VIP/OCCASIONNEL sans zone | Il demande le détail ou une modification administrative | 403 selon contrat proposé, sans données de la cible et sans modification ; GLOBAL dispose des seules opérations validées pour ces ressources | A6 ; RM49/RM51 ; politique d'occultation éventuelle à préciser |
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
| AC31 | Réponses login/me/liste/détail/création/erreur, journaux capturés en test et audits métier | Parcours valides et refus 400/401/403/404/409 sont exercés | Aucun mot de passe, hash, secret JWT, jeton de preuve, stack trace ou SQL dans réponses/logs ; seul login retourne son accessToken prévu ; audit utile sans secret ; pas d'exposition d'entité ; no-store sur réponses sensibles | A7/A8 ; RM60 ; handlers métier 404/409 à développer |

### Comptes fictifs isolés — DEC-COMPTES-002

Ces scénarios sont documentaires et non exécutés. Ils décrivent les garanties attendues d'un éventuel futur outillage de données fictives ; ils n'autorisent aucune création dans la base de développement actuelle. La couverture existante citée plus haut ne démontre pas encore ces garanties.

| ID | Given | When | Then | Référence / dépendance |
|---|---|---|---|---|
| AC32 | Un jeu exclusivement fictif dans un environnement de développement ou de démonstration dont l'isolation est vérifiée | Un futur outillage explicitement activé prépare un compte fictif | Le mot de passe initial suit identifiant + "1234!" si cette convention est retenue ; seul son hash BCrypt est stocké en PostgreSQL via le PasswordEncoder existant ; les valeurs fictives de consultation restent uniquement dans le registre Markdown privé principal ; aucune valeur calculée en clair dans les logs ; aucun compte réel modifié | DEC-COMPTES-002/A7 ; AA6/AA10 ; outillage et preuve d'isolation à spécifier |
| AC33 | Un contexte production et une base éphémère de test avec état initial connu | L'application démarre, y compris avec une demande d'activation de données fictives | Aucun compte de démonstration et aucun mot de passe initial prévisible créés ; aucune insertion ou modification de compte par un initialiseur de démonstration ; les comptes préexistants et leurs hashes restent inchangés | DEC-COMPTES-002 ; AA6/AA10 ; test de contexte production isolé, jamais contre la production réelle |
| AC34 | Un contexte où prod est combiné avec dev ou avec un éventuel profil de démonstration | Le contexte démarre et les points d'initialisation sont sollicités | La présence de prod interdit toute initialisation fictive ; aucun composant de démonstration n'exécute d'écriture de compte, même si son activation est demandée ; même résultat qu'AC33 | DEC-COMPTES-002 ; matrice de combinaisons à adapter aux profils effectivement retenus |
| AC35 | Un environnement non isolé ou contenant un compte réel, même si dev est actif | Une préparation de données fictives tente d'appliquer la convention | Aucune création fictive dans cet environnement et aucune attribution ou réinitialisation prévisible d'un compte réel ; un simple profil dev ne suffit pas à autoriser le traitement ; état des comptes réels inchangé | DEC-COMPTES-002 ; contrôle technique d'isolation à spécifier, refus avant toute écriture |
| AC36 | Une inscription publique OCCASIONNEL avec un mot de passe choisi par le visiteur différent de la convention, y compris en dev | Le parcours d'inscription normal est appelé | Le hash BCrypt correspond au mot de passe choisi, jamais à une substitution par la convention ; CLIENT/OCCASIONNEL imposés, e-mail à vérifier et aucune connexion automatique avant validation | DEC-COMPTES-001/A3/A7 et DEC-COMPTES-002 ; complète AC01/AC10 |
| AC37 | Une création administrative normale autorisée, en développement comme en production | Le compte client ou administrateur est créé puis l'invitation est traitée | Invitation sécurisée limitée et à usage unique ; aucun mot de passe calculé par la convention ni envoyé en clair ; le destinataire choisit son mot de passe via le parcours prévu ; aucun contournement par un paramètre de requête | A7/DEC-COMPTES-002 ; complète AC16/AC27 ; P06/P08/P11 demeurent ouverts |
| AC38 | Un identifiant fictif pour lequel la concaténation avec "1234!" atteint 72 octets UTF-8, puis un autre dépassant cette limite | Le futur outillage prépare les données dans une base fictive isolée | À 72 octets, BCrypt vérifie la valeur exacte ; au-delà, préparation refusée sans troncature ni création partielle ; aucun assouplissement de PasswordEncoder pour la démonstration | A7/DEC-COMPTES-002 ; complète AC24 ; modalités de l'outillage à spécifier |

Le registre privé principal est comptes-demo.local.md. Aucun CSV n'est requis ou recréé ; les éventuels fichiers Markdown/CSV privés doivent rester ignorés et absents des fichiers publiables. Les parcours AC10/AC36 concernent les nouvelles inscriptions publiques ; AC16/AC27/AC37 couvrent les nouvelles invitations administratives normales. Leur articulation exacte reste P06/P08/P11 ; AC11 conserve la compatibilité des comptes préexistants sans application rétroactive implicite.

Pour AC33–AC35, contrôler les composants d'initialisation et comparer l'état des comptes avant/après démarrage et tentative d'activation : l'absence d'une seule valeur en clair dans les fichiers ne prouve pas l'absence de création avec un hash prévisible. Une revue des futurs scripts et migrations doit aussi rechercher les initialisations de comptes fictifs et les valeurs de secours ; ne pas modifier V1/V2.


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
