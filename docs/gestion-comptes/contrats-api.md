# Contrats REST — gestion centralisée des comptes

[Décisions](decisions.md) · [Permissions](matrice-permissions.md) · [Acceptation](tests-acceptation.md).
Issue [#7](https://github.com/Zoom225/Gestion-covoiturage/issues/7), base 8a2a474, 21 octobre 2026.

**Seuls POST /api/auth/login et GET /api/auth/me sont existants. Toutes les autres opérations de ce document sont proposées, non implémentées.** Les DTO nouveaux sont conceptuels : aucun fichier Java ou Angular créé.

## 1. Responsabilités et conventions

Controller : HTTP, Bean Validation, DTO, délégation. Service : droits, périmètre, transactions, cycle de vie et audit. Repository : requêtes et verrouillage PostgreSQL. Mapper explicite des seuls champs autorisés ; aucune désérialisation d'entité JPA.

Réutiliser AuthController, AuthService, TokenService, PasswordEncoder, ApiErrorResponse et la politique administrative. Prévoir un service central de gestion des comptes si nécessaire, pas un service de connexion par profil.

Les créations spécialisées Client et Administrateur évitent un POST générique /api/admin/comptes concurrent : aucun endpoint générique supplémentaire proposé. Les listes et détails sont centralisés, les créations portent leurs invariants spécifiques.

| Méthode / URI | État | Accès cible | Succès |
|---|---|---|---|
| POST /api/auth/register | PROPOSÉ | Visiteur, exception publique limitée | 201 proposé, compte en attente |
| POST /api/auth/login | EXISTANT | Public, identifiants requis | 200, TokenResponse |
| GET /api/auth/me | EXISTANT | JWT et compte/client actifs | 200, ProfileResponse |
| GET /api/admin/comptes | PROPOSÉ | ADMIN, périmètre et types de comptes autorisés | 200, page de DTO |
| GET /api/admin/comptes/{id} | PROPOSÉ | ADMIN, contrôle de la ressource | 200, DTO |
| POST /api/admin/clients | PROPOSÉ | ADMIN habilité selon catégorie et périmètre | 201, invitation en attente |
| POST /api/admin/administrateurs | PROPOSÉ | ADMIN GLOBAL seulement | 201, invitation en attente |
| PATCH /api/admin/comptes/{id}/statut | PROPOSÉ | ADMIN autorisé, droits fins P09 | 200, DTO actualisé |

Les succès 201 et les enveloppes ci-dessous sont des propositions de contrat à valider. Ne pas activer les endpoints dont les préconditions P01–P11 ne sont pas précisées. Aucun privilège ne se déduit du seul fait que le chemin commence par /api/admin.

Périmètre d'activation : vérification d'e-mail avant activation pour les nouveaux inscrits publics OCCASIONNEL ; invitation sécurisée avant accès pour les nouveaux comptes créés administrativement par le parcours normal. Une éventuelle vérification d'e-mail distincte de l'invitation reste P06/P11. Les comptes préexistants conservent les contrôles actuels de login/me ; aucune obligation rétroactive d'e-mail ou d'invitation n'est ajoutée sans décision et migration adaptées. Le futur jeu fictif isolé reste l'exception DEC-COMPTES-002, sans nouvel endpoint.

## 2. Inscription publique — PROPOSÉE

### POST /api/auth/register

DTO conceptuel RegisterRequest :

| Champ | Obligatoire | Validation / responsabilité |
|---|---|---|
| identifiant | Oui | Non blanc, au plus 100 caractères comme l'existant ; normalisation et unicité A2 après P02 |
| email | Oui | Contact valide à vérifier ; longueur maximale et unicité à préciser P03 ; ne vaut pas automatiquement identifiant de connexion |
| password | Oui | Non blanc ; limite BCrypt de 72 octets UTF-8, sans troncature ; politique minimale P08 |

Aucun champ de rôle, catégorie, numéro, actif, clientId, périmètre ou zone administrative. Le choix manuel d'une zone client est P03 ; le DTO proposé exclut zoneId et zoneClientId tant que non validé. Le serveur ne déduit pas une zone du navigateur.

**Politique de validation proposée :** rejeter avec 400 les champs inconnus ou réservés au serveur, notamment role, categorie, numero, actif, passwordHash, perimetreAdmin, zoneAdminId et zoneClientId. Cette politique stricte concerne la future inscription ; le login existant ignore aujourd'hui des champs supplémentaires sans leur donner de privilèges. Ne pas annoncer le rejet strict comme un comportement déjà livré.

Traitement attendu :

1. Valider le DTO et les protections anti-abus ; fixer CLIENT et OCCASIONNEL côté service.
2. Vérifier l'identifiant normalisé et conserver la protection UNIQUE finale contre la concurrence.
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

Comportement constaté : recherche exacte de l'identifiant, comparaison BCrypt, erreur générique pour inconnu/mot de passe incorrect/compte ou client inactif. A2 prévoit l'insensibilité à la casse après migration cohérente ; **elle n'est pas livrée dans cette issue**.

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

200 et Cache-Control no-store. 401 si JWT absent/invalide/expiré ou compte/client indisponible. Le profil actuel n'expose pas les deux statuts, puisqu'il est réservé aux comptes autorisés ; ne pas le confondre avec le DTO administratif futur. Pas de duplication par /api/clients/me.

Frontend cible : formulaire commun → login → jeton en mémoire → me → validation de la combinaison du profil → destination A9. Aucune redirection déterminée seulement par un claim décodé ou le stockage navigateur.

## 5. Consultation administrative — PROPOSÉE

### GET /api/admin/comptes

Paramètres conceptuels :

| Paramètre | Proposition |
|---|---|
| page, size | Pagination indexée à partir de zéro ; valeurs positives/cohérentes, taille maximale à préciser avant implémentation |
| identifiant | Recherche sans distinction de casse selon A2 ; mode exact/préfixe/partiel P02 |
| role | ADMIN ou CLIENT, enum validé |
| categorie | VIP, LOCAL, OCCASIONNEL ; champ CLIENT |
| zoneId | Filtre sur la zone pertinente selon le type de compte, sans élargir le périmètre |
| compteActif, clientActif | Booléens distincts ; clientActif inapplicable aux comptes ADMIN |
| sort | Liste blanche de champs, tri stable incluant id ; champs autorisés à fixer |

Requête serveur : périmètre issu de PostgreSQL ET filtres demandés, **avant** limit/offset et count. Aucun chargement paginé réseau puis suppression en mémoire. LOCAL ne voit pas les clients sans zone ni les comptes administrateurs dont la visibilité reste P07. GLOBAL peut consulter le réseau dans les limites des opérations définies.

PageResponse conceptuel stable : content, page, size, totalElements, totalPages. Tous les totaux sont calculés dans le même périmètre. Filtres impossibles/incohérents : validation 400 proposée ; ne jamais ignorer un filtre d'une manière qui élargit les permissions.

CompteAdministrationResponse conceptuel commun liste/détail :

- compteId, identifiant, role.
- clientId, numeroClient, categorie, zoneClientId si CLIENT.
- perimetreAdmin, zoneAdminId si ADMIN.
- compteActif ; clientActif nullable pour ADMIN ; accesEffectif calculé sur les deux états applicables.
- actionsAutorisees envisageable pour l'interface, uniquement calculé côté backend ; sa forme reste à stabiliser. Son absence ne justifie aucun contrôle fondé seulement sur Angular.

Pas de password, passwordHash, secret, jeton de preuve, entité JPA ou stack trace. Pas d'e-mail dans la liste par défaut ; son éventuelle consultation détaillée demande une justification métier et un contrat explicite.

### GET /api/admin/comptes/{id}

Même DTO et mêmes règles de rôle/type/périmètre que la liste. id positif valide ; 400 si entrée invalide, 401 sans authentification, 403 si rôle ou périmètre insuffisant, 404 si compte inexistant dans le périmètre autorisé. Le message 403 ne divulgue pas les attributs d'un compte hors zone. Une éventuelle politique d'occultation par 404 doit être décidée explicitement avant implémentation.

## 6. Création administrative — PROPOSÉE

### POST /api/admin/clients

CreateClientRequest conceptuel : identifiant, email de contact pour invitation, categorie, zoneClientId conditionnel. L'obligation de contact pour l'invitation est une proposition technique à stabiliser avec P11 ; elle ne transforme pas l'e-mail en login.

Le rôle CLIENT, le numéro et les états de sécurité sont imposés côté serveur ; aucun password administrativement choisi. LOCAL exige une zone existante. VIP uniquement par GLOBAL. L'attribution LOCAL est autorisée à GLOBAL ou LOCAL habilité dans sa zone selon P05 ; le droit précis de création d'un nouveau client par LOCAL reste P07 et ne se déduit pas automatiquement du droit d'attribution. Un LOCAL ne peut créer un client sans zone ; son droit sur un OCCASIONNEL zoné reste également P07.

Client/Compte/invitation et audit dans une transaction. Le compte reste sans accès jusqu'au parcours d'invitation et aux validations requises ; modélisation du hash obligatoire actuel à préciser P06, sans valeur par défaut.

201 proposé : DTO administratif minimal et état invitation en attente, éventuellement Location vers le détail du compte ; ni jeton d'invitation ni mot de passe. 400 données/profil/zone manquante ; 401 JWT ; 403 rôle/catégorie/périmètre ; 404 zone autorisée inexistante ; 409 unicité ou état concurrent. Anti-énumération hors zone appliquée avant divulgation de la cible.

Cette création ne définit pas implicitement les transitions ultérieures de catégorie, qui requièrent P05 et une opération spécifique à concevoir.

### POST /api/admin/administrateurs

CreateAdministrateurRequest conceptuel : identifiant, contact d'invitation, perimetreAdmin GLOBAL/LOCAL, zoneAdminId requise pour LOCAL et absente pour GLOBAL. role ADMIN fixé par le serveur ; aucun clientId, categorie ou mot de passe fourni par le créateur.

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

Réutiliser ApiErrorResponse : timestamp, status, code, message, path, validationErrors (field/message). Les codes existants sont VALIDATION_ERROR, INVALID_REQUEST, UNAUTHORIZED, FORBIDDEN. Les codes symboliques de 404/409 restent à stabiliser et leurs handlers métier à développer ; ils ne sont pas livrés actuellement.

| HTTP | Sens cible |
|---|---|
| 400 | JSON, champ, enum, filtre ou identifiant de ressource invalide |
| 401 | Authentification absente/invalide/expirée ; login incorrect ou compte indisponible |
| 403 | Principal authentifié sans rôle, habilitation ou périmètre requis |
| 404 | Ressource autorisée introuvable |
| 409 | Unicité, invariant métier ou conflit concurrent |

Ne pas renvoyer la valeur rejetée d'un mot de passe, une requête SQL, un nom technique de contrainte ou une stack trace. Réponses d'erreur de connexion génériques. Conserver les réponses sensibles sans mise en cache ; politique no-store recommandée également pour les DTO administratifs.

Future sécurité : contrôles de rôle et de périmètre dans les services, plus restrictions HTTP ; seul POST register explicitement public lorsque livré, pas /api/auth/** entièrement public. Les parcours de preuve/invitation devront avoir leur propre preuve à usage unique et une limitation des abus. Pas d'accès à une API existante accordé par une catégorie métier.

Références : DEC/A1–A9 ; RM01–RM04/RM07/RM09–RM13/RM49/RM51/RM57–RM60 ; AA5–AA9. Les API seules ne démontrent pas les acquis : exécuter ultérieurement les [scénarios](tests-acceptation.md).
