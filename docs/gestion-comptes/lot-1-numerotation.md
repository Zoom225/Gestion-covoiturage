# Lot 1 — numérotation V/C/O et préparation des identifiants

Références : [décisions](decisions.md), [contrats](contrats-api.md), [permissions](matrice-permissions.md), [acceptation](tests-acceptation.md), issue documentaire [#7](https://github.com/Zoom225/Gestion-covoiturage/issues/7).
Branche : feature/client-numbering-vco, créée depuis origin/master 10e85fe (PR documentaire #8 fusionnée).
Issue technique demandée : « feat: préparer la numérotation V/C/O et la migration des identifiants de connexion ». Création refusée par le connecteur GitHub : HTTP 403, Resource not accessible by integration ; aucun numéro inventé.

## Audit préalable

Le 21 octobre 2026, inspection des entités, repositories, services, JWT/BCrypt, V1/V2, tests, énoncé CarShare et cahier référencé par README. Aucun conflit avec RM09–RM11, RM58–RM60.
La base locale a été interrogée exclusivement dans BEGIN READ ONLY / ROLLBACK : V1/V2 réussies, zéro client, compte et zone ; zéro collision ou numéro incompatible. Aucun historique externe à importer identifié. Aucune migration de cette base exécutée.

Ce constat ne vaut pas audit de toutes les autres installations. V3 refuse un numéro existant non canonique ou un suffixe atteignant/dépassant la limite du BIGINT positif avant import ; une correspondance approuvée est nécessaire. Aucun numéro, compte ou hash n'est corrigé automatiquement.

## Fonctionnement livré

- Trois séquences BIGINT indépendantes : client_numero_v_seq, client_numero_c_seq, client_numero_o_seq ; démarrage à un, sans cycle.
- Format : préfixe V/C/O, au moins quatre chiffres avec zéros initiaux, croissance après quatre chiffres sans troncature. La capacité technique est celle d'un BIGINT positif ; une limite métier plus basse reste à préciser.
- ClientNumberGenerator.reserve(CategorieClient) est l'unique service Java de génération. Il exige une transaction appelante (Propagation.MANDATORY).
- ClientNumberRepository appelle reserve_client_number : nextval et insertion unique de la réservation en PostgreSQL. Une collision avec un numéro déjà réservé fait avancer la séquence ; jamais count()+1.
- client_numero_reservation conserve les numéros réservés, leur préfixe, origine, client éventuel, instant de réservation/affectation/retrait. Aucun mot de passe, login ou JWT.
- Les créations SQL/JPA de Client sont également enregistrées par trigger. Une réservation générée ne peut être affectée que dans sa transaction de création ; une réservation abandonnée après commit reste indisponible.
- Les anciens numéros sont conservés lors d'un changement SQL du numéro. Le trigger n'est pas une autorisation de changement de catégorie : aucune transition métier ni API correspondante n'est livrée.
- Les clés et affectations historiques ne peuvent pas être réécrites ni supprimées par DML. Une FK empêche de supprimer un client référencé par cet historique ; aucun nouveau parcours de suppression n'est prévu.
- Les opérations opérateur privilégiées (TRUNCATE, suppression de trigger, remise à zéro manuelle) ne font pas partie de l'API et ne doivent jamais être utilisées pour gérer les numéros. Le rôle applicatif de production devra disposer des seuls droits nécessaires.

## Transaction et utilisation future

Le futur service de création possède la transaction : réserver le numéro, enregistrer le Client avec ce numéro, puis le Compte et les écritures du parcours autorisé. Une erreur annule les lignes Client/Compte/réservation ; la valeur de séquence consommée reste un trou et n'est pas réutilisée lors d'un rollback ordinaire. Ne pas réserver dans une transaction séparée pour affecter plus tard le numéro.

Aucun endpoint n'est ajouté. Le futur chemin reste Controller → Service transactionnel → Repository → PostgreSQL. L'absence de service de création dans ce lot est volontaire ; aucune création de compte réel ou fictif n'a lieu au démarrage.

## Migration V3 et compatibilité

V1/V2 restent inchangées. V3 :
1. Verrouille les écritures Client pendant l'import et le positionnement des séquences.
2. Vérifie que les numéros existants peuvent être conservés tels quels.
3. Copie ces numéros dans l'historique sans modifier Client.id, Client.numero, Compte.id, identifiant, activité ou hash.
4. Positionne chaque séquence au-delà du plus grand suffixe connu de son préfixe, puis installe les fonctions et triggers.
5. Utilise le search_path capturé par Flyway pour rester correcte dans un schéma isolé de test ou une installation hors public.

L'horodatage MIGRATION représente la date d'observation, pas une date d'attribution ancienne inventée. Les anciens numéros retirés avant V3 ne peuvent pas être reconstitués sans source fiable : leur inventaire est requis avant reprise d'une installation qui en possède. L'audit local vide ne présente pas ce cas.

Aucune contrainte identifiant = numero, aucun index de login insensible à la casse, aucune obligation e-mail ADMIN et aucun contrôle nouveau préfixe/catégorie sur les lignes Client existantes. Les anciens logins continuent donc à fonctionner à l'identique. L'historique décrit le préfixe du numéro attribué ; il ne déduit pas la catégorie ou les permissions du client.

## DEC-COMPTES-003 — cible des lots suivants

Décisions validées : Compte.identifiant contiendra l'e-mail ADMIN en minuscules ou le matricule CLIENT en majuscules ; saisie client en minuscules acceptée ; unicité de connexion insensible à la casse. Un seul AuthService, le PasswordEncoder BCrypt et le JWT existants. Identités techniques conservées.

La nouvelle résolution de login, sa migration contrôlée et la cohérence entre Client.numero et Compte.identifiant restent à implémenter au lot suivant, après normalisation/longueurs et correspondances approuvées. La catégorie et le périmètre restent lus en PostgreSQL.

Les changements de catégorie imposeront nouveau matricule, modification atomique du login et historique ; permissions exactes, zones et réservations futures doivent être précisées avant leur API. Aucun ancien numéro ne devient automatiquement un alias de connexion.

DEC-COMPTES-001 garde le mot de passe choisi lors de l'inscription OCCASIONNEL et l'invitation des créations administratives normales. DEC-COMPTES-002 reste limitée aux données fictives isolées ; les valeurs concrètes sont uniquement dans le registre privé ignoré. BCrypt et JWT ne sont pas modifiés.

## Scénarios automatisés du lot

Les identifiants AC-NUM distinguent ces tests des scénarios documentaires AC01–AC38 présents dans la base Git de ce lot et des révisions non publiées dans l'autre worktree.

| Scénario | Preuve attendue |
|---|---|
| AC-NUM01 | Premier numéro à quatre chiffres et indépendance V/C/O |
| AC-NUM02 | Passage de 9999 à 10000 pour chaque préfixe |
| AC-NUM03 | Génération refusée sans transaction appelante |
| AC-NUM04 | 32 créations dans des transactions concurrentes : numéros distincts et historique affecté après commits |
| AC-NUM05 | Échec après création Client/Compte : rollback des trois tables et trou de séquence |
| AC-NUM06 | Ancien numéro conservé, refus de réattribution au même client ou à un autre, collision ignorée même après recul artificiel de séquence |
| AC-NUM07 | Écriture JPA directe historisée et évitée par le générateur |
| AC-NUM08 | Une autre transaction ne peut pas récupérer une réservation abandonnée |
| AC-NUM09 | Suppression ou réécriture de l'allocation refusée |
| AC-NUM10 | Épuisement BIGINT : refus sans retour au début |
| AC-NUM11 | V2 vers V3 : données, ids, anciens logins et hash conservés ; séquences positionnées au-delà des numéros importés ; Flyway rejouable sans nouvelle migration |
| AC-NUM12 | Ancien numéro incompatible : V3 échoue et les données V2 restent intactes |

Les tests réutilisent PostgreSQL 16.6 Testcontainers. Le nettoyage de l'historique et le réglage des séquences ont lieu uniquement dans ces bases jetables, jamais dans la base locale. Les 36 tests préexistants restent actifs ; seule l'attente de version Flyway et la remise à zéro des fixtures de sécurité sont adaptées.

**Résultat exécuté le 21 octobre 2026 :** `mvnw.cmd clean verify` — BUILD SUCCESS, 48 tests, zéro échec, zéro erreur, zéro test ignoré (12 tests de numérotation et 36 tests existants). Compilation Java 21 et JAR Spring Boot produits. Un premier passage a révélé une fuite de fixture entre contextes partagés ; le nettoyage avant/après les tests de numérotation a corrigé cette isolation, sans désactiver de test. Les scénarios documentaires AC01–AC38 ne sont pas comptés comme tests exécutés.

## Traçabilité et prochaine étape

| Références | Portée du lot |
|---|---|
| RM09–RM11 | Numéros générés V/C/O |
| RM58–RM59 | Réservation, FK, unicité, atomicité et concurrence |
| RM60 | Historique des allocations ; auteur/motif des futures transitions non encore livrés |
| RM01–RM04, RM13, RM49/RM51 | Non-régression de la sécurité existante |
| RM restantes | Aucun changement des règles de réservation, trajet, paiement, dette ou zone |
| AA3–AA5 | Modèle persistant, migration et couches |
| AA6–AA7, AA9 | Sécurité et contrat HTTP existants préservés |
| AA10–AA11 | Tests d'intégration, Git et documentation |
| AA1–AA2 | Analyse et décisions tracées ; AA8 sans développement Angular |

Étape suivante : normalisation et migration explicite des identifiants, adaptation login/me et tests PostgreSQL/HTTP des cinq profils. Inscription publique, administration, transitions et démonstration restent des lots séparés.
