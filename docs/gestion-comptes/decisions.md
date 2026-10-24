# Gestion centralisée des comptes — décisions

**État courant — issue [#23](https://github.com/Zoom225/Gestion-covoiturage/issues/23), PR [#24](https://github.com/Zoom225/Gestion-covoiturage/pull/24) :** DEC-COMPTES-006 valide les créations GLOBAL/LOCAL, les activités initiales et la politique de mot de passe. Les quatre POST de création/renvoi/acceptation et le bootstrap opérateur sont implémentés. V8 additive ; V1–V7 conservées. Voir [contrats, sécurité, modèle et preuves](parcours-invitations-preparation.md). Les instantanés des lots antérieurs ci-dessous restent historiques.


**Actualisation — lot 3C.2 / issue [#21](https://github.com/Zoom225/Gestion-covoiturage/issues/21) :** DEC-COMPTES-005 livre le service interne de preuves d'invitation (32 octets, SHA-256, durée configurable, révocation/renouvellement et consommation atomique). Aucun endpoint, e-mail, mot de passe ou compte créé. Voir [modèle, concurrence, tests et limites AA](lot-3c2-invitations.md).


**Actualisation — lot 3C.1 / issue [#19](https://github.com/Zoom225/Gestion-covoiturage/issues/19) :** DEC-COMPTES-004 ajoute INVITE/INITIALISE. Compte invité sans hash, accès refusé y compris par JWT ; INITIALISE et activités sont indépendants. Les comptes préexistants restent INITIALISES. Seul le socle est livré ; jetons, e-mails et création administrative restent futurs. Voir [modèle, migration V5 et preuves](lot-3c1-comptes-invites.md).


**État courant — lot 3B / issue [#15](https://github.com/Zoom225/Gestion-covoiturage/issues/15) :** consultation administrative implémentée selon les règles validées le 24 octobre 2026. GLOBAL consulte le réseau ; LOCAL seulement les comptes CLIENT de sa zone, sans visibilité des comptes ADMIN. Pagination 0/20/100, ordre compteId croissant, recherche exacte sans casse, filtres par intersection et même 404 pour absent/invisible. Les propositions du lot 3A ci-dessous sont historiques lorsqu'elles sont remplacées par cet état. Voir [architecture, contrat et preuves TFE](lot-3b-consultation.md).

Dossier de l'[issue #7](https://github.com/Zoom225/Gestion-covoiturage/issues/7), établi le 21 octobre 2026.
Base initiale inspectée pour l'issue #7 : origin/master, commit 8a2a47466df3375ad3e1c2ba4356b15de942fac6, intégrant la PR #6.
Statut initial de l'issue #7 : décisions fonctionnelles validées par le responsable ; propositions et questions ouvertes distinguées. Aucun comportement nouveau livré à cette étape documentaire.

Navigation : [permissions](matrice-permissions.md), [contrats REST](contrats-api.md), [acceptation](tests-acceptation.md).

**État courant — lot 2 / issue [#11](https://github.com/Zoom225/Gestion-covoiturage/issues/11) :** login/me appliquent DEC-COMPTES-003, avec recherche sans casse, stockage canonique et V4 sans conversion automatique. Les constats d'authentification exacte des lots précédents ci-dessous sont historiques. Voir [preuve, migration et limites](lot-2-authentification.md).

**Actualisation documentaire — lot 3A / issue [#13](https://github.com/Zoom225/Gestion-covoiturage/issues/13), 24 octobre 2026 :** base origin/master 39a7b6b (PR #10 et #12 fusionnées). Aucun endpoint administratif livré par ce lot. Le matricule CLIENT est le login généré ; l'e-mail de connexion ADMIN est distinct du contact CLIENT. Les droits de lecture confirmés et les modalités encore ouvertes sont séparés dans les [contrats de consultation](contrats-api.md#5-consultation-administrative--implémentée-lot-3b).

## DEC-COMPTES-006 — création administrative et acceptation validées

**VALIDÉ par le responsable le 24 octobre 2026**, après revue de l'énoncé et du cahier.
Cette évolution précise P05/P07/P08/P09 sans renuméroter RM01–RM60 ni effacer DEC-COMPTES-001/002/003/004/005.

1. GLOBAL crée les clients VIP, LOCAL, OCCASIONNEL et les administrateurs.
2. Tout LOCAL crée uniquement des clients LOCAL de sa propre zone, sans habilitation supplémentaire.
   Il ne crée ni VIP, ni OCCASIONNEL, ni administrateur. Cette précision de création ne résout pas
   les autres transitions de catégorie/habilitations historiques d'A4/P05.
3. Création administrative : Compte.actif=true et, si CLIENT, Client.actif=true ; état INVITE,
   sans mot de passe, donc aucune authentification. L'acceptation ne réactive jamais les activités.
4. Nouveau mot de passe par invitation : au moins 15 caractères Unicode (points de code),
   au plus 72 octets UTF-8, non blanc, aucune règle de composition supplémentaire.
   Aucun changement rétroactif des mots de passe ou du login des comptes existants.

Les renvois appliquent les mêmes droits de création au profil cible, après visibilité SQL ;
destinataire enregistré uniquement. L'adresse de contact CLIENT n'est pas son login et n'est pas
ajoutée au DTO administratif commun. Le parcours d'invitation ne déclare pas une preuve e-mail
distincte de l'inscription publique livrée. DEC-COMPTES-001 et les règles publiques restent intactes.
DEC-COMPTES-002 demeure une exception fictive isolée ; aucun compte fictif n'est créé par ce lot.

V8 ajoute un contact nullable sans conversion des comptes préexistants. Détails et limites dans le
[dossier de réalisation](parcours-invitations-preparation.md). La création/acceptation ne modifie ni
Client.id/Compte.id, ni matricule, catégorie, zone ou activités au-delà de la création validée.


## 1. Références

- Énoncé « Gestion d'un réseau de covoiturage multi-zones », fichier externe ../Enonce_CarShare_Network.docx.
- Cahier référencé par le [README](../../README.md) : ../cahier des charge/FIN-DriveConnect_Cahier_des_charges_FINAL_25P_DIAGRAMMES_UML_EA.docx ; sections 7, 8 (RM01–RM60), 9–14, 16 (AA1–AA11), 18–20 et 25.
- Ces chemins externes sont relatifs à la racine du dépôt ; les deux documents Word sont hors Git.
- Mission du responsable « Issue GitHub : documentation et validation des règles de gestion des comptes » : DEC-COMPTES-001 et A1–A9 validées. Cette décision complète le cahier sans renuméroter RM01–RM60.
- Code, migrations et tests : constat de l'existant, distinct de la cible.
- PadellAout : référence locale de parcours et d'interface seulement, sans autorité métier pour DriveConnect.

L'énoncé définit catégories, préfixes, périmètres, authentification et mots de passe hachés ; il ne définit pas l'inscription autonome ni toutes les opérations administratives. DEC-COMPTES-001 est une évolution explicite. RM01 conserve sa portée sur les opérations protégées ; la nouvelle inscription sera une exception publique expressément limitée.

## 2. Existant à conserver — instantané historique avant le parcours de l'issue #23

| Élément inspecté | Fonctionnement constaté | Limite actuelle |
|---|---|---|
| [Compte](../../src/main/java/com/plateforme/covoiturage/entity/Compte.java) | Identifiant unique, EtatIdentifiants INVITE/INITIALISE, hash BCrypt conditionnel, actif, rôle CLIENT/ADMIN ; client exclusif ou périmètre administratif | ADMIN : e-mail porté par identifiant ; invitation non livrée |
| [Client](../../src/main/java/com/plateforme/covoiturage/entity/Client.java) | Numéro unique, catégorie, actif, zone facultative sauf LOCAL | Générateur V/C/O livré au lot 1 ; API de création/transitions non livrées |
| [Zone](../../src/main/java/com/plateforme/covoiturage/entity/Zone.java) | Identité technique et nom | Pas d'état ni de code métier ; CRUD non livré |
| Enums existants | RoleTechnique CLIENT/ADMIN ; PerimetreAdmin GLOBAL/LOCAL ; CategorieClient VIP/LOCAL/OCCASIONNEL | Ne pas créer cinq rôles ou tables d'utilisateurs |
| [CompteRepository](../../src/main/java/com/plateforme/covoiturage/repository/CompteRepository.java) | Login lower ; lot 3B : sélection et détail par Specification, EntityGraph client/zones | Périmètre appliqué par CompteService avant pagination/count ; pas de mutation administrative |
| [AuthService](../../src/main/java/com/plateforme/covoiturage/service/AuthService.java) | Login BCrypt, erreur générique, refus au-delà de 72 octets UTF-8 ; contrôle INITIALISE, Compte.actif et Client.actif | Pas d'inscription ou de gestion administrative |
| [TokenService](../../src/main/java/com/plateforme/covoiturage/service/TokenService.java) | JWT HS256 : sujet = id Compte ; émetteur, audience, dates, identifiant du jeton | Pas de refresh token ni révocation individuelle |
| [AuthController](../../src/main/java/com/plateforme/covoiturage/controller/AuthController.java) | POST login et GET me ; réponses no-store | Deux opérations d'authentification seulement |
| [ProfileResponse](../../src/main/java/com/plateforme/covoiturage/dto/response/ProfileResponse.java) | Rôle, catégorie, périmètre et zones issus de PostgreSQL | identifiant contient le matricule CLIENT ; états détaillés non exposés |
| [SecurityConfiguration](../../src/main/java/com/plateforme/covoiturage/security/SecurityConfiguration.java) | Stateless ; login public ; me authentifié ; préfixe admin réservé à ADMIN ; reste refusé | Inscription non autorisée et non implémentée |
| [AdminAuthorization](../../src/main/java/com/plateforme/covoiturage/security/AdminAuthorization.java) | Lot 3B : requireAccountReadZone centralise GLOBAL réseau / LOCAL clients de sa zone | canManageZone garde son contrat historique non-null pour les autres opérations |
| [Gestion d'erreurs](../../src/main/java/com/plateforme/covoiturage/exception/GlobalExceptionHandler.java) | Réponses contrôlées 400/401/403 et, lot 3B, 404 uniforme pour absent/invisible | 409 métier et parcours de mutation toujours futurs |

Le convertisseur JWT recharge le compte en PostgreSQL à chaque requête : les permissions ne proviennent pas de données Angular ni de claims de rôle faisant autorité. Conserver ce mécanisme et le PasswordEncoder. Les sondes /api/admin/probe existent seulement dans les tests, sans API administrative métier disponible.

Aucun projet Angular DriveConnect trouvé dans les fichiers du worktree ; sa création relève d'un lot distinct.

### Référence PadellAout

Les services de création et formulaires réactifs, chargements, erreurs et affichage du mot de passe constituent des références utiles. PadellAout distingue connexions administrateur et membre et conserve des états de session séparés dans une gestion Angular centralisée avec stockage navigateur. DriveConnect conserve sa connexion unique, ses deux rôles, son JWT et la mémoire par défaut. Ne pas transférer catégories du padel, matricules, secrets, comptes initiaux, permissions déduites du navigateur ou durée de session.

## 3. DEC-COMPTES-001 — architecture mixte

**VALIDÉE — évolution du cahier.**

Un visiteur peut créer un Client et un Compte associés. Spring Boot impose CLIENT et OCCASIONNEL et génère le numéro. Aucun choix public de VIP, LOCAL, ADMIN, périmètre administratif ou privilège. Les données reçues ne sont jamais mappées directement sur l'entité.

Le mot de passe choisi est haché avec BCrypt. Une transaction lie Client, Compte et écritures nécessaires : une erreur annule les écritures associées. PostgreSQL garantit l'unicité en concurrence. Attribution VIP/LOCAL et création administrative relèvent d'opérations protégées distinctes.

A3 complète ce nouveau parcours d'inscription publique : aucun accès avant vérification de l'e-mail ; aucun JWT de connexion émis lors de la simple inscription. Cette précondition ne s'applique pas rétroactivement aux comptes préexistants. Le traitement d'un échec d'envoi doit rester récupérable sans état de compte incohérent ; modalités P11 et périmètre détaillé en A7.

RM01–RM04, RM09–RM13, RM58–RM60 ; AC01–AC10 dans les [tests](tests-acceptation.md).

## 3 bis. DEC-COMPTES-002 — mots de passe des comptes fictifs isolés

**VALIDÉE par le responsable le 21 octobre 2026 — complément documentaire à A7 et DEC-COMPTES-001, rattaché à l'issue #7.**

Pour un futur jeu de comptes exclusivement fictifs, dans un environnement de développement ou de démonstration isolé uniquement, le mot de passe initial peut suivre la convention **identifiant + "1234!"**. Cette formule est une convention de données fictives, pas un secret réel ni un mot de passe par défaut de l'application.

Conditions et limites :

- Ne jamais appliquer cette convention comme mot de passe initial, valeur de secours ou remplacement aux comptes réels, quel que soit l'environnement.
- Ne jamais initialiser de compte de démonstration ou de mot de passe prévisible en production. La présence de prod exclut cette initialisation même avec dev ou un éventuel profil de démonstration simultanément actif.
- Le seul profil dev ne prouve pas l'isolation : réserver les données fictives à un environnement et une base séparés des comptes réels et de la production. Ne pas modifier un compte existant pour lui appliquer la formule.
- Conserver le mot de passe choisi par l'utilisateur pour toute inscription publique OCCASIONNEL, conformément à DEC-COMPTES-001/A3/A7, y compris en développement. Ne jamais substituer la convention au choix reçu.
- Conserver l'invitation sécurisée A7 pour les comptes créés administrativement par les parcours normaux, y compris en développement. Le futur jeu fictif est distinct de ces parcours ; il n'autorise aucun contournement de l'invitation dans les API administratives.
- Stocker uniquement le hash BCrypt produit par le PasswordEncoder existant. La formule ne dispense ni des limites BCrypt ni du refus de toute troncature silencieuse au-delà de 72 octets UTF-8. Ne pas journaliser la valeur calculée ni l'envoyer en clair par e-mail.
- Ne pas modifier les rôles, catégories, contrôles d'activité ou périmètres pour faciliter une démonstration. Cette décision ne remplace pas la procédure sécurisée d'amorçage du premier GLOBAL réel prévue par A5.

| Parcours | Règle de mot de passe conservée |
|---|---|
| Inscription publique CLIENT OCCASIONNEL | Choix de l'utilisateur ; vérification d'e-mail et activation selon DEC-COMPTES-001/A3 |
| Création administrative normale, compte client ou administrateur | Invitation sécurisée A7 ; aucun mot de passe prévisible attribué par le créateur |
| Futur jeu exclusivement fictif, développement/démonstration isolé | Convention facultative identifiant + "1234!", hashée avec BCrypt |
| Compte réel, environnement non isolé ou production | Convention d'initialisation interdite ; aucune initialisation de démonstration en production |

**Portée de cette issue : documentation seulement.** Aucun compte créé, aucun mot de passe calculé pour un compte existant, aucune donnée PostgreSQL modifiée et aucun mécanisme d'initialisation développé. L'outillage éventuel, son activation explicite et la vérification technique de l'isolation devront être spécifiés dans un lot ultérieur avant implémentation ; aucune initialisation automatique n'est introduite ici.

Cohérence : A7 reste la règle des parcours normaux ; DEC-COMPTES-001 conserve rôle CLIENT, catégorie OCCASIONNEL imposés côté serveur et mot de passe choisi par le visiteur. DEC-COMPTES-002 autorise seulement la préparation future de données fictives isolées. Aucun nouveau droit public ou administratif n'en découle ; RM01–RM60 et A1–A9 ne sont pas renumérotés.

Support de consultation retenu : comptes-demo.local.md est le registre privé principal des cinq profils fictifs. Il reste local et exclu de Git. Le CSV précédemment évoqué est absent et n'est plus requis ; il n'est pas recréé. Aucun mot de passe concaténé ni contenu du registre privé n'est reproduit dans les documents publiables. Voir le [guide du registre](comptes-demo-exemple.md).

Preuves à développer : AC32–AC38 dans les [tests d'acceptation](tests-acceptation.md), notamment absence de données de démonstration en production et en combinaison de profils. Contribution AA6 (sécurité), AA10 (tests à réaliser) et AA11 (documentation).


## 3 ter. DEC-COMPTES-003 — e-mail ADMIN et matricule CLIENT

**Décision fonctionnelle et choix techniques validés par le responsable ; mise en œuvre progressive.**

ADMIN GLOBAL/LOCAL : Compte.identifiant contiendra l'e-mail en minuscules, avec unicité insensible à la casse.
CLIENT VIP/LOCAL/OCCASIONNEL : Compte.identifiant contiendra Client.numero, stocké en majuscules ; saisie en minuscules acceptée.
Un seul AuthService, PasswordEncoder BCrypt et JWT existants ; Compte.id et Client.id conservés.
Le rôle, la catégorie et le périmètre viennent de PostgreSQL, jamais de la saisie ou du navigateur.

Trois séquences PostgreSQL indépendantes V/C/O ; minimum quatre chiffres puis croissance ; historique et aucune réattribution.
Un changement de catégorie autorisé nécessitera nouveau matricule, mise à jour du login et historique dans une transaction.
Aucun changement silencieux des comptes préexistants.

Cette décision remplace la séparation login/matricule de l'A2 historique sans effacer cette ancienne décision.
DEC-COMPTES-001 conserve le mot de passe choisi pour l'inscription OCCASIONNEL et les parcours d'invitation normaux.
DEC-COMPTES-002 garde l'isolation fictive et l'interdiction de production ; la formule fictive s'applique aux matricules CLIENT.
Les mots de passe fictifs ADMIN sont ceux expressément retenus dans la fiche privée, sans dérivation depuis l'e-mail.
Aucune valeur concrète du registre privé n'est publiée.

**Lot 1 livré sur branche :** V3, séquences, réservations et historique ; aucune nouvelle résolution du login, transition de catégorie ou création de compte.
Voir [implémentation, limites et tests du lot](lot-1-numerotation.md). Longueurs/normalisation détaillée, reprise des anciens comptes,
permissions et effets des transitions restent à préciser avant les lots correspondants.

## 3 bis. DEC-COMPTES-004 — initialisation des identifiants

**VALIDÉE — lot 3C.1, issue [#19](https://github.com/Zoom225/Gestion-covoiturage/issues/19).**
EtatIdentifiants distingue INVITE (password_hash NULL) et INITIALISE (hash BCrypt valide).
V5 conserve tous les comptes préexistants INITIALISES, avec IDs, hashes et activités inchangés.
INITIALISE est un prérequis à la connexion et à la résolution d'un JWT, sans réactivation implicite.
Ce n'est pas une preuve d'e-mail ni une permission. Le DTO administratif expose uniquement cet état non sensible.

Cette décision complète A7/A8 et résout le modèle d'invité en P06. DEC-COMPTES-001/002/003
et A1–A6/A9 ne sont pas remplacées. Aucun jeton, envoi, endpoint de création ou compte fictif
n'est livré. [Modèle, migration, tests et limites](lot-3c1-comptes-invites.md).

## 3 ter. DEC-COMPTES-005 — preuves d'invitation

**VALIDÉE — lot 3C.2, issue #21.** Jeton opaque SecureRandom de 32 octets, uniquement son empreinte SHA-256 en V6.
Émission et consommation réservées à l'état INVITE, expiration configurable, usage unique, révocation et renouvellement.
Défaut technique dev : 24 h ; hors dev, configuration explicite requise avant émission.
Le compte, son matricule et les activités sont conservés après expiration ou consommation de la preuve.

Cette consommation interne ne choisit pas de mot de passe et n'initialise pas encore le compte.
Le futur parcours devra unir consommation et initialisation BCrypt dans une transaction englobante,
sans réactivation implicite. Aucun endpoint, envoi ou droit administratif nouveau.
DEC-COMPTES-001/002/003/004 restent conservées. [Détails et preuves](lot-3c2-invitations.md).

## 4. Décisions A1–A9

### A1 — numérotation

**Actualisation lot 1 :** trois séquences indépendantes validées ; V3 implémente la réservation et l'historique. Les mentions de séquence à confirmer ci-dessous sont historiques. Voir le dossier du lot.

**VALIDÉ :** V pour VIP, C pour LOCAL, O pour OCCASIONNEL ; minimum quatre chiffres ; génération automatique avec séquences PostgreSQL ; unicité, non-réutilisation, conservation de l'historique lors des changements de catégorie ; jamais count()+1.

**Proposition technique de dépassement :** compléter à gauche jusqu'à quatre chiffres puis laisser croître : O0001, O9999, O10000. Ne jamais tronquer, remettre à zéro ou imposer une largeur fixe de quatre chiffres. Maximum : P01.

RM09–RM11 écrivent Vxxxx/Cxxxx/Oxxxx ; l'énoncé demande un préfixe suivi de chiffres sans longueur exacte. Le minimum de quatre est la précision validée. Les fixtures existantes à quatre chiffres sont compatibles. V1 utilise TEXT UNIQUE sans CHECK de format : le format cible n'est pas encore garanti par SQL.

**À confirmer avant migration :** séquence indépendante par catégorie ou commune ; recommandation de séquences par catégorie. P01 fixe aussi le numéro courant lors d'un changement de catégorie. Conserver l'id technique Client et réserver les anciens numéros dans un historique unique. Une séquence peut laisser des trous après rollback : ne pas promettre une numérotation sans trous.

Backend : générateur partagé et transitions transactionnelles. PostgreSQL : futures séquences et historique, audit des numéros et initialisation au-delà des valeurs déjà attribuées, y compris les écritures concurrentes pendant la transition. Angular : numéro en lecture seule. Tests AC07–AC09/AC20 ; RM09–RM11/RM58–RM60.

### A2 — identifiant de connexion

**Historique :** le login distinct du matricule décrit ci-dessous est remplacé par DEC-COMPTES-003 pour la cible. Le code de connexion reste inchangé dans le lot 1.

**VALIDÉ :** unique, distinct du numéro client et comparé sans distinction de casse. Préserver les identifiants existants ; audit des collisions après normalisation avant toute migration.

L'existant utilise VARCHAR(100) UNIQUE et recherche exacte. **Cette issue ne change ni login ni index.** L'insensibilité à la casse est une cible à implémenter avec l'unicité SQL après P02 ; aucune conversion silencieuse des comptes en collision.

Backend : normalisation partagée entre création, recherche et connexion. PostgreSQL : stratégie d'unicité après audit et résolution autorisée des collisions. Angular : identifiant indépendant de l'e-mail et du numéro. Tests AC06/AC29 ; RM59.

### A3 — inscription publique

**VALIDÉ :** OCCASIONNEL initial ; mot de passe choisi par le visiteur ; e-mail de contact obligatoire et vérifié avant activation ; aucune zone automatiquement attribuée ; aucune connexion automatique avant validation ; protection contre les inscriptions automatisées.

Le serveur fixe rôle, catégorie et champs de sécurité. Le contrat proposé n'accepte aucune zone publique tant que l'éventuel choix manuel n'est pas précisé (P03). L'absence d'attribution automatique est validée ; l'exclusion durable de tout choix manuel ne l'est pas.

Backend : inscription, preuve d'e-mail, activation et anti-abus. PostgreSQL : coordonnées et état de vérification à modéliser ; empreinte du jeton à usage unique recommandée. Angular : formulaire public, état e-mail à vérifier, puis connexion commune. P03/P04/P08/P11 restent nécessaires. Tests AC01–AC05/AC10/AC27 ; RM01–RM04/RM13/RM58–RM60.

### A4 — catégories

**VALIDÉ :** attribution VIP par GLOBAL exclusivement ; attribution LOCAL par GLOBAL ou ADMIN LOCAL habilité dans sa zone. Contrôle backend et trace auteur/date/motif. Préserver réservations et historique ; ne pas inventer de condition commerciale VIP.

« Habilité » ne signifie pas que tout LOCAL peut promouvoir. Habilitation exacte, autres transitions, numéro courant et réservations futures : P01/P05. Ne pas autoriser implicitement un LOCAL à transformer un VIP parce que la catégorie cible serait LOCAL.

Backend : autorisation partagée, zones source/cible et transaction auditée. PostgreSQL : FK conservées, historique et protection de concurrence futurs. Angular : actions autorisées par le serveur et motif de changement. Tests AC18–AC20 ; RM04–RM12/RM38–RM39/RM49/RM51/RM60.

### A5 — administrateurs

**VALIDÉ :** premier GLOBAL par procédure opérateur sécurisée et ponctuelle, sans secret codé en dur. Seul GLOBAL crée les autres administrateurs. LOCAL ne crée donc ni GLOBAL ni LOCAL. Aucun endpoint public de création d'administrateur.

Interdiction de désactiver le dernier GLOBAL actif, y compris sous concurrence. Les requêtes utilisent des transactions indépendantes, sérialisées par un verrou commun PostgreSQL : chaque transaction acquiert ce même verrou avant de relire les GLOBAL actifs et de décider la désactivation, puis le conserve jusqu'au commit ou rollback. Verrouiller seulement chaque ligne cible ne protège pas deux GLOBAL différents. Le mécanisme concret reste à définir en P06 ; aucune transaction unique partagée entre plusieurs requêtes n'est supposée.

La procédure initiale devrait refuser un second amorçage ; forme opératoire P06. Protéger aussi d'éventuels déclassements/suppressions est recommandé, mais ces droits et opérations ne sont pas spécifiés.

Backend : création GLOBAL seule, invitation A7, invariant concurrent. PostgreSQL : stratégie de verrou à concevoir ; aucun compte créé ici. Angular : création réservée GLOBAL, sans mot de passe temporaire fourni par l'administrateur. Tests AC16/AC17/AC22/AC23 ; RM02/RM49/RM51/RM60.

### A6 — périmètre

**VALIDÉ :** GLOBAL couvre le réseau dans les limites des opérations autorisées ; LOCAL sa zone seulement. Gestion administrative des VIP/OCCASIONNEL sans zone réservée GLOBAL. Un changement de zone contrôle source ET cible.

Appliquer le périmètre avant pagination aux lignes, totaux, compteurs, détails et modifications. Les filtres Angular se combinent avec les restrictions serveur sans les élargir. La zone administrative de gestion ne définit pas toutes les zones de réservation accessibles à un VIP/OCCASIONNEL : ces droits métier restent partiellement ouverts dans le cahier.

Backend : compléter la même classe AdminAuthorization pour la consultation des ressources sans zone et du réseau, sans service d'autorisation concurrent. Conserver le comportement de canManageZone pour les autres opérations ; ne pas supprimer globalement son contrôle de zone non nulle. PostgreSQL : requêtes filtrées et index justifiés. Angular : données autorisées seulement. P05/P07 précisent les droits fins. Tests AC14/AC15/AC18/AC21 ; RM07/RM12/RM49/RM51/RM57.

### A7 — mots de passe

**VALIDÉ :** BCrypt par le PasswordEncoder existant ; mot de passe choisi lors de l'inscription ; invitation sécurisée pour création administrative, jeton à usage unique et durée limitée ; aucun mot de passe envoyé en clair par e-mail ; changement et réinitialisation sécurisés à prévoir.

**Périmètre des parcours d'e-mail et d'invitation :**

| Population | Précondition du nouveau parcours | Limite |
|---|---|---|
| Nouveaux inscrits publics CLIENT OCCASIONNEL | E-mail de contact vérifié avant activation ; mot de passe choisi par la personne | DEC-COMPTES-001/A3 ; pas de connexion automatique avant validation |
| Nouveaux comptes créés administrativement par le parcours normal, CLIENT ou ADMIN | Invitation sécurisée à usage unique et durée limitée pour établir le mot de passe, avant accès au compte | A7 ; l'éventuelle vérification distincte d'e-mail et son articulation avec l'invitation restent P06/P11 |
| Comptes préexistants | Contrôles d'authentification et d'activité actuellement en vigueur | Aucun refus fondé sur l'absence de métadonnées d'e-mail ou d'invitation introduit par cette issue ; toute application rétroactive exige une décision et une migration adaptées |
| Futur jeu exclusivement fictif en environnement isolé | Exception DEC-COMPTES-002 et contrôles de rôle, zone et activité conservés | Aucun détournement du parcours normal d'invitation ; aucune initialisation en production |

L'invitation n'est pas déclarée automatiquement équivalente à une vérification d'e-mail : les modalités ouvertes doivent être précisées avant développement. La création opérateur du premier GLOBAL réel reste la procédure distincte A5/P06.

Conserver la limite applicable de 72 octets UTF-8 et refuser tout dépassement sans troncature. La longueur en caractères seule ne suffit pas. DEC-COMPTES-006 fixe désormais le minimum à 15 caractères Unicode pour les nouveaux mots de passe par invitation ; les autres politiques et durées d'exploitation restent P08.

Backend : cycle de vie des comptes réutilisant l'encodeur et AuthService ; consommation atomique et empreinte des jetons recommandées, sans mécanisme JWT de connexion parallèle. PostgreSQL : historiquement V2 imposait un hash NOT NULL ; V5 (DEC-COMPTES-004) autorise NULL uniquement pour INVITE et impose un hash BCrypt de format/coût valide pour INITIALISE. Aucun mot de passe par défaut ; le socle des preuves est livré au lot 3C.2 ; acceptation avec BCrypt et livraison d'e-mail restent à spécifier. Angular : parcours invitation/changement/réinitialisation futurs ; ne pas conserver le mot de passe après soumission. Tests AC16/AC17/AC24/AC27 ; RM01–RM03/RM60.

Complément documentaire : DEC-COMPTES-002 autorise la convention de mot de passe initial uniquement pour un futur jeu fictif en développement/démonstration isolé. Les parcours normaux conservent le choix utilisateur ou l'invitation A7 ; la production reste exclue. Tests AC32–AC38.

### A8 — statuts

**VALIDÉ :** Compte.actif = accès technique ; Client.actif = état métier. Conserver les deux contrôles ; modifier l'un ne change pas implicitement l'autre. Afficher les deux et l'accès effectif ; historiser les modifications.

Accès CLIENT = INITIALISE ET Compte.actif ET Client.actif ; ADMIN = INITIALISE ET Compte.actif, sous les autres contrôles JWT. L'état d'initialisation est indépendant des deux activités ; ni un changement d'état ni V5 ne les réactive. La vérification d'e-mail précède l'activation publique sans réactiver un client métier désactivé. DEC-COMPTES-006 fixe les activités initiales administratives à true, sans accès tant que INVITE ; réactivation et droits de bascule restent P09.

Backend : changement explicite et audit, effet immédiat via relecture en base même avec ancien JWT. PostgreSQL : booléens conservés, historique futur. Angular : colonnes distinctes et aucun bouton ambigu « utilisateur actif ». Tests AC12/AC13/AC22/AC25 ; RM13/RM60.

### A9 — Angular

**VALIDÉ :** une page de connexion, une session, POST login puis GET me, guards et interceptor Bearer ; JWT en mémoire par défaut. Un 401 d'API protégée invalide la session ; un 403 n'entraîne pas de déconnexion systématique. Aucun rôle issu du navigateur ou du préfixe du numéro.

| Profil relu via /api/auth/me | Destination retenue |
|---|---|
| ADMIN GLOBAL | /admin/global |
| ADMIN LOCAL | /admin/local |
| CLIENT VIP | /client/vip |
| CLIENT LOCAL | /client/local |
| CLIENT OCCASIONNEL | /client/occasionnel |

Un profil incohérent n'ouvre aucun espace protégé. Limiter le Bearer à l'origine API autorisée. Le stockage en mémoire implique une reconnexion après rechargement tant qu'aucun renouvellement n'est validé.

Structure cible : core/auth, core/guards, core/interceptors, core/services ; features/auth, features/admin, features/client ; shared. Reactive Forms, validations, erreurs, chargement et masquage du mot de passe réutilisables. Tableau administratif : identifiant, numéro, rôle, catégorie ou périmètre, zone, Compte.actif, Client.actif, accès effectif et actions autorisées ; recherche, filtres et pagination serveur. Durée de session et détails UX : P08/P10. Tests AC26/AC28/AC30 ; AA8/AA9, RM01–RM03.

## 5. Détails À PRÉCISER

Ces recommandations ne sont pas des permissions supplémentaires.

| Réf. | Ce que les sources fixent / ce qui manque | Recommandation à valider | Impacts backend / PostgreSQL / Angular | Tests |
|---|---|---|---|---|
| P01 | Trois séquences indépendantes et nouveau matricule lors des futures transitions validés ; maximum métier et effets détaillés des transitions ouverts | Séquences par catégorie, historique unique, id Client stable et format extensible | Générateur / reprise et réservation des anciens numéros / lecture seule | AC07–AC09/AC20, limites maximales après choix |
| P02 | Lots 2/3B : login et recherche administrative exacte sans casse PostgreSQL lower ; e-mail minuscule, matricule majuscule, aucun trim. Modification du login toujours ouverte ; recherche partielle non retenue dans 3B | Conserver la canonisation existante pour les futures écritures ; aucun changement silencieux | V4 conservée / recherche exacte utilisant lower / champ de recherche commun | AC-AUTH01–AC-AUTH12 ; AC-CONS06/AC-CONS07 |
| P03 | E-mail public obligatoire ; pas de zone automatique ; autres champs, unicité e-mail et zone manuelle non définis | DTO public minimal e-mail de contact/mot de passe ; matricule et login CLIENT générés par le serveur, aucun identifiant libre ; zone provisoirement exclue (correction lot 3A, DEC-COMPTES-003) | Validation / coordonnées et éventuels index / formulaire minimal | AC01/AC04/AC05 |
| P04 | V2 impose au plus un Compte par Client ; nombre de comptes par personne non défini | Conserver relation existante ; définir la notion de personne avant assouplissement | Politique anti-doublon / UNIQUE client_id conservé / conflits explicites | AC06/AC29 et cas multi-comptes futurs |
| P05 | Création validée DEC-006 : VIP/OCCASIONNEL GLOBAL ; LOCAL GLOBAL ou tout LOCAL de sa zone, sans habilitation supplémentaire. Conditions VIP et transitions restent ouvertes | Ne pas déduire les transitions du droit de création | Politique unique et transactions existantes | Tests HTTP création ; AC18–AC20 restent partiellement futurs |
| P06 | Modèle invité sans hash VALIDÉ et livré au lot 3C.1 (DEC-COMPTES-004/V5) ; preuves V6 livrées au lot 3C.2 ; procédure premier GLOBAL, acceptation utilisateur/envoi, verrou du dernier GLOBAL et modification de périmètre toujours ouverts | Amorçage ponctuel et réduction des GLOBAL à préciser ; aucun mot de passe initial inventé | Réutiliser l'état INVITE/INITIALISE / futurs jetons séparés de V5 / aucun endpoint de création livré | AC-INIT01–AC-INIT08 ; AC16/AC17/AC22/AC23 futurs |
| P07 | Lecture lot 3B conservée ; créations et renvois DEC-006 validés, GLOBAL réseau / LOCAL uniquement CLIENT LOCAL de sa zone. CRUD zones ouvert | Aucun droit de création ADMIN pour LOCAL | AdminAuthorization et périmètre SQL | Tests HTTP 401/403/404 et renvoi |
| P08 | DEC-006 : nouveaux passwords invitation min 15 caractères Unicode, max 72 octets UTF-8 sans composition ; pas rétroactif. V6 et durée dev 24 h conservées | Durée hors dev explicite ; anti-abus et autres politiques restent ouverts | AuthService et PasswordEncoder uniques | Tests HTTP limites, expiration, concurrence et JWT |
| P09 | DEC-006 : activités initiales administratives vraies, accès bloqué par INVITE ; acceptation sans réactivation. Les droits de modification/réactivation restent ouverts | Séparer initialisation et activités | V5/V8, transaction et relecture des états | Tests HTTP états/rollback ; AC25 futur |
| P10 | Cinq destinations et mémoire validées ; aucun frontend trouvé | Lot Angular distinct après contrats ; pas de refresh token inventé | Login/me conservés / aucun changement SQL imposé / session et écrans partagés | AC28/AC30, responsive et accessibilité |
| P11 | E-mails nécessaires ; prestataire, expéditeur, renvoi et reprise d'échec non définis | Livraison après commit avec reprise fiable à concevoir ; aucun secret ou jeton complet journalisé | Envoi / livraison persistante si retenue / confirmation et renvoi selon politique | AC10/AC16/AC27, reprise sans doublon |
| P12 | VALIDÉ lot 3B : index 0 ; défaut 20 ; taille 1–100 ; compteId croissant ; recherche exacte sans casse ; rôle/catégorie/zone/statuts par intersection ; 404 uniforme absent/invisible ; CLIENT authentifié 403 | Appliquer ces choix sans ajouter recherche partielle, tri personnalisable ou accès ADMIN pour LOCAL | WHERE de périmètre et filtres commun au contenu et count ; détail filtré ; 400 contrôlés / V1–V4 inchangées / contrat utilisable par futur Angular | AC-CONS01–AC-CONS08 automatisés dans AdminCompteIntegrationTests |

## 6. PostgreSQL, migration et risques

[V1](../../src/main/resources/db/migration/V1__create_referentials.sql) : zone/client/vehicule ; numéro TEXT UNIQUE, catégories CHECK, FK zone, zone obligatoire LOCAL.
[V2](../../src/main/resources/db/migration/V2__create_accounts.sql) : identifiant VARCHAR(100) UNIQUE, hash BCrypt VARCHAR(60) NOT NULL avec CHECK, actif, rôle, client_id UNIQUE et FK, cohérence CLIENT/ADMIN et GLOBAL/LOCAL, index de zone.

[V3](../../src/main/resources/db/migration/V3__reserve_client_numbers.sql) livre les séquences et l'historique ; [V4](../../src/main/resources/db/migration/V4__canonical_account_identifiers.sql) la canonisation et la cohérence du login CLIENT. V1–V4 restent intactes au lot 3B. Les identités techniques ne génèrent pas les numéros métier.

Les séquences, l'historique et l'unicité normalisée sont livrés par V3/V4. Après arbitrages, de futures migrations pourront traiter les coordonnées et preuves d'e-mail, invitations/réinitialisation et traces des nouvelles opérations ; aucune n'est nécessaire à la consultation du lot 3B. Auditer numéros et collisions de login avant contraintes ; aucune réécriture automatique autorisée ici. Les fixtures de sécurité changent actuellement la catégorie en conservant V0001 : une future contrainte préfixe/catégorie exigera leur adaptation en conservant la preuve qu'une catégorie ne donne jamais ADMIN.

Risques : escalade par JSON, accès hors zone, fuite des totaux, attribution VIP par LOCAL, inscription massive, réutilisation ou consommation concurrente de jetons, double désactivation GLOBAL, collisions et perte d'historique. Autoriser l'inscription future par méthode/route exactes, sans permitAll global sur /api/auth/**. Conserver PostgreSQL, JWT, Swagger, données et secrets.

[V5](../../src/main/resources/db/migration/V5__account_credential_state.sql) ajoute l'état d'initialisation et la cohérence état/hash, sans modifier V1–V4 ni les activités. Le [lot 3C.1](lot-3c1-comptes-invites.md) décrit sa validation sur PostgreSQL jetable ; aucune application à la base de développement pendant ce lot.

[V6](../../src/main/resources/db/migration/V6__secure_invitations.sql) ajoute les empreintes et le cycle de preuve, sans compte ni migration de la base locale ; voir le [lot 3C.2](lot-3c2-invitations.md).

## 7. Traçabilité RM01–RM60

Cette classification couvre le registre entier sans prétendre l'implémenter.

| RM | Contribution documentaire | Décisions / acceptation |
|---|---|---|
| RM01–RM04 | Identité, exception publique limitée, rôle/catégorie et propriété | DEC, A3/A6/A9 ; AC01–AC05/AC11/AC14/AC26 |
| RM05–RM08 | VIP J-30, LOCAL J-10, OCCASIONNEL J-3 et zone LOCAL conservés | A4/A6 ; AC18–AC21 ; tests métier futurs |
| RM09–RM12 | Préfixes, minimum, unicité ; zones VIP seulement si autorisées | A1/A4/A6 ; AC07–AC09/AC20 |
| RM13 | Compte et client actifs ; vérification avant activation | A3/A8 ; AC10/AC12/AC13/AC25 |
| RM14–RM37 | Trajets, privé, capacité, véhicules, réservations, paiement, dette : hors implémentation, aucune règle modifiée | A4 préserve réservations ; tests des futurs modules |
| RM38–RM39 | Validation à la réservation et revalidation à la confirmation | A4/A6, P05 ; AC20/AC21 ; tests métier futurs |
| RM40–RM48 | États, J-1, dette et annulation : hors implémentation, conservation des données | P05 ; futurs tests métier |
| RM49 | Administration LOCAL dans sa zone | A4/A6 ; AC14/AC15/AC18/AC21 |
| RM50 | Régularisation de dette : hors périmètre | Futur module dette |
| RM51 | GLOBAL réseau dans les fonctions autorisées | A4–A6 ; AC16/AC18/AC22 |
| RM52–RM56 | Restauration, dette unique, paiements distincts, J-1 et indisponibilités : hors périmètre | A4 ; futurs modules métier |
| RM57 | Compteurs et statistiques filtrés selon le périmètre | A6 ; AC14 |
| RM58 | Relations, atomicité et contraintes | DEC/A1/A3 ; AC06–AC09/AC16/AC31 |
| RM59 | Numéros et login normalisé cible uniques en SQL | A1/A2 ; AC06–AC09/AC29 |
| RM60 | Auteur/date/motif, statuts, numéros et événements sans secrets | A1/A4/A8 ; AC20/AC25/AC31 |

Anomalie source : RM07 se termine dans le cahier par une phrase incomplète (« La simple connaissance. »). L'énoncé confirme clairement le périmètre LOCAL ; aucune règle supplémentaire n'en est déduite. La route indicative /api/clients/me en section 20 ne justifie pas de dupliquer /api/auth/me déjà livré.

## 8. Acquis AA1–AA11

| AA | Contribution de cette issue documentaire | Preuve future |
|---|---|---|
| AA1 | Besoin, acteurs et parcours | Revue fonctionnelle |
| AA2 | Exigences, décisions et questions | Validation du dossier |
| AA3 | Relations et impacts du modèle | Modèle actualisé après arbitrages |
| AA4 | Contraintes et risques de migration | Migrations futures testées |
| AA5 | Responsabilités des couches | Services intégrés sans doublons |
| AA6 | JWT, BCrypt, rôles et périmètres | Tests de sécurité |
| AA7 | Contrats REST conceptuels | API et OpenAPI futurs |
| AA8 | Structure, session et écrans cibles | Angular responsive futur |
| AA9 | Validations et erreurs | Tests HTTP et formulaires |
| AA10 | Scénarios, concurrence et Git | Tests automatisés et CI ultérieurs |
| AA11 | Dossier navigable et issue | Documentation et démonstration |

Contribution documentaire ne signifie pas validation technique complète des acquis.

## 9. Étapes futures et stratégie Git

### Plan initial — historique de l'issue #7

Le plan et les instantanés suivants décrivent la préparation initiale ; les lots 1 et 2 ont depuis livré générateur, historique et authentification DEC-COMPTES-003. La situation du lot 3A figure à la fin de cette section.

1. Valider le dossier et préciser les questions bloquantes du prochain lot.
2. Concevoir création atomique, invitations et numérotation ; nouvelles migrations seulement après P01/P02/P06.
3. Réaliser les API administratives et le périmètre avant pagination après P05/P07/P09.
4. Réaliser inscription, vérification d'e-mail et activation après P03/P04/P08/P11 ; conserver login/me.
5. Créer Angular séparément puis session, tableau et cinq destinations A9.
6. Tester sur PostgreSQL isolé, tester sécurité/Angular, documenter puis préparer une PR française après accord.

Chaque futur lot aura issue et branche après validation ; aucune issue de développement supplémentaire créée ici. Besoins nouveaux conceptuels : service central des comptes, générateur de numéros, composants invitation/vérification, DTO administratifs ciblés. Noms à fixer sans dupliquer AuthService, TokenService, repositories ou autorisations.

### État initial de la préparation

Lors de la création du worktree documentaire, le dossier original était sur chore/issue-5-configuration-swagger, HEAD 5e4d049, avec README.md et application-dev.properties modifiés et deux scripts IntelliJ non suivis. Le fetch effectué à cette étape a actualisé origin/master à 8a2a474 ; master local est resté à 140597c, deux commits derrière. Ce relevé historique ne décrit pas toutes les modifications locales intervenues ensuite.

### Instantané historique — correction documentaire de l'issue #7

| Répertoire | Branche / HEAD | Modifications locales conservées |
|---|---|---|
| Dossier original covoiturage, hors périmètre d'écriture | chore/issue-5-configuration-swagger / 5e4d049 | .gitignore, README.md et application-dev.properties modifiés ; scripts/intellij-env.bat et scripts/intellij-env.ps1 non suivis |
| Worktree covoiturage-docs-gestion-comptes | docs/gestion-comptes-regles-contrats / 8a2a474 | .gitignore modifié ; cinq documents docs/gestion-comptes non suivis, destinés au futur commit documentaire après accord |

Le registre privé principal comptes-demo.local.md reste dans le dossier original, ignoré par Git et sans copie dans le worktree documentaire. Le CSV est absent. Les exclusions Markdown et CSV sont consolidées dans le .gitignore du worktree sans retrait des règles antérieures.

Cet état est un instantané avant commit : aucun fichier indexé, aucun commit, push, merge ou PR dans cette correction. Aucun stash, reset, clean, transfert de correction locale ou changement de branche n'est effectué. Java, Angular, V1/V2, PostgreSQL et les comptes restent inchangés.

La branche documentaire suit encore origin/master, base locale 8a2a474. Une publication ultérieure, uniquement après accord, devra cibler sa propre branche distante. Le futur commit devra inclure seulement les cinq documents et le .gitignore documentaire ; les modifications du dossier original et les registres privés restent hors publication.

### Lot 3A — instantané historique avant le lot 3B

Base dédiée : origin/master 39a7b6b ; branche docs/lot-3a-consultation-comptes dans covoiturage-docs-lot-3a-consultation-comptes. Les quatre worktrees préexistants et leurs modifications locales restent hors périmètre. Les états historiques ci-dessus ne décrivent pas cette nouvelle branche. L'issue [#13](https://github.com/Zoom225/Gestion-covoiturage/issues/13) porte uniquement la correction et la préparation documentaires.

Droits de lecture confirmés : GLOBAL consulte les comptes du réseau ; LOCAL consulte seulement les comptes CLIENT liés à sa zone. Les comptes sans zone sont exclus de la lecture LOCAL. La visibilité des comptes ADMIN pour LOCAL demeure P07, sans permission accordée en attendant ; son propre profil reste accessible via me.

Correction de cohérence, sans nouvelle décision métier : le login CLIENT est le matricule généré (DEC-COMPTES-003, V3/V4), jamais un champ public libre. Dans le DTO administratif proposé, identifiant contient l'e-mail de connexion pour une ligne ADMIN autorisée au GLOBAL ; cette donnée ne peut être supprimée au titre d'une interdiction générale des e-mails. L'e-mail de contact CLIENT ne fait pas partie du DTO commun proposé ; une éventuelle exposition ultérieure nécessite finalité et permission explicites.

Prochain lot recommandé : consultation seule, après validation de P12 et des combinaisons de filtres. Réutiliser AdminAuthorization et CompteRepository ; un seul service de gestion des comptes, transactions de lecture, DTO/mapper explicites et GlobalExceptionHandler étendu aux erreurs nécessaires. Aucun second AuthService ni générateur. Invitations, créations, transitions et statuts restent des lots distincts soumis à P03–P11. Aucune migration nécessaire pour ce dossier ; tout éventuel index futur sera justifié par les requêtes retenues et ajouté par migration, sans toucher V1–V4.

Traçabilité du lot 3A : RM01–RM04 (identité/permissions), RM09–RM13 (matricule et états conservés), RM49/RM51/RM57 (périmètres et comptages), RM58–RM60 (intégrité et données sensibles). Les autres RM restent classées en section 7, sans changement. Contribution directe AA1/AA2/AA11 ; préparation AA3–AA7/AA9/AA10 ; AA8 reste un lot Angular distinct. La documentation ne vaut pas validation applicative de ces acquis.

### Lot 3B — règles de consultation désormais validées et implémentées

Le responsable a validé les paramètres et le comportement de lecture de P12, ainsi que l'exclusion de tous les comptes ADMIN pour LOCAL. Cette précision remplace les propositions ouvertes du lot 3A sans effacer leur historique. La visibilité des comptes ADMIN pour LOCAL n'est plus une question ouverte dans le contrat livré. La recherche exacte et les intersections ne nécessitent pas les décisions de création, invitation, catégories ou statuts encore ouvertes.

Deux endpoints de lecture, aucun endpoint de mutation ; même 404 générique pour absent ou invisible et 403 pour un acteur sans permission administrative. Les paramètres et les filtres par zone applicable sont définis dans les [contrats courants](contrats-api.md#5-consultation-administrative--implémentée-lot-3b). Les mots de passe et hashes ne sont jamais exposés ; JWT/BCrypt, identités et V1–V4 sont conservés. Le [dossier du lot 3B](lot-3b-consultation.md) décrit architecture, modèle réutilisé, tests et couverture partielle des AA1–AA11.
