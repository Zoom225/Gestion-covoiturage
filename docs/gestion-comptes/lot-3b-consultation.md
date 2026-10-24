# Lot 3B — consultation administrative et preuves TFE

Issue [#15](https://github.com/Zoom225/Gestion-covoiturage/issues/15). Base : origin/master a17d32d après fusion de la [PR #14](https://github.com/Zoom225/Gestion-covoiturage/pull/14), avec fermeture automatique de #13. Branche : feature/admin-account-consultation ; worktree covoiturage-feature-admin-account-consultation. Aucun worktree préexistant modifié.

[Décisions](decisions.md) · [Permissions](matrice-permissions.md) · [Contrats](contrats-api.md#5-consultation-administrative--implémentée-lot-3b) · [Scénarios et tests](tests-acceptation.md#2-bis-consultation-administrative--preuves-du-lot-3b).

## Besoin et décisions

Le responsable a validé pour ce lot : GLOBAL sur le réseau ; LOCAL uniquement CLIENT de sa zone ; CLIENT sans accès administratif. Pagination indexée à zéro, 20 par défaut, maximum 100 ; ordre compteId croissant ; recherche exacte insensible à la casse ; filtres rôle/catégorie/zone/statuts par intersection. Un détail invisible ou absent produit le même 404. Ce sont les arbitrages P12 et la précision de P07 attendus au lot 3A ; les décisions de création et de mutation restent ouvertes.

Références relues : énoncé CarShare Network et cahier référencé dans README, RM01–RM04, RM13, RM49, RM51, RM57–RM60 ; DEC-COMPTES-001/002/003 et A1–A9. Aucun droit commercial VIP, invitation, changement de catégorie, réactivation ou création de zone n'est déduit de ce lot.

## Architecture et responsabilités

~~~mermaid
flowchart LR
    H[HTTP + JWT] --> C[CompteController]
    C --> S[CompteService : lecture transactionnelle]
    S --> A[AdminAuthorization : périmètre]
    S --> R[CompteRepository : Specification + pagination]
    R --> P[(PostgreSQL)]
    S --> M[CompteMapper : DTO explicite]
    E[GlobalExceptionHandler + ApiErrorResponse] --> C
~~~

- Controller : paramètres HTTP, Bean Validation, réponses et no-store ; deux GET seulement.
- Service : autorisation dans la politique existante, intersection des filtres et du périmètre, orchestration en transaction de lecture.
- Repository existant : JpaSpecificationExecutor, même prédicat SQL pour sélection et total ; détail filtré par id et périmètre. EntityGraph pour les associations client/zones, sans chargements par ligne.
- Mapper : liste explicite des champs du DTO ; jamais d'entité sérialisée.
- GlobalExceptionHandler : format existant des erreurs, étendu au 404 et à la validation des paramètres.
- AuthService/TokenService/PasswordEncoder : mécanisme unique conservé. Le calcul des deux statuts est partagé dans Compte.isAccesEffectif, sans changement de comportement.

Les Specifications composent les prédicats dans le repository existant ; cette utilisation suit la [documentation Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html). Le chargement déclaré des associations repose sur [EntityGraph](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.entity-graph). Les erreurs de validation de modèle et de méthode sont traitées conformément aux [deux niveaux de validation Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html). Aucune dépendance supplémentaire.

## Modèle de données conservé

~~~mermaid
erDiagram
    COMPTE o|--o| CLIENT : "client_id UNIQUE"
    ZONE o|--o{ CLIENT : "zone_id"
    ZONE o|--o{ COMPTE : "zone_admin_id"
~~~

Les cardinalités optionnelles sont affinées par les contraintes de rôle : CLIENT exige son Client, ADMIN n'a pas de Client ; LOCAL administratif exige une zone et GLOBAL n'en a pas ; un Client de catégorie LOCAL exige une zone. Un Client peut exister sans Compte, mais cette API liste les comptes et leurs clients associés. Compte.id et Client.id sont conservés ; V4 impose Compte.identifiant = Client.numero pour CLIENT. ADMIN se connecte par e-mail.

Aucune table, colonne, contrainte ou migration nouvelle ; V1–V4 intactes. Aucune reprise des anciens comptes et aucune écriture dans la base locale. Les méthodes de lecture réutilisent les indexes et relations existants ; aucun nouvel index supposé utile sans mesure.

## Contrat et sécurité

| Route | Résultat |
|---|---|
| GET /api/admin/comptes | ComptePageResponse : content/page/size/totalElements/totalPages ; mêmes restrictions avant pagination et total |
| GET /api/admin/comptes/{id} | CompteAdministrationResponse ; id technique positif ; absent/invisible : 404 NOT_FOUND / Ressource introuvable |

Les paramètres précis, champs et combinaisons sont définis dans les [contrats](contrats-api.md#5-consultation-administrative--implémentée-lot-3b), source unique du format REST. zoneId utilise la zone client pour CLIENT ou administrative pour ADMIN. Des filtres valides incompatibles produisent une intersection vide, pas un accès élargi. Sans filtre, les cibles inactives restent visibles dans le périmètre ; l'acteur doit être actif.

Le JWT conserve sub=Compte.id et les données d'autorisation sont relues en PostgreSQL à chaque requête. Une catégorie n'est pas un rôle Spring Security. GLOBAL lit aussi les clients sans zone ; LOCAL ne lit aucun ADMIN, même lui-même via ces GET (me reste disponible). Aucun filtre Angular ni claim de rôle ne remplace ces règles.

401 pour authentification indisponible ; 403 pour CLIENT authentifié ; même 404 pour cible absente/invisible, sans recherche globale de secours. Les 400 ne reprennent pas les valeurs rejetées ni des détails de conversion techniques. La seule différence path des erreurs correspond à l'URI déjà fournie par l'appelant ; aucun attribut invisible n'est renvoyé.

Le DTO expose l'e-mail de connexion ADMIN comme identifiant aux seuls lecteurs autorisés ; pas de contact CLIENT. Aucun mot de passe/hash/secret/jeton, aucun registre privé, aucune invitation ni action de mutation exposée. BCrypt et les deux contrôles d'activité sont conservés. La borne technique du décalage JPA est validée, sans troncature ni requête invalide.

## Tests et résultats

[AdminCompteIntegrationTests](../../src/test/java/com/plateforme/covoiturage/AdminCompteIntegrationTests.java) : 35 tests réels (19 simples + 16 cas paramétrés). Base PostgreSQL 16.6 Testcontainers via ServiceConnection, serveur HTTP sur port aléatoire, clé JWT de test éphémère ; aucun secret ou compte de démonstration du poste utilisé.

Couverture : GLOBAL/LOCAL, tous les ADMIN invisibles à LOCAL, clients hors zone et sans zone, pagination/totaux, recherche exacte/casse, filtres intersectés, statuts indépendants, validation et décalage, 401/403/404, relecture des droits, contrôle du service indépendamment du filtre HTTP, schéma DTO, no-store et OpenAPI. Les mesures Hibernate vérifient au plus trois requêtes pour principal/page/total et deux pour principal/détail, sans chargement secondaire d'entité dans les parcours testés.

| Vérification locale, 24 octobre 2026 | Résultat |
|---|---|
| Compilation Java 21 | BUILD SUCCESS |
| mvnw.cmd -Dtest=AdminCompteIntegrationTests test | 35 tests, 0 échec, 0 erreur, 0 ignoré |
| mvnw.cmd clean verify | BUILD SUCCESS ; 95 tests, 0 échec, 0 erreur, 0 ignoré ; 5 min 45 s, Java 21.0.9 ; JAR Spring Boot produit |

Les 60 tests antérieurs restent actifs : migrations V1–V4, numérotation, unicité/cohérence des identifiants, BCrypt/JWT, profil, sécurité et Swagger dev/prod. Les fixtures et nettoyages des tests ne concernent que les conteneurs éphémères. Le premier essai ciblé a détecté un utilitaire ids manquant dans la classe de test ; il a été ajouté avant le passage réussi, sans retirer d'assertion.

Les 38 AC01–AC38 restent un référentiel de parcours partiellement futurs. AC-CONS01–AC-CONS08 sont reliés aux nouveaux tests, sans être comptés comme huit tests supplémentaires. Résultats GitHub Actions à distinguer des résultats locaux et à consulter dans la PR ; aucune réussite CI n'est supposée avant son exécution.

## Conformité AA1–AA11 et limites

| AA | Contribution et preuve de ce lot | Limite |
|---|---|---|
| AA1 | Acteurs GLOBAL/LOCAL/CLIENT et besoin de consultation clarifiés | Analyse complète du réseau non réalisée par ce seul lot |
| AA2 | P07/P12 et contrat validés, liens vers cahier/RM/décisions | Création/invitation/transitions encore à spécifier |
| AA3 | Modèle et cardinalités existants expliqués, identités conservées | Aucun nouveau modèle complet trajets/réservations |
| AA4 | PostgreSQL réel, filtrage et contraintes existantes conservés | Aucune migration nouvelle ; optimisation à mesurer |
| AA5 | Controller → Service → Repository, mapper et politique réutilisés | Couverture limitée à la consultation |
| AA6 | JWT/BCrypt, rôle/périmètre, activité, 401/403/404, DTO sans secrets | Invitations, réinitialisation, inscription hors lot |
| AA7 | Deux GET REST et OpenAPI, page/DTO documentés | Pas de CRUD complet ni fonctions trajets |
| AA8 | Contrat consommable par un futur tableau | Aucun Angular développé ni test de responsive |
| AA9 | Validation types/bornes et erreurs centralisées sans données sensibles | Pas de validation des futurs formulaires de création |
| AA10 | Tests PostgreSQL/HTTP, requêtes N+1, non-régression, branche/issue/PR et CI vérifiée lors de livraison | Tests Angular et autres modules futurs |
| AA11 | Dossier soutenance : architecture, modèle, règles, sécurité, contrats et résultats | Démonstration complète du réseau encore à construire |

RM directement concernées : RM01–RM04/RM13 (identité, rôle/catégorie, activité), RM49/RM51/RM57 (zone/réseau/comptage), RM58–RM60 (intégrité, identifiants et non-divulgation). Les autres RM restent hors implémentation, avec leur classification conservée dans decisions.md. Aucun acquis n'est annoncé entièrement validé par ce seul lot.

## Suite du développement

La PR du lot 3B attend validation avant fusion. Les comptes métier, invitations, premier GLOBAL, inscription publique et preuve d'e-mail, changements de catégorie/zone/statut et Angular feront l'objet de lots séparés après leurs arbitrages. Aucun compte fictif n'est créé au démarrage ; registre privé, données locales et worktrees existants restent préservés.
