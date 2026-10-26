# Compléter l’historique des paiements, les statistiques et la traçabilité backend

Le backend conserve les paiements validés mais n'expose pas leur historique
personnel ni les statistiques administratives attendues. Les opérations
sensibles disposent aussi de traces de réussite insuffisantes.

Cette proposition réutilise les services, repositories, DTO, mappers et
politiques existants pour exposer l'historique personnel paginé et des
statistiques filtrées par zone. Elle ajoute une journalisation après commit,
sans secrets ni faux succès après rollback. Les migrations V1–V14 restent
inchangées ; aucun frontend ni base existante n'est modifié.

La configuration de test borne le cache des contextes et libère les ressources
jetables ; la création PostgreSQL est centralisée avec trois tentatives maximum
en cas d'erreur Docker transitoire. Aucune assertion n'est relancée ou ignorée.

Closes #35. Closes #36. Closes #37. Les arbitrages de #25 et #26 restent nécessaires
avant leurs mutations ; aucune règle d'annulation ou de remboursement inventée.

Validation locale : 31 tests ciblés réussis ; clean verify réussi avec 541 tests
dans 26 suites, aucun échec/erreur/ignoré. Les 510 cas antérieurs sont conservés,
31 cas ajoutés. Dernier passage du 26 octobre 2026, 14:47:04 CET, 9 min 31 s,
sans erreur HTTP 500 Docker ni dépassement du délai de sortie Surefire.
Le JAR est construit ; aucune migration existante ni assertion antérieure
n'est modifiée. Périmètre : 24 fichiers, sans frontend ni configuration de production.

Les tests couvrent les périmètres multi-zones, 400/401/403, la pagination et les
sommes, les paiements partiels/intégraux, le commit/rollback réel PostgreSQL,
les preuves différées et l'idempotence. Les contrôles distants restent à lancer
après autorisation de publication.

Document local de préparation : aucune Pull Request créée ou publiée pour ce lot.
