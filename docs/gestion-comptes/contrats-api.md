# Contrats REST — gestion centralisée des comptes

**Préparation du parcours complet — issue [#23](https://github.com/Zoom225/Gestion-covoiturage/issues/23) :** socle interne d'initialisation transactionnelle et outbox chiffrée en préparation. Les nouveaux endpoints, créations administratives et bootstrap ne sont pas livrés : droits LOCAL (P05/P07), activités initiales (P09) et politique minimale du mot de passe (P08) attendent un arbitrage. Voir [état réel, sécurité et limites](parcours-invitations-preparation.md).


**Actualisation — lot 3C.2 / issue [#21](https://github.com/Zoom225/Gestion-covoiturage/issues/21) :** DEC-COMPTES-005 livre le service interne de preuves d'invitation (32 octets, SHA-256, durée configurable, révocation/renouvellement et consommation atomique). Aucun endpoint, e-mail, mot de passe ou compte créé. Voir [modèle, concurrence, tests et limites AA](lot-3c2-invitations.md).


**Actualisation — lot 3C.1 / issue [#19](https://github.com/Zoom225/Gestion-covoiturage/issues/19) :** DEC-COMPTES-004 ajoute INVITE/INITIALISE. Compte invité sans hash, accès refusé y compris par JWT ; INITIALISE et activités sont indépendants. Les comptes préexistants restent INITIALISES. Seul le socle est livré ; jetons, e-mails et création administrative restent futurs. Voir [modèle, migration V5 et preuves](lot-3c1-comptes-invites.md).


**État courant — lot 3B / issue [#15](https://github.com/Zoom225/Gestion-covoiturage/issues/15) :** consultation administrative implémentée selon les règles validées le 24 octobre 2026. GLOBAL consulte le réseau ; LOCAL seulement les comptes CLIENT de sa zone, sans visibilité des comptes ADMIN. Pagination 0/20/100, ordre compteId croissant, recherche exacte sans casse, filtres par intersection et même 404 pour absent/invisible. Les propositions du lot 3A ci-dessous sont historiques lorsqu'elles sont remplacées par cet état. Voir [architecture, contrat et preuves TFE](lot-3b-consultation.md).

**État courant — lot 2 / #11 :** POST login accepte l'e-mail ADMIN ou le matricule CLIENT sans distinction de casse ; GET me conserve les droits issus de PostgreSQL. V4 impose stockage canonique et cohérence Client/Compte sans réécrire les anciens comptes. Les formulations antérieures décrivent l'historique. Voir [contrat livré et tests AC-AUTH](lot-2-authentification.md).


**Actualisation lot 1 / DEC-COMPTES-003 :** la cible est e-mail ADMIN et matricule CLIENT ; les descriptions antérieures d'un login client indépendant sont historiques. Ce lot livre uniquement les séquences et l'historique, sans changer login/me ou les permissions. Les tests automatisés AC-NUM01–AC-NUM12 sont décrits dans le [dossier du lot](lot-1-numerotation.md) ; ils sont distincts des scénarios documentaires ci-dessous.


[Décisions](decisions.md) · [Permissions](matrice-permissions.md) · [Acceptation](tests-acceptation.md).
Issue [#7](https://github.com/Zoom225/Gestion-covoiturage/issues/7), base 8a2a474, 21 octobre 2026.

**Existants : POST /api/auth/login, GET /api/auth/me et, depuis le lot 3B, GET /api/admin/comptes et GET /api/admin/comptes/{id}.** Inscription, créations administratives et statut restent proposés, non implémentés. Les DTO de ces parcours futurs restent conceptuels ; aucun Angular livré.

**Actualisation documentaire — lot 3A / issue [#13](https://github.com/Zoom225/Gestion-covoiturage/issues/13), 24 octobre 2026 :** base origin/master 39a7b6b (PR #10 et #12 fusionnées). Aucun endpoint administratif livré par ce lot. Le matricule CLIENT est le login généré ; l'e-mail de connexion ADMIN est distinct du contact CLIENT. Les droits de lecture confirmés et les modalités encore ouvertes sont séparés dans les [contrats de consultation](contrats-api.md#5-consultation-administrative--implémentée-lot-3b).

## 1. Responsabilités et conventions

Controller : HTTP, Bean Validation, DTO, délégation. Service : droits, périmètre, transactions, cycle de vie et audit. Repository : requêtes et verrouillage PostgreSQL. Mapper explicite des seuls champs autorisés ; aucune désérialisation d'entité JPA.

Réutiliser AuthController, AuthService, TokenService, PasswordEncoder, ApiErrorResponse et la politique administrative. Prévoir un service central de gestion des comptes si nécessaire, pas un service de connexion par profil.

Les créations spécialisées Client et Administrateur évitent un POST générique /api/admin/comptes concurrent : aucun endpoint générique supplémentaire proposé. Les listes et détails sont centralisés, les créations portent leurs invariants spécifiques.

| Méthode / URI | État | Accès cible | Succès |
|---|---|---|---|
| POST /api/auth/register | PROPOSÉ | Visiteur, exception publique limitée | 201 proposé, compte en attente |
| POST /api/auth/login | EXISTANT | Public, identifiants requis | 200, TokenResponse |
| GET /api/auth/me | EXISTANT | JWT, état INITIALISE et compte/client actifs | 200, ProfileResponse |
| GET /api/admin/comptes | EXISTANT lot 3B | GLOBAL réseau ; LOCAL seulement CLIENT de sa zone | 200, ComptePageResponse |
| GET /api/admin/comptes/{id} | EXISTANT lot 3B | Même périmètre que la liste ; invisible = absent | 200, CompteAdministrationResponse |
| POST /api/admin/clients | PROPOSÉ | ADMIN habilité selon catégorie et périmètre | 201, invitation en attente |
| POST /api/admin/administrateurs | PROPOSÉ | ADMIN GLOBAL seulement | 201, invitation en attente |
| PATCH /api/admin/comptes/{id}/statut | PROPOSÉ | ADMIN autorisé, droits fins P09 | 200, DTO actualisé |

Les succès 201 et les enveloppes ci-dessous sont des propositions de contrat à valider. Ne pas activer un endpoint tant que ses préconditions applicables P01–P12 ne sont pas précisées ; la lecture seule ne dépend pas des parcours de création/invitation hors périmètre. Aucun privilège ne se déduit du seul fait que le chemin commence par /api/admin.

Périmètre d'activation : vérification d'e-mail avant activation pour les nouveaux inscrits publics OCCASIONNEL ; invitation sécurisée avant accès pour les nouveaux comptes créés administrativement par le parcours normal. Une éventuelle vérification d'e-mail distincte de l'invitation reste P06/P11. Les comptes préexistants conservent les contrôles actuels de login/me ; aucune obligation rétroactive d'e-mail ou d'invitation n'est ajoutée sans décision et migration adaptées. Le futur jeu fictif isolé reste l'exception DEC-COMPTES-002, sans nouvel endpoint.

## 2. Inscription publique — PROPOSÉE

### POST /api/auth/register

DTO conceptuel RegisterRequest :

| Champ | Obligatoire | Validation / responsabilité |
|---|---|---|
| email | Oui | Contact valide à vérifier ; longueur maximale et unicité à préciser P03 ; ne vaut pas automatiquement identifiant de connexion |
| password | Oui | Non blanc ; limite BCrypt de 72 octets UTF-8, sans troncature ; politique minimale P08 |

Aucun champ de rôle, catégorie, numéro, actif, clientId, périmètre ou zone administrative. Le choix manuel d'une zone client est P03 ; le DTO proposé exclut zoneId et zoneClientId tant que non validé. Le serveur ne déduit pas une zone du navigateur.

**Politique de validation proposée :** rejeter avec 400 les champs inconnus ou réservés au serveur, notamment role, categorie, numero, actif, passwordHash, perimetreAdmin, zoneAdminId et zoneClientId. Cette politique stricte concerne la future inscription ; le login existant ignore aujourd'hui des champs supplémentaires sans leur donner de privilèges. Ne pas annoncer le rejet strict comme un comportement déjà livré.

Traitement attendu :

1. Valider le DTO et les protections anti-abus ; fixer CLIENT et OCCASIONNEL côté service.
2. Le visiteur ne fournit pas de login libre ; le matricule généré sera le login CLIENT. Conserver les protections UNIQUE finales ; unicité éventuelle du contact P03.
3. Générer un numéro O par séquence, hacher le mot de passe avec l'encodeur existant.
4. Dans une transaction, créer Client, Compte sans accès et preuve de vérification ; toutes les écritures associées réussissent ou sont annulées.
5. Préparer la livraison d'e-mail selon P11 ; aucun token de vérification retourné au navigateur.
6. Ne délivrer ni JWT ni connexion automatique avant validation de l'e-mail. Activation conditionnelle sans réactivation implicite du statut métier.

RegistrationResponse conceptuel minimal : compteId, identifiant, numeroClient, role CLIENT, categorie OCCASIONNEL, etatInscription EN_ATTENTE_VERIFICATION et message contrôlé. Cet état est un état de parcours proposé, pas un nouvel enum existant ni une permission métier.

Erreurs proposées : 400 champs/JSON invalides ; 409 identifiant en conflit ; erreurs anti-abus, notamment 429 si limitation retenue, à préciser avec son mécanisme. Aucun hash, mot de passe, jeton de preuve ou erreur SQL dans la réponse. La divulgation d'existence via 409 doit être prise en compte dans la stratégie anti-abus.

Dépendances bloquantes : P01/P02/P03/P04/P08/P09/P11. Les routes de validation d'e-mail et de renvoi restent à spécifier, sans créer ici d'API parallèle ou d'activation par simple modification de actif.

## 3. Connexion — EXISTANTE

### POST /api/auth/login

LoginRequest existant : identifiant non blanc, maximum 100 caractères ; password non blanc, maximum 72 caractères en validation DTO, complété par un contrôle de 72 octets UTF-8 dans AuthService.

Comportement livré au lot 2 : recherche PostgreSQL sans distinction de casse, e-mail pour ADMIN et matricule pour CLIENT ; comparaison BCrypt et erreur générique pour inconnu/mot de passe incorrect/compte ou client inactif. V4 garantit unicité normalisée, stockage canonique et correspondance Client/Compte ; aucun ancien alias accepté implicitement.

Réponse existante : 200 avec accessToken, tokenType Bearer, expiresIn en secondes et Cache-Control no-store. JWT HS256 existant : sub = id Compte, émetteur/audience/dates/jti, sans rôle/catégorie/zone faisant autorité. La configuration actuelle a une durée par défaut de 15 minutes ; la durée fonctionnelle de session reste P08, sans modification ici.

400 pour JSON ou validation invalides ; 401 pour identifiants incorrects ou compte indisponible. Au-delà de 72 octets avec un nombre de caractères admis par le DTO, AuthService retourne actuellement 401 ; ne pas annoncer uniformément 400 pour ce cas de login. Le futur formulaire ne doit pas tronquer le mot de passe.

Aucun remplacement de JWT, BCrypt ou AuthService. Les cinq profils utilisent cette même route.

## 4. Profil — EXISTANT

### GET /api/auth/me

Aucun id de compte/client fourni par Angular n'est source d'identité. Le JWT validé identifie Compte ; PostgreSQL fournit le profil à chaque requête.

ProfileResponse existant :

| Champ | Sens |
|---|---|
| compteId, identifiant | Compte authentifié |
| role | ADMIN ou CLIENT |
| clientId, categorie, zoneClientId | Champs client, nuls pour ADMIN ; zone facultative sauf LOCAL |
| perimetreAdmin, zoneAdminId | GLOBAL sans zone ou LOCAL avec zone ; nuls pour CLIENT |

200 et Cache-Control no-store. 401 si JWT absent/invalide/expiré, compte INVITE ou compte/client indisponible. Le profil actuel n'expose pas les deux statuts, puisqu'il est réservé aux comptes autorisés ; ne pas le confondre avec le DTO administratif futur. Pas de duplication par /api/clients/me.

Frontend cible : formulaire commun → login → jeton en mémoire → me → validation de la combinaison du profil → destination A9. Aucune redirection déterminée seulement par un claim décodé ou le stockage navigateur.

## 5. Consultation administrative — IMPLÉMENTÉE lot 3B

Règles validées par le responsable pour l'issue [#15](https://github.com/Zoom225/Gestion-covoiturage/issues/15). Elles remplacent les propositions ouvertes du lot 3A pour la lecture seule. Les autres parcours restent hors périmètre.

### 5.1 GET /api/admin/comptes

| Paramètre | Contrat livré |
|---|---|
| page | Entier, index à partir de 0 ; défaut 0 ; valeur négative ou non entière : 400 |
| size | Entier de 1 à 100 ; défaut 20 ; hors bornes ou non entier : 400 |
| identifiant | Facultatif, 1 à 100 caractères si présent ; égalité exacte avec lower PostgreSQL des deux côtés ; aucun préfixe, joker, recherche partielle ni trim implicite |
| role | Facultatif, enum ADMIN/CLIENT |
| categorie | Facultatif, enum VIP/LOCAL/OCCASIONNEL ; s'applique aux seuls comptes liés à un CLIENT |
| zoneId | Facultatif, id positif ; zone du CLIENT pour rôle CLIENT, zone administrative pour rôle ADMIN |
| compteActif | Facultatif, booléen, filtre du statut technique |
| clientActif | Facultatif, booléen, filtre du statut métier ; un ADMIN n'a pas ce statut et ne satisfait pas ce filtre |

Ordre livré : compteId croissant (id technique unique), stable et indépendant du matricule. Aucun tri personnalisable ni paramètre sort n'est défini dans ce lot. Page/size sont validés explicitement ; le décalage doit aussi rester représentable par JPA (au plus Integer.MAX_VALUE), sinon 400 contrôlé.

**Les filtres sont combinés par intersection avec le périmètre de sécurité.** Un filtre syntaxiquement valide mais incompatible ne crée pas de permission et produit un résultat vide : role=ADMIN pour LOCAL, role=ADMIN avec categorie ou clientActif, zone d'une autre zone pour LOCAL. Les enums, identifiants de ressource ou types mal formés donnent 400. Une zone inexistante demandée comme filtre produit une page vide, sans recherche d'existence globale ni 404 de liste.

| Acteur relu depuis PostgreSQL | Prédicat obligatoire avant pagination et count |
|---|---|
| ADMIN GLOBAL | Tous les comptes du réseau, y compris ceux sans zone |
| ADMIN LOCAL | Compte.role = CLIENT ET Client.zone.id = zoneAdminId persisté de l'acteur ; zone client non nulle |
| CLIENT authentifié | 403 avant lecture administrative |
| Acteur non authentifié, JWT invalide/expiré ou compte/client appelant indisponible | 401 via la sécurité existante |

L'accès du GLOBAL reste une permission de lecture ; il ne valide aucune création ou mutation future. LOCAL ne lit aucun compte ADMIN, même lui-même ou un administrateur de la même zone ; GET me demeure son accès au profil personnel. Les clients VIP/OCCASIONNEL sans zone sont visibles au GLOBAL uniquement. Les comptes cibles inactifs restent consultables dans le périmètre, sauf filtre contraire ; les contrôles d'activité portent sur l'acteur.

ComptePageResponse livré : content, page, size, totalElements, totalPages. La page et le total utilisent la même Specification (périmètre ET filtres) dans PostgreSQL. Pas de page réseau filtrée en mémoire ni de compteur global pour LOCAL. Une page au-delà du résultat est vide avec les totaux filtrés conservés ; une recherche sans résultat donne 200, content vide et total nul. Le contrat partage le prédicat, sans promettre un instantané strict entre deux requêtes SQL en présence d'écritures concurrentes.

### 5.2 GET /api/admin/comptes/{id}

id est Compte.id, entier positif sur 64 bits ; aucune recherche par alias ou matricule à cet emplacement. Le service réutilise le périmètre de la liste puis ajoute l'égalité de l'id.

| Situation | Réponse livrée |
|---|---|
| Détail autorisé trouvé | 200, CompteAdministrationResponse |
| Compte absent ou invisible (hors zone, sans zone pour LOCAL, tout ADMIN pour LOCAL) | Même 404, code NOT_FOUND, message Ressource introuvable |
| Acteur authentifié sans permission administrative | 403, indépendamment de l'existence de la cible |
| Authentification indisponible | 401 |
| id mal formé, nul ou négatif | 400 pour l'acteur autorisé à cette API |

Aucune requête d'existence globale de secours. Format ApiErrorResponse commun ; seuls timestamp et path reflètent le moment et le chemin demandé, pas les attributs de la cible. Aucun indice de compte, rôle ou zone invisible dans le message. Les anciennes propositions de 403 pour un compte hors zone ne s'appliquent plus à ces GET.

### 5.3 DTO commun liste et détail

[CompteAdministrationResponse](../../src/main/java/com/plateforme/covoiturage/dto/response/CompteAdministrationResponse.java) est un DTO explicite, distinct des entités :

| Champs | Contrat |
|---|---|
| compteId, identifiant, role | Identité technique et login. CLIENT : identifiant = numeroClient généré. ADMIN : identifiant est l'e-mail de connexion, visible au GLOBAL pour les comptes ADMIN consultables |
| clientId, numeroClient, categorie, zoneClientId | Champs CLIENT ; nuls pour ADMIN. Zone facultative pour VIP/OCCASIONNEL |
| perimetreAdmin, zoneAdminId | Champs ADMIN ; nuls pour CLIENT. GLOBAL sans zone administrative |
| compteActif, clientActif | Activités distinctes et indépendantes ; clientActif null pour ADMIN |
| etatIdentifiants | INVITE ou INITIALISE, état non sensible ajouté au lot 3C.1 |
| accesEffectif | Prérequis d'accès dérivés selon A8/DEC-COMPTES-004 |

accesEffectif = INITIALISE ET Compte.actif pour ADMIN, INITIALISE ET Compte.actif ET Client.actif pour CLIENT. INVITE est toujours sans accès, même avec un JWT signé. Le calcul est partagé avec AuthService dans Compte.isAccesEffectif ; il ne prétend pas valider un JWT particulier ni accorder une permission de mutation.

**L'e-mail de connexion ADMIN est un identifiant de compte ; le contact CLIENT n'est pas son login et n'est pas exposé.** Aucun champ password, passwordHash, secret, jeton de preuve, contact CLIENT, entité JPA, stack trace ou actions de mutation n'est ajouté. Le registre privé ne participe à aucune réponse. Liste et détail portent Cache-Control: no-store.

### 5.4 Architecture réutilisée

[CompteController](../../src/main/java/com/plateforme/covoiturage/controller/CompteController.java) traite HTTP, validation et DTO ; [CompteService](../../src/main/java/com/plateforme/covoiturage/service/CompteService.java) centralise lecture et application du périmètre ; [AdminAuthorization](../../src/main/java/com/plateforme/covoiturage/security/AdminAuthorization.java) détermine le périmètre administratif depuis le principal relu en base.

[CompteRepository](../../src/main/java/com/plateforme/covoiturage/repository/CompteRepository.java) est étendu avec JpaSpecificationExecutor et les méthodes existantes de lecture ; EntityGraph charge client et zones dans la sélection pour éviter les requêtes N+1. La même politique de périmètre sert à la liste, au total et au détail. Le contrat historique canManageZone reste inchangé pour les autres opérations ; la consultation réseau traite les sans-zone via la nouvelle méthode de la même politique.

[CompteMapper](../../src/main/java/com/plateforme/covoiturage/mapper/CompteMapper.java) convertit uniquement les champs autorisés. GlobalExceptionHandler et ApiErrorResponse traitent validation 400, sécurité 401/403 et ResourceNotFoundException 404, sans nouveau format d'erreur. Aucun second service d'authentification, générateur, repository de comptes ou politique de permissions.

Transactions de lecture seulement. Aucune migration nouvelle ni modification de V1–V4. Les filtres utilisent les colonnes existantes ; l'analyse d'éventuels index supplémentaires reste distincte et exige un besoin mesuré. Les preuves et limites AA sont dans le [dossier du lot](lot-3b-consultation.md).

## 6. Création administrative — PROPOSÉE

### POST /api/admin/clients

CreateClientRequest conceptuel : email de contact pour invitation, categorie, zoneClientId conditionnel ; login CLIENT fixé au matricule généré, aucun identifiant libre. L'obligation de contact pour l'invitation est une proposition technique à stabiliser avec P11 ; elle ne transforme pas l'e-mail en login.

Le rôle CLIENT, le numéro et les états de sécurité sont imposés côté serveur ; aucun password administrativement choisi. LOCAL exige une zone existante. VIP uniquement par GLOBAL. L'attribution LOCAL est autorisée à GLOBAL ou LOCAL habilité dans sa zone selon P05 ; le droit précis de création d'un nouveau client par LOCAL reste P07 et ne se déduit pas automatiquement du droit d'attribution. Un LOCAL ne peut créer un client sans zone ; son droit sur un OCCASIONNEL zoné reste également P07.

Client/Compte/invitation et audit dans une transaction. Le compte reste sans accès jusqu'au parcours d'invitation et aux validations requises ; modélisation du hash obligatoire actuel à préciser P06, sans valeur par défaut.

201 proposé : DTO administratif minimal et état invitation en attente, éventuellement Location vers le détail du compte ; ni jeton d'invitation ni mot de passe. 400 données/profil/zone manquante ; 401 JWT ; 403 rôle/catégorie/périmètre ; 404 zone autorisée inexistante ; 409 unicité ou état concurrent. Anti-énumération hors zone appliquée avant divulgation de la cible.

Cette création ne définit pas implicitement les transitions ultérieures de catégorie, qui requièrent P05 et une opération spécifique à concevoir.

### POST /api/admin/administrateurs

CreateAdministrateurRequest conceptuel : e-mail de connexion canonique, contact d'invitation, perimetreAdmin GLOBAL/LOCAL, zoneAdminId requise pour LOCAL et absente pour GLOBAL. role ADMIN fixé par le serveur ; aucun clientId, categorie ou mot de passe fourni par le créateur.

Seul le principal ADMIN GLOBAL relu en base peut appeler ce parcours. Un ADMIN LOCAL reçoit 403, même pour créer un LOCAL, même si le JSON déclare GLOBAL. La route ne sert pas à amorcer le premier GLOBAL.

Création transactionnelle et invitation A7 ; 201 proposé, sans secret ; erreurs 400/401/403/404/409 comme ci-dessus. Procédure opérateur initiale, représentation du compte invité sans hash et éventuelle modification de périmètre restent P06.

## 7. Statut technique — PROPOSÉ

### PATCH /api/admin/comptes/{id}/statut

CompteStatutRequest conceptuel : actif booléen obligatoire uniquement. Un motif d'audit est recommandé ; obligation et transport à préciser avec P09. Aucun champ Client.actif, rôle, catégorie ou périmètre dans ce DTO.

Autorisation par acteur, type de compte et zone à finaliser P09 ; ne pas considérer tous les ADMIN comme autorisés. Changer Compte.actif laisse Client.actif inchangé ; recalculer accesEffectif.

Une demande de désactivation du dernier GLOBAL actif est refusée. Chaque requête possède sa transaction indépendante et acquiert un même verrou commun PostgreSQL avant de relire les GLOBAL actifs et de décider la désactivation ; ce verrou est conservé jusqu'au commit ou rollback. Verrouiller seulement deux lignes cibles distinctes ne suffit pas. Proposition de 409 pour cet invariant ou un conflit concurrent, sans état partiellement modifié ; mécanisme concret P06.

Pour un compte issu du nouveau parcours d'inscription publique, la réactivation respecte la preuve d'e-mail requise par A3 ; droits et préconditions restent P09. Cette exigence ne s'étend pas rétroactivement aux comptes préexistants sans décision et migration adaptées.

200 proposé : DTO actualisé ; 400 mauvais corps/id ; 401 JWT ; 403 droits/périmètre ; 404 compte absent ; 409 invariant ou conflit. Opérations Client.actif, changement de catégorie, zone, périmètre, invitation et réinitialisation : contrats ultérieurs, pas d'endpoints redondants ajoutés ici.

## 8. Erreurs et sécurité transversales

Réutiliser ApiErrorResponse : timestamp, status, code, message, path, validationErrors (field/message). Les codes existants sont VALIDATION_ERROR, INVALID_REQUEST, UNAUTHORIZED, FORBIDDEN et NOT_FOUND (404 de consultation lot 3B). Le traitement métier de 409 reste à développer dans un futur lot de mutation.

| HTTP | Sens cible |
|---|---|
| 400 | JSON, champ, enum, filtre ou identifiant de ressource invalide |
| 401 | Authentification absente/invalide/expirée ; login incorrect ou compte indisponible |
| 403 | Principal authentifié sans droit d'opération ; consultation administrative : CLIENT ou profil administratif sans périmètre autorisé |
| 404 | Consultation administrative : même réponse générique pour absent/invisible ; autres futurs parcours selon leur contrat |
| 409 | Unicité, invariant métier ou conflit concurrent |

Ne pas renvoyer la valeur rejetée d'un mot de passe, une requête SQL, un nom technique de contrainte ou une stack trace. Réponses d'erreur de connexion génériques. Conserver les réponses sensibles sans mise en cache ; politique no-store recommandée également pour les DTO administratifs.

Future sécurité : contrôles de rôle et de périmètre dans les services, plus restrictions HTTP ; seul POST register explicitement public lorsque livré, pas /api/auth/** entièrement public. Les parcours de preuve/invitation devront avoir leur propre preuve à usage unique et une limitation des abus. Pas d'accès à une API existante accordé par une catégorie métier.

Références : DEC/A1–A9 ; RM01–RM04/RM07/RM09–RM13/RM49/RM51/RM57–RM60 ; AA5–AA9. Les API seules ne démontrent pas les acquis : exécuter ultérieurement les [scénarios](tests-acceptation.md).
