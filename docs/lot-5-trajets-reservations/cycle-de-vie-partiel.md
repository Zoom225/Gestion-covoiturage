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

`POST /api/reservations/{id}/annulation` permet au seul propriétaire d'annuler
sa réservation avant l'instant de départ. L'opération est idempotente, garde sa
ligne et son éventuel paiement de 20 €, et ne déclenche aucun remboursement.
Elle ne demande pas l'absence de dette ACTIVE : cette dette continue à bloquer
uniquement les nouvelles créations, réservations et ajouts. Le trajet et le client
sont verrouillés dans le même ordre que le paiement ; les changements concurrents
sont relus avant la mutation. La réinscription crée une nouvelle ligne et exige
un nouveau paiement pour confirmer la place.

L'annulation directe d'un trajet privé incomplet avant J-1 n'est pas exposée :
le contrôle planifié ignore un trajet `ANNULE`, ce qui permettrait d'éviter sa
dette de 80 € sans politique supplémentaire. La décision sur ce cas est attendue.
Il faut aussi définir l'acteur et le mécanisme de `REALISE`, les remboursements
et les modifications des trajets avant de déclarer ces parcours terminés.
