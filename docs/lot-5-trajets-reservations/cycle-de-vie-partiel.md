# Intégrité du cycle de vie — issue #26

Ce lot dépend de la branche de réinscription et de V16. V17 conserve les paiements
des réservations `ANNULEE`. La preuve différée exige toujours un paiement de 20 €
pour `CONFIRMEE` et interdit un paiement sur `EN_ATTENTE`. Elle refuse un historique
incohérent avant migration et ne supprime aucune réservation, paiement ou dette.

Une réservation annulée ne peut plus être réactivée : la réinscription crée une
nouvelle ligne. L'annulation d'une place `CONFIRMEE` ramène un trajet `COMPLET`
à `PLANIFIE` lorsque moins de quatre places restent confirmées. Le contrôle de
capacité continue à verrouiller le trajet. L'ancien paiement reste lié à l'ancienne
ligne ; une nouvelle confirmation exige un nouveau paiement de 20 €.

Une réservation annulée ne donne plus accès à un trajet privé. L'organisateur
conserve l'accès à son trajet en sa qualité de propriétaire.

Aucune API d'annulation ou de remboursement n'est exposée par ce lot. Il faut
encore arbitrer le délai d'annulation, le mode d'intervention de l'organisateur,
les remboursements, la transition vers `REALISE` et les modifications des trajets.
Ces parcours ne sont pas déclarés terminés.
