# Lot 2 — connexion e-mail ADMIN / matricule CLIENT

Issue [#11](https://github.com/Zoom225/Gestion-covoiturage/issues/11), DEC-COMPTES-003.
Base : origin/master b86711e ; PR #10 fusionnée, issue #9 fermée. Branche feature/auth-email-matricule.
Références : [décisions](decisions.md), [contrats](contrats-api.md), [permissions](matrice-permissions.md), [acceptation](tests-acceptation.md), [lot 1](lot-1-numerotation.md).

## Audit et portée

Le 24 octobre 2026 : inspection de l'énoncé, du cahier référencé par README, RM01–RM60 et AA1–AA11, entités, repositories, services, DTO, JWT/BCrypt, V1–V3 et tests.
La base locale a été interrogée uniquement dans BEGIN READ ONLY / ROLLBACK : V1/V2 appliquées, zéro compte, client et zone ; zéro collision de login ou numéro incompatible. Aucun identifiant, hash, mot de passe ou jeton affiché.
Cet audit ne remplace pas celui d'une autre installation et ne donne aucune autorisation de modifier la base locale. V4 n'y est pas appliquée par ce travail.

Aucune création d'utilisateur au démarrage, inscription publique, transition de catégorie, API administrative, donnée de démonstration ou interface Angular. Les parcours futurs conservent mot de passe choisi / invitation selon DEC-COMPTES-001/A7 ; DEC-COMPTES-002 reste strictement fictive et isolée. Le registre privé et ses valeurs ne sont pas publiés.

## Contrat effectivement livré

POST /api/auth/login conserve LoginRequest avec identifiant et password ; GET /api/auth/me conserve ProfileResponse.

| Profil | Saisie de connexion | Stockage exigé | Autorité de permissions |
|---|---|---|---|
| ADMIN GLOBAL | E-mail, casse indifférente | Compte.identifiant en minuscules | Compte.role, perimetreAdmin et zoneAdmin en PostgreSQL |
| ADMIN LOCAL | E-mail, casse indifférente | Compte.identifiant en minuscules | Compte réel et zone administrative |
| CLIENT VIP | Matricule, casse indifférente | Matricule V en majuscules, égal à Client.numero | Compte CLIENT et catégorie/zone du Client |
| CLIENT LOCAL | Matricule, casse indifférente | Matricule C en majuscules, égal à Client.numero | Compte CLIENT et rattachement local |
| CLIENT OCCASIONNEL | Matricule, casse indifférente | Matricule O en majuscules, égal à Client.numero | Compte CLIENT et catégorie/zone persistées |

Les préfixes ne confèrent jamais un rôle. Une manipulation de role, clientId, catégorie ou zone dans le JSON n'accorde aucun privilège.
L'e-mail de contact d'un CLIENT n'est pas une alternative à son matricule. Aucun ancien login ou numéro retiré n'est recherché comme alias.

AuthService est conservé ; CompteRepository utilise explicitement lower(identifiant) = lower(paramètre), avec EntityGraph des relations existantes.
La même fonction PostgreSQL que l'index définit la comparaison, sans normalisation Java concurrente ou dépendante de la locale de la JVM.
Aucune écriture n'est faite pendant la connexion pour corriger un ancien compte.

## Canonisation, limites et erreurs

- Stockage canonique imposé par CHECK : e-mail ADMIN déjà en minuscules ; matricule CLIENT et Client.numero sous forme V/C/O suivie d'au moins quatre chiffres.
- Aucune API de création n'est livrée. Une future création doit fournir la valeur canonique ; pour un e-mail Unicode, utiliser les mêmes règles PostgreSQL que la recherche et l'index. Une écriture non canonique est refusée, sans correction silencieuse.
- La limite existante de Compte.identifiant et LoginRequest est conservée à 100 caractères. L'élargissement éventuel est un changement séparé à décider ; les matricules générés par BIGINT restent dans cette limite.
- Aucun trim automatique, suppression d'accent, de points ou de suffixe d'adresse. Les zéros du matricule sont conservés.
- Le contrôle SQL d'e-mail impose une partie de chaque côté d'un unique @, sans espace. Il ne vérifie pas la délivrabilité, n'ajoute ni preuve d'e-mail ni politique de création de compte.
- Les mots de passe ne sont jamais transformés. BCrypt existant, 72 octets UTF-8 sans troncature, contrôles d'activité et hash factice d'un compte inconnu conservés.
- 400 : validation JSON/champs existante, dont vide ou dépassement de taille. 401 : identifiant inconnu/invalide, mauvais mot de passe ou compte/client inactif ; même message générique. 403 : utilisateur authentifié sans droit. Aucun nouveau 404/409 métier ni API de création.
- Réponses no-store, pas de cookie de session. Aucun hash ou secret dans les DTO ou messages d'erreur.

## V4 — intégrité sans conversion de données

V1/V2/V3 restent inchangées. V4 verrouille les écritures des tables concernées pendant audit et ajout des contraintes.
Elle refuse avant modification structurelle finale :
- collisions de lower(identifiant) ;
- numéros non canoniques ;
- ADMIN sans e-mail canonique ;
- CLIENT dont le login diffère du matricule associé.

Après audit satisfaisant :
1. Index unique uk_compte_identifiant_casefold sur lower(identifiant).
2. CHECK du format de Client.numero et des identifiants selon le rôle.
3. Clé UNIQUE non différable client(id, numero).
4. FK composite compte(client_id, identifiant) → client(id, numero), DEFERRABLE INITIALLY DEFERRED.

La FK est vérifiée au commit, ce qui permet au futur service autorisé de changer les deux valeurs dans une transaction sans cascade cachée.
Une modification d'un seul côté échoue et est annulée. MATCH SIMPLE laisse les ADMIN sans Client ; les invariants de profil V2 restent actifs.
La correspondance préfixe/catégorie et les transitions métier ne sont pas ajoutées à ce lot : la catégorie demeure une donnée métier en base, distincte du rôle technique.

Compte.id, Client.id, les hashes BCrypt, états d'activité et relations ne sont ni réécrits ni recréés. Aucun UPDATE de reprise dans V4.
Le JWT conserve sub = Compte.id ; les requêtes protégées rechargent le compte réel, donc un changement autorisé d'identifiant ne réattribue pas un JWT à une autre personne.

## Procédure obligatoire si une installation contient des données incompatibles

1. Audit en lecture seule : inventaire et compteurs, collisions normalisées, e-mails à confirmer et correspondances Client/Compte.
2. Faire approuver un mapping explicite par identités techniques stables ; aucune adresse inventée depuis un alias, aucun compte fusionné.
3. Définir notification, date de bascule et traitement des anciens logins ; aucun alias implicite.
4. Tester la reprise sur une copie isolée et vérifier ids, relations, hashes, états et historique ; prévoir sauvegarde et retour arrière.
5. Obtenir l'autorisation d'écriture sur cette installation avant une opération opérateur contrôlée.
6. Rejouer l'audit, puis seulement déployer V4 et le code associé. En cas d'échec, corriger la cause ; ne pas modifier V1–V4 appliquées, ne pas contourner les contrôles par repair ou suppression.

Le lot n'exécute aucune de ces écritures sur la base de développement. La future vérification d'e-mail/invitation ne s'applique pas rétroactivement aux comptes préexistants.

## Tests exécutables du lot

Les identifiants AC-AUTH sont indépendants des scénarios documentaires AC01–AC38 et des révisions en attente dans l'autre worktree.

| Scénario | Test automatisé |
|---|---|
| AC-AUTH01 | Deux ADMIN GLOBAL/LOCAL : HTTP login par e-mail avec variantes de casse, puis me conforme à PostgreSQL |
| AC-AUTH02 | Trois catégories CLIENT : HTTP login par matricule en minuscules, puis profil et zone réels |
| AC-AUTH03 | Anciens alias, formats invalides et e-mail de contact : 401 générique |
| AC-AUTH04 | Casse et espaces du mot de passe conservés, indépendamment de la casse du login |
| AC-AUTH05 | CHECK de stockage e-mail canonique et présence de l'index unique fonctionnel |
| AC-AUTH06 | CLIENT : refus du numéro d'autrui, des minuscules stockées ou d'un e-mail comme login |
| AC-AUTH07 | Changement d'un seul côté : rejet au commit et rollback de l'historique/numéro |
| AC-AUTH08 | Changement SQL cohérent dans la même catégorie : ids/hash conservés, historique, ancien login refusé et sujet JWT stable |
| AC-AUTH09 | Deux transactions avec e-mails égaux après normalisation : exactement un propriétaire au commit |
| AC-AUTH10 | Règle de casse Unicode commune à PostgreSQL, l'index et la connexion |
| AC-AUTH11 | Migration V3→V4 : ids, hashes, relations, états, numéros et historique préservés ; validation et second migrate sans changement |
| AC-AUTH12 | Ancien alias, e-mail non canonique ou collision : V4 refuse sans convertir les comptes |

Les 48 tests antérieurs restent actifs. Les fixtures publiques utilisent désormais des logins canoniques ; le test de changement de rôle met simultanément à jour l'identifiant de sa fixture. Le test historique V2→V3 du lot 1 conserve volontairement ses alias dans un schéma ciblé à V3.

**Exécution locale finale du 24 octobre 2026 :** `mvnw.cmd clean verify` — BUILD SUCCESS, 60 tests, zéro échec, zéro erreur et zéro test ignoré : 48 tests antérieurs conservés et 12 nouveaux. Les contrôles de rejet des hashes non BCrypt et des profils invalides vérifient explicitement les contraintes attendues. Compilation Java 21 et JAR Spring Boot produits. Les scénarios documentaires AC01–AC38 ne sont pas comptés comme tests applicatifs exécutés.

## Traçabilité

RM01–RM04/RM13 : identité, rôle, catégorie, activité et propriété ; RM09–RM11 : matricules ; RM49/RM51 : périmètres administratifs inchangés ; RM58–RM60 : FK, unicité et confidentialité.
Les autres RM ne sont pas modifiées. AA3–AA7/AA9–AA11 : modèle, migration, couches, sécurité, REST, validation, tests et documentation ; AA1/AA2 pour l'analyse, AA8 sans développement Angular.

Étapes suivantes distinctes : gestion administrative, inscription publique, invitations et vérification d'e-mail, transitions autorisées de catégorie et Angular. Aucun compte de démonstration initialisé.
