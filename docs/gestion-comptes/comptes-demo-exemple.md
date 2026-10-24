# Registre privé des comptes fictifs de démonstration

Préparation documentaire pour l'[issue #7](https://github.com/Zoom225/Gestion-covoiturage/issues/7).
Références : [DEC-COMPTES-001, DEC-COMPTES-002 et A1–A9](decisions.md), [permissions](matrice-permissions.md), [contrats](contrats-api.md), [AC32–AC38](tests-acceptation.md).
Base initiale : 8a2a474, 21 octobre 2026. Actualisation documentaire lot 3A / [#13](https://github.com/Zoom225/Gestion-covoiturage/issues/13) : origin/master 39a7b6b, 24 octobre 2026 ; DEC-COMPTES-003 et V3/V4 font désormais référence.

**Registre privé principal retenu : comptes-demo.local.md.** La fiche contient cinq profils fictifs NON_INITIALISE ; elle ne prouve ni leur existence en PostgreSQL ni la disponibilité des identifiants ou numéros. Aucun compte, script ou mécanisme d'initialisation n'est créé.

Le CSV précédemment envisagé est absent et n'est plus requis. Il n'est pas recréé. Les exclusions CSV sont conservées par précaution pour les anciens emplacements, sans obligation de maintenir un second registre.

## 1. Identifiant de connexion et numéro client

[Compte](../../src/main/java/com/plateforme/covoiturage/entity/Compte.java) et [Client](../../src/main/java/com/plateforme/covoiturage/entity/Client.java) conservent leurs identités techniques et la relation compte.client_id. **État courant DEC-COMPTES-003, livré au lot 2 :** pour CLIENT, Compte.identifiant = Client.numero généré en majuscules ; pour ADMIN, Compte.identifiant contient l'e-mail de connexion en minuscules. La saisie sans distinction de casse est acceptée. V4 garantit l'unicité normalisée et la correspondance CLIENT, sans conversion silencieuse des anciens comptes.

Les anciennes propositions de login client indépendant et de recherche exacte sont historiques et ne doivent pas être utilisées pour préparer de nouveaux comptes. LoginRequest conserve identifiant et password ; aucun second système de connexion.

Pour les comptes fictifs isolés uniquement, la convention DEC-COMPTES-002 porte sur le matricule CLIENT. L'exception de mot de passe ADMIN de DEC-COMPTES-003 reste dans la fiche privée ; ne pas dériver automatiquement un mot de passe depuis l'e-mail. Aucune valeur concrète de cette fiche n'est reproduite ici.

JWT existant conservé : login unique puis GET /api/auth/me ; sujet JWT = id Compte, permissions relues en PostgreSQL. Aucun accès au registre par une API, Angular ou Swagger.

## 2. Emplacement et consultation locale

Chemin de référence, relatif à la racine du checkout contenant le registre privé :

~~~text
src/main/java/com/plateforme/covoiturage/comptes-demo.local.md
~~~

Dans l'environnement de préparation, cette fiche reste dans le dossier original covoiturage, à côté de CovoiturageApplication.java. Aucune copie n'est ajoutée au worktree documentaire. Ouvrir la fiche localement dans l'éditeur ou son aperçu Markdown.

Le lien historique vers un CSV dans la fiche privée est obsolète : il ne définit plus la source du registre et n'est pas nécessaire à la consultation. Le dossier original, y compris cette fiche, reste intact dans cette correction exclusivement documentaire ; aucune synchronisation CSV/Markdown n'est requise.

Le présent document est la description versionnable du registre, sans mot de passe concaténé. La fiche privée ne doit jamais être ajoutée à Git, jointe à la PR ou incluse dans un artefact de livraison.

Le [.gitignore du worktree](../../.gitignore) conserve les exclusions existantes et protège les deux noms privés aux emplacements connus :

| Emplacement | Markdown privé | CSV historique ou éventuel |
|---|---|---|
| À côté de CovoiturageApplication.java | /src/main/java/com/plateforme/covoiturage/comptes-demo.local.md | /src/main/java/com/plateforme/covoiturage/comptes-demo.local.csv |
| Ancien dossier scripts/demo | /scripts/demo/comptes-demo.local.md | /scripts/demo/comptes-demo.local.csv |

Ces protections n'impliquent la création d'aucun fichier privé supplémentaire.

## 3. Présentation des cinq profils

La fiche principale comporte deux groupes, Administrateurs et Clients, et une fiche par profil. Les informations pertinentes sont : identifiant, numéro client éventuel, rôle technique, périmètre administratif éventuel, catégorie client éventuelle, zone associée et son id lorsqu'il sera résolu, Compte.actif, Client.actif si applicable, mot de passe fictif et état local d'initialisation.

Le tableau ci-dessous utilise des repères symboliques, sans identifiant de connexion utilisable ni mot de passe. Les matricules devront provenir du générateur existant et être reportés dans la seule fiche privée après succès. Les statuts actifs sont uniquement des valeurs prévues pour une future démonstration isolée.

| Profil | Identifiant attendu, symbolique | Numéro attendu, symbolique | Rôle | Périmètre admin | Catégorie | Zone prévue | Compte.actif prévu | Client.actif prévu | État |
|---|---|---|---|---|---|---|---|---|---|
| Administrateur GLOBAL | EMAIL_ADMIN_GLOBAL_FICTIF | — | ADMIN | GLOBAL | — | Aucune | true | — | NON_INITIALISE |
| Administrateur LOCAL | EMAIL_ADMIN_LOCAL_FICTIF | — | ADMIN | LOCAL | — | Zone Demo A, id à résoudre | true | — | NON_INITIALISE |
| Client VIP | MATRICULE_V_GENERE | MATRICULE_V_GENERE | CLIENT | — | VIP | Aucune | true | true | NON_INITIALISE |
| Client LOCAL | MATRICULE_C_GENERE | MATRICULE_C_GENERE | CLIENT | — | LOCAL | Zone Demo A, id à résoudre | true | true | NON_INITIALISE |
| Client OCCASIONNEL | MATRICULE_O_GENERE | MATRICULE_O_GENERE | CLIENT | — | OCCASIONNEL | Aucune | true | true | NON_INITIALISE |

NON_INITIALISE est une métadonnée locale de présentation, pas un nouvel enum métier ni un champ en base. INITIALISE ne pourra être indiqué qu'après création transactionnelle effectivement réussie et contrôlée.

L'accès d'un CLIENT effectivement créé dépend de Compte.actif ET Client.actif ; celui d'un ADMIN de Compte.actif, sous les autres contrôles JWT. Un statut actif prévu dans la fiche n'accorde aucun accès.

Aucun vrai e-mail ni donnée personnelle n'est nécessaire. Les éventuels scénarios de preuve d'e-mail devront utiliser des adresses fictives et une réception isolée, à spécifier.

## 4. Compatibilité avec les entités et PostgreSQL

| Constat V1–V4 et code courant | Conséquence pour un futur outil |
|---|---|
| V4 : identifiant canonique, index unique lower et correspondance du login CLIENT au numéro | CLIENT : matricule généré ; ADMIN : e-mail minuscule ; contrôler collisions sans conversion silencieuse |
| V3 : séquences V/C/O, réservation et historique ; générateur central livré | Réutiliser ce générateur ; aucune allocation depuis le tableau symbolique, jamais count()+1 |
| CLIENT exige un client_id unique, sans champs administratifs | Créer Client/Compte atomiquement ; aucune seconde entité ou table de comptes |
| ADMIN sans Client ; GLOBAL sans zone ; LOCAL avec zone FK | Pas de numéro ou catégorie client pour ADMIN ; résoudre zone_admin_id pour un LOCAL |
| Client LOCAL exige une zone FK | Résoudre zone_client_id avant création ; un simple nom indicatif ne satisfait pas la contrainte |
| Zone a id et nom ; nom non UNIQUE | Refuser un nom ambigu ou une zone absente, sans choisir arbitrairement le premier résultat |
| BCrypt VARCHAR(60) NOT NULL avec CHECK | Réutiliser PasswordEncoder ; seul le hash est stocké en PostgreSQL |
| Limite BCrypt de 72 octets UTF-8 | Vérifier la valeur fictive applicable selon DEC-COMPTES-002/003 ; refuser tout dépassement sans troncature |
| Compte.actif et Client.actif distincts | Afficher et contrôler les deux états, sans propagation implicite |
| Aucun champ demo/fictif ni métadonnées d'e-mail ou invitation | Définir une preuve externe d'isolation ; ne pas inventer de flag ou de migration dans cette issue |

Aucune modification de V1–V4, aucune connexion PostgreSQL et aucun id de compte ou zone recherché dans cette correction.

## 5. Protection des valeurs fictives et portée des parcours

Le registre Markdown est le support privé principal de consultation en clair des seules valeurs fictives. En base, conserver uniquement les hashes BCrypt. Aucun mot de passe réel ne doit être ajouté à la fiche.

- Démonstration uniquement dans un environnement et une base fictifs isolés ; prod interdit le traitement même combiné à dev.
- Aucun compte créé ou activé automatiquement au démarrage Spring Boot.
- Aucune valeur de mot de passe issue de la fiche dans Java, application.properties, Flyway, logs, erreurs, API normales, Angular ou Swagger.
- Aucun ajout forcé du registre à Git, aucune publication dans la PR, archive, capture publique ou artefact de livraison. Git ignore ne remplace pas les protections d'accès du poste ni les contrôles de packaging.
- Aucune réinitialisation de compte réel ou existant depuis la fiche.
- La fiche est une aide de consultation ; elle ne constitue ni un fichier de commandes ni une autorité de permissions. Les données persistées et les contrôles backend restent déterminants.
- Aucun CSV requis pour consulter la fiche. Si un export privé est un jour autorisé séparément, les règles d'exclusion restent applicables ; aucun export n'est développé ici.

Portée conservée : nouvelle inscription publique OCCASIONNEL avec mot de passe choisi et e-mail vérifié avant activation ; nouvelle création administrative normale par invitation sécurisée avant accès ; modalités d'une vérification d'e-mail distincte pour ces invitations encore P06/P11.

Les comptes préexistants gardent les contrôles actuels. Aucune vérification d'e-mail ou invitation rétroactive n'est imposée sans décision et migration adaptées. L'exception fictive DEC-COMPTES-002 reste distincte de ces parcours normaux et du premier GLOBAL réel A5.

## 6. Étude du futur script initialisation-demo.ps1

**Aucun script exécutable créé.** scripts/demo/initialisation-demo.ps1 reste un nom proposé. Le choix de l'outillage et la preuve d'isolation doivent être validés avant implémentation.

1. Exécution opérateur explicite et contrôle de la destination effective avant écriture. Aucun raccordement au démarrage Spring Boot ou aux migrations.
2. Refuser prod, y compris avec dev. Un profil, un nom de base, localhost ou la présence d'une fiche ne suffisent pas à prouver l'isolation ; si elle n'est pas démontrée, refuser le traitement.
3. Valider la définition du jeu fictif, les rôles, catégories, statuts et collisions. Ne pas interpréter la fiche Markdown comme du code exécutable ni réinitialiser de comptes existants.
4. Résoudre les ids des zones fictives, refuser les noms ambigus et les zones absentes. Une éventuelle préparation des zones est une opération distincte à valider.
5. Réutiliser le générateur métier livré au lot 1, sans imposer les repères symboliques ni réutiliser un ancien numéro.
6. Réutiliser PasswordEncoder et les entités/repositories existants. Définir l'intégration Java depuis PowerShell sans dupliquer le login, les comptes ou les règles.
7. Garantir l'atomicité Client/Compte ; préciser l'atomicité du lot et la relance sans écrasement avant codage.
8. Mettre à jour la fiche privée après succès en base seulement, avec les numéros, zones et statuts réellement confirmés. Définir la reprise d'un échec d'écriture de la fiche après commit PostgreSQL sans nouvelle création ni changement de mot de passe.
9. Contrôler login/me pour les cinq profils et les scénarios AC32–AC38 dans une base de test isolée, sans afficher mot de passe ou JWT.

Aucun contrat d'import CSV n'est retenu. La préparation de cette étude ne modifie pas les permissions métier ni les points ouverts P01–P11.

## 7. Cohérence et critères de validation

| Référence | Respect par ce dossier |
|---|---|
| DEC-COMPTES-001 / A3 | Inscription publique CLIENT OCCASIONNEL, mot de passe choisi et preuve d'e-mail pour les nouveaux inscrits |
| DEC-COMPTES-002 / A7 | Registre Markdown privé, valeurs fictives isolées, BCrypt en base ; invitations des nouvelles créations administratives normales conservées |
| A1 / lot 1 | Générateur V/C/O existant à réutiliser ; aucune allocation dans ce document, non-réutilisation conservée |
| A2 historique remplacé par DEC-COMPTES-003 / lot 2 | Login CLIENT égal au matricule généré ; login ADMIN e-mail ; identités techniques conservées, pas de changement silencieux |
| A4 | Aucune condition commerciale VIP ou permission de promotion inventée |
| A5 | Premier GLOBAL réel et droits administratifs inchangés ; protection du dernier GLOBAL par transactions indépendantes et verrou commun PostgreSQL |
| A6 | LOCAL dans sa zone ; VIP/OCCASIONNEL sans zone sous gestion GLOBAL |
| A8 | Deux statuts distincts ; état du registre sans nouvel état métier |
| A9 | Login/me, session et destinations conservés ; aucune exposition du registre dans Angular |

Contrôles documentaires : cinq profils, aucun contenu du registre privé publié, CSV non requis et non recréé, exclusions Markdown/CSV conservées et consolidées, liens internes valides, Java/Angular/V1–V4 inchangés. Ces contrôles ne démontrent pas une connexion effective des comptes.

Tests futurs : AC32–AC38, combinaisons de profils de production, isolation, BCrypt, limites, collisions, transaction, rollback, relance et cohérence fiche/base après succès ou panne de fichier. Les preuves de non-publication doivent couvrir API, Swagger, Angular et artefacts distribués.

Contribution documentaire AA3–AA7, AA10 et AA11 ; aucun nouveau résultat de test applicatif annoncé.

## 8. Adaptation de l'exemple DataInitializer de PadellAout

Référence complémentaire : extrait Java fourni par le responsable du projet. Il sert à comprendre l'organisation d'un jeu de démonstration ; aucune classe PadellAout n'est copiée ni exécutée.

| Élément de l'exemple | Adaptation prévue pour DriveConnect |
|---|---|
| Repositories injectés et PasswordEncoder | Réutiliser CompteRepository, ClientRepository, ZoneRepository et le PasswordEncoder existant |
| Administrateur et Membre dans deux modèles | Utiliser Compte pour tous les accès ; associer un Client seulement au rôle CLIENT |
| Sites et catégories propres au padel | Utiliser les Zones et catégories VIP/LOCAL/OCCASIONNEL déjà définies ; aucune transposition des règles du padel |
| CommandLineRunner et Component lançant le remplissage au démarrage | Prévoir un point d'entrée manuel séparé ; aucun amorçage de données lors du démarrage normal de CovoiturageApplication |
| Mot de passe partagé par une catégorie d'utilisateurs | Suivre seulement en base fictive isolée DEC-COMPTES-002/003 : convention CLIENT et exception ADMIN privée ; BCrypt conservé |
| migrateLegacyMemberCredentials et ensureDemoMembersCredentials modifiant des comptes existants | Ne pas reprendre ces traitements : aucun rattrapage de mot de passe réel, aucune réinitialisation à chaque lancement |
| Test global siteRepository.count() pour décider de l'état du jeu | Vérifier explicitement la cible et chaque collision ; la présence d'une zone ne prouve ni l'isolation ni la complétude du jeu |
| Appels save successifs sans transaction englobante explicite | Encadrer la création du jeu et des paires Client/Compte par une transaction de service ; déterminer l'atomicité du lot avant implémentation |
| Relance complétant ou réécrivant des données | Pour un premier outil, refuser une collision sans écraser ; une éventuelle reprise doit s'appuyer sur une preuve de provisionnement définie, pas sur le seul nom de login |
| Matchs, réservations, paiements et pénalités du padel | Hors adaptation actuelle : limiter le jeu aux cinq profils et aux rattachements de zone nécessaires |

### Proposition concrète avant implémentation

Le dispositif proposé comporte un lanceur manuel PowerShell, un point d'entrée Java explicitement invoqué pour la démonstration et une opération transactionnelle utilisant les entités et repositories existants. Leur emplacement et leur exclusion de la livraison de production sont à fixer avant codage ; ce ne sont ni de nouveaux endpoints ni un second système d'authentification.

Ordre de traitement proposé :

1. Vérifier l'environnement effectif et la preuve de base fictive isolée avant toute écriture ; refuser prod, y compris combiné à dev.
2. Valider les cinq profils du registre, résoudre leurs zones et refuser toutes les collisions sans modifier de comptes existants.
3. Réutiliser le générateur V/C/O existant et fixer Compte.identifiant au numéro CLIENT obtenu ; conserver le login ADMIN e-mail. Hacher les valeurs fictives applicables avec le PasswordEncoder existant, sans modifier V1–V4.
4. Créer les deux Compte ADMIN et les trois paires Client/Compte dans la transaction retenue. Aucun compte réel n'est concerné.
5. Mettre à jour le registre privé seulement après succès en base ; contrôler ensuite login/me dans l'environnement isolé sans afficher de mot de passe ou JWT.
6. Exécuter les scénarios AC32–AC38 et les tests de rollback, de collision et de relance sur une base de test éphémère.

Les invitations des parcours administratifs normaux et le mot de passe choisi lors de l'inscription publique restent inchangés. Les principes de cette adaptation sont documentés ; aucun lanceur, service, initialiseur, compte ou changement de base n'est créé par cette section.
