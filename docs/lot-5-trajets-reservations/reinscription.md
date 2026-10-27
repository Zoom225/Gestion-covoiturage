# Réinscription après annulation — issue #26

La migration V16 conserve toutes les réservations et remplace uniquement
`uq_reservation_client` par un index unique partiel sur `(trajet_id, client_id)`
pour les statuts différents de `ANNULEE`. Les valeurs de statut vérifiées dans V11
sont `EN_ATTENTE`, `CONFIRMEE` et `ANNULEE`. Aucune ancienne migration n'est modifiée.

`ReservationService` ignore les lignes annulées lors du contrôle des doublons.
La nouvelle demande garde les contrôles existants : trajet ouvert, droit de zone,
absence de dette active et disponibilité. Le verrouillage du trajet sérialise les
créations concurrentes ; l'index PostgreSQL interdit une deuxième demande active.
Une nouvelle réservation possède un nouvel identifiant et son propre paiement
de 20 € pour accéder à `CONFIRMEE`. Les réservations, paiements et dettes antérieurs
restent conservés.

Ce lot n'ajoute pas de commande d'annulation. Les délais d'annulation, le parcours
de remboursement et la transition vers `REALISE` restent à arbitrer dans #26.
La preuve V13 de paiement lié à `CONFIRMEE` demeure en vigueur : une réservation
déjà payée ne peut donc pas encore être annulée sans une évolution distincte
préservant ce paiement historique.
