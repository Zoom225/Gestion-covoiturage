# Contrats REST — gestion centralisée des comptes

**État courant — lot 2 / #11 :** POST login accepte l'e-mail ADMIN ou le matricule CLIENT sans distinction de casse ; GET me conserve les droits issus de PostgreSQL. V4 impose stockage canonique et cohérence Client/Compte sans réécrire les anciens comptes. Les formulations antérieures décrivent l'historique. Voir [contrat livré et tests AC-AUTH](lot-2-authentification.md).


**Actualisation lot 1 / DEC-COMPTES-003 :** la cible est e-mail ADMIN et matricule CLIENT ; les descriptions antérieures d'un login client indépendant sont historiques. Ce lot livre uniquement les séquences et l'historique, sans changer login/me ou les permissions. Les tests automatisés AC-NUM01–AC-NUM12 sont décrits dans le [dossier du lot](lot-1-numerotation.md) ; ils sont distincts des scénarios documentaires ci-dessous.


[Décisions](decisions.md) · [Permissions](matrice-permissions.md) · [Acceptation](tests-acceptation.md).
Issue [#7](https://github.com/Zoom225/Gestion-covoiturage/issues/7), base 8a2a474, 21 octobre 2026.

**Seuls POST /api/auth/login et GET /api/auth/me sont existants. Toutes les autres opérations de ce document sont proposées, non implémentées.** Les DTO nouveaux sont conceptuels : aucun fichier Java ou Angular créé.

**Actualisation documentaire — lot 3A / issue [#13](https://github.com/Zoom225/Gestion-covoiturage/issues/13), 24 octobre 2026 :** base origin/master 39a7b6b (PR #10 et #12 fusionnées). Aucun endpoint administratif livré par ce lot. Le matricule CLIENT est le login généré ; l'e-mail de connexion ADMIN est distinct du contact CLIENT. Les droits de lecture confirmés et les modalités encore ouvertes sont séparés dans les [contrats de consultation](contrats-api.md#5-consultation-administrative--proposée).

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

200 et Cache-Control no-store. 401 si JWT absent/invalide/expiré ou compte/client indisponible. Le profil actuel n'expose pas les deux statuts, puisqu'il est réservé aux comptes autorisés ; ne pas le confondre avec le DTO administratif futur. Pas de duplication par /api/clients/me.

Frontend cible : formulaire commun → login → jeton en mémoire → me → validation de la combinaison du profil → destination A9. Aucune redirection déterminée seulement par un claim décodé ou le stockage navigateur.

## 5. Consultation administrative — PROPOSÉE

**Lot 3A / #13 : contrat à valider avant développement.** Les deux GET suivants n'existent pas encore. Ce contrat de lecture n'active ni création, invitation, changement de catégorie/zone, ni modification de statut.

### 5.1 Droits et périmètre confirmés

- GLOBAL : consultation des comptes CLIENT et ADMIN sur le réseau, y compris les clients VIP/OCCASIONNEL sans zone.
- LOCAL : consultation des seuls comptes CLIENT rattachés à sa zone administrative lue en PostgreSQL, quelle que soit leur catégorie. Un client sans zone ou d'une autre zone est exclu.
- La visibilité des comptes ADMIN pour LOCAL reste P07, même s'ils ont la même zone. Aucun accès à ces lignes n'est accordé par la présente proposition. Le profil propre reste accessible via GET me.
- Visiteur : authentification requise. CLIENT authentifié : aucun accès administratif, quelle que soit sa catégorie. Le rôle ADMIN seul ne suffit pas à déterminer toutes les lignes visibles.
- Les statuts des comptes consultés sont des données de lecture A8, sans droit de mutation. L'acteur, lui, reste soumis aux contrôles d'activité existants. Les éventuels filtres de statut ne doivent pas être confondus avec ces contrôles.

### 5.2 GET /api/admin/comptes

Paramètres conceptuels réservés ; **la liste ci-dessous ne fixe aucune règle de recherche, de tri ou de taille de page** :

| Paramètre envisagé | Déjà fixé / choix restant ouvert |
|---|---|
| page, size | Pagination serveur ; index de départ, valeurs par défaut, bornes et comportement de dépassement À PRÉCISER P12. L'indexation à zéro de la version antérieure était une proposition, pas une règle validée |
| identifiant | Login ADMIN e-mail ou CLIENT matricule ; comparaison sans casse déjà livrée pour le login. Sa réutilisation pour une recherche administrative, le mode exact/préfixe/partiel et les jokers restent P02/P12 |
| role | ADMIN ou CLIENT selon enum ; ne donne aucun droit. Traitement d'un filtre ADMIN présenté par LOCAL à préciser ; aucune ligne ni compteur ADMIN rendu visible en attendant P07 |
| categorie | Enum CLIENT existant ; combinaisons avec role à préciser |
| zoneId | Toujours intersecté avec le périmètre serveur. Sens précis pour une ligne ADMIN (zone administrative) ou CLIENT (zone client), recherche sans zone et combinaisons à préciser |
| compteActif, clientActif | Booléens distincts ; traitement de clientActif avec ADMIN et combinaisons à préciser |
| sort | Champs autorisés, directions, ordre par défaut et départage stable À PRÉCISER P12 ; aucune règle implicite du framework ne vaut validation métier |

Périmètre de lecture à appliquer **dans PostgreSQL, avant limit/offset et count** :

| Acteur | Restriction obligatoire, indépendante des filtres demandés |
|---|---|
| GLOBAL | Ensemble des comptes autorisés à la consultation réseau |
| LOCAL | Compte.role = CLIENT ET Client.zone.id = zoneAdminId de l'acteur relu en base ; Client.zone non nulle |
| Autre acteur ou profil administratif incohérent | Aucune lecture administrative autorisée |

Assembler une seule définition du périmètre, réutilisée pour la sélection paginée, le total et le détail. Le résultat est l'intersection de ce périmètre et des filtres valides. Aucun findAll réseau suivi d'un filtrage Java, aucune page réseau filtrée après coup, aucun count global pour LOCAL. Un filtre Angular de zone, rôle ou catégorie ne remplace jamais le périmètre.

PageResponse conceptuel : content (DTO explicites), page, size, totalElements, totalPages. Les compteurs décrivent uniquement le même ensemble filtré et autorisé que content. Sans résultats autorisés : 200 avec contenu vide et total nul proposé, sans indication de comptes hors périmètre. En concurrence, liste et total doivent conserver le même prédicat ; une éventuelle exigence d'instantané strict reste à préciser, sans promesse d'isolation nouvelle.

Les entrées mal formées ou valeurs hors des domaines qui seront validés donneront 400. Pour un filtre syntaxiquement valide mais incompatible avec le rôle, la catégorie ou le périmètre, décider le traitement (rejet ou intersection vide) avant implémentation ; ne pas utiliser ce cas pour révéler des lignes hors périmètre. Les défauts et limites de Spring Pageable ne doivent pas devenir des choix fonctionnels implicites.

### 5.3 DTO commun liste et détail

CompteAdministrationResponse conceptuel, mappé explicitement sans sérialiser Compte ou Client :

| Champs | Signification / exposition proposée |
|---|---|
| compteId, identifiant, role | Identité technique et login existants. CLIENT : identifiant = numeroClient généré. ADMIN : identifiant contient l'e-mail de connexion ; il est visible au GLOBAL pour les lignes ADMIN autorisées |
| clientId, numeroClient, categorie, zoneClientId | Champs CLIENT ; zone nulle admise pour VIP/OCCASIONNEL. Nuls pour ADMIN |
| perimetreAdmin, zoneAdminId | Champs ADMIN ; zone nulle pour GLOBAL. Nuls pour CLIENT |
| compteActif, clientActif, accesEffectif | Statut technique, statut métier nullable pour ADMIN et accès effectif dérivé selon A8 |

accesEffectif décrit les deux statuts persistés : Compte.actif pour ADMIN ; Compte.actif ET Client.actif pour CLIENT. Il ne prétend ni valider un JWT particulier ni attribuer une permission. La lecture de ces champs ne crée aucun droit de bascule de statut.

**Visibilité des e-mails :** l'e-mail de connexion ADMIN fait partie de identifiant ; interdire tous les e-mails tout en exposant ce champ serait contradictoire avec DEC-COMPTES-003. Le contact CLIENT n'est pas son login et n'est pas inclus dans le DTO commun proposé, en liste comme en détail. Son éventuelle consultation future exige une finalité, une permission et un contrat explicites. Cette distinction n'accorde aucune visibilité de comptes ADMIN à LOCAL.

Aucun password, passwordHash, secret JWT, jeton de preuve, entité JPA ou stack trace. Ne pas lire le registre privé pour alimenter le DTO. actionsAutorisees est différé : les opérations d'écriture et leurs droits ne sont pas entièrement spécifiés. Toute future aide d'affichage devra être dérivée du backend et ne remplacera pas ses contrôles.

### 5.4 GET /api/admin/comptes/{id} et erreurs sans divulgation

id désigne Compte.id, conservé ; il ne s'agit ni du matricule ni d'un login libre. Même DTO et même périmètre que la liste. Valider la forme de l'id selon le contrat retenu (400 pour entrée invalide), puis rechercher le compte **avec le périmètre dans la requête**.

| Situation | État / réponse |
|---|---|
| Authentification absente, JWT invalide/expiré ou compte appelant indisponible | 401, mécanisme existant conservé |
| Acteur authentifié sans droit d'opération administrative, notamment CLIENT | 403 avant toute recherche d'existence de la cible |
| Détail autorisé trouvé dans le périmètre | 200 proposé, DTO uniquement |
| Compte absent ou hors périmètre du lecteur | Refus sans données confirmé ; recommandation d'un même 404 générique dans les deux cas, **À VALIDER P12** |

L'ancienne proposition 403 pour une cible hors zone et 404 pour une cible inexistante n'est pas retenue comme règle définitive : cette différence peut révéler l'existence d'un compte, même sans ses attributs. La recommandation P12 utilise le même résultat de recherche filtrée et ne lance pas de recherche globale de secours pour distinguer les deux cas. Le message et le code d'erreur doivent rester identiques pour absent/invisible. 403 reste disponible pour une interdiction d'opération établie indépendamment de l'existence de la cible. Les garanties contre la divulgation d'existence devront être testées ; le seul code HTTP ne démontre pas l'absence de tous canaux auxiliaires.

Aucun contrat de mutation ne se déduit de ce choix de lecture. La politique exacte 403/404 devra être validée avant le lot de développement ; aucun de ces GET n'est annoncé comme livré.

### 5.5 Réutilisation et responsabilités du futur lot

Controller : validation HTTP et DTO. Un seul service de gestion des comptes : autorisation, périmètre de lecture et orchestration en transaction de lecture. CompteRepository existant : requêtes filtrées et pagination ; ClientRepository et ZoneRepository seulement si utiles, sans repository concurrent pour les mêmes données. Mapper : conversion explicite des champs de la section 5.3.

Compléter AdminAuthorization dans sa responsabilité actuelle ; ne pas créer un second service de permissions. canManageZone refuse actuellement une zone nulle même pour GLOBAL : ne pas l'appeler tel quel pour conclure qu'un GLOBAL ne peut pas consulter un client sans zone. Ajouter ultérieurement la politique de consultation nécessaire dans cette même classe, sans relâcher globalement son contrat pour les autres opérations. Le service et les requêtes doivent partager la même décision de périmètre.

Conserver AuthService, le PasswordEncoder BCrypt, TokenService et le rechargement du principal depuis PostgreSQL. Réutiliser GlobalExceptionHandler et ApiErrorResponse ; ajouter ultérieurement le traitement métier de ressource introuvable selon P12, sans second format d'erreurs. Cache-Control: no-store est recommandé pour les réponses administratives.

Aucun changement de schéma nécessaire pour définir ces lectures. Des index ne seront proposés qu'après choix des requêtes et analyse de leur utilité, via nouvelle migration si nécessaire ; V1–V4 restent inchangées. Invitations, créations, statuts, transitions et Angular sont hors du lot 3A.

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

Réutiliser ApiErrorResponse : timestamp, status, code, message, path, validationErrors (field/message). Les codes existants sont VALIDATION_ERROR, INVALID_REQUEST, UNAUTHORIZED, FORBIDDEN. Les codes symboliques de 404/409 restent à stabiliser et leurs handlers métier à développer ; ils ne sont pas livrés actuellement.

| HTTP | Sens cible |
|---|---|
| 400 | JSON, champ, enum, filtre ou identifiant de ressource invalide |
| 401 | Authentification absente/invalide/expirée ; login incorrect ou compte indisponible |
| 403 | Principal authentifié sans droit d'opération ; pour le détail administratif proposé, distinguer ce refus de l'absence/invisibilité selon P12 |
| 404 | Ressource autorisée introuvable ; occultation d'un détail invisible recommandée mais à valider P12 |
| 409 | Unicité, invariant métier ou conflit concurrent |

Ne pas renvoyer la valeur rejetée d'un mot de passe, une requête SQL, un nom technique de contrainte ou une stack trace. Réponses d'erreur de connexion génériques. Conserver les réponses sensibles sans mise en cache ; politique no-store recommandée également pour les DTO administratifs.

Future sécurité : contrôles de rôle et de périmètre dans les services, plus restrictions HTTP ; seul POST register explicitement public lorsque livré, pas /api/auth/** entièrement public. Les parcours de preuve/invitation devront avoir leur propre preuve à usage unique et une limitation des abus. Pas d'accès à une API existante accordé par une catégorie métier.

Références : DEC/A1–A9 ; RM01–RM04/RM07/RM09–RM13/RM49/RM51/RM57–RM60 ; AA5–AA9. Les API seules ne démontrent pas les acquis : exécuter ultérieurement les [scénarios](tests-acceptation.md).
