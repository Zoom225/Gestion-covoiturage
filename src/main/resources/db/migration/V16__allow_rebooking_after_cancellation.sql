-- Conserver chaque réservation historique. Une seule réservation non annulée
-- peut exister pour un couple trajet/client.
LOCK TABLE reservation IN SHARE ROW EXCLUSIVE MODE;
ALTER TABLE reservation DROP CONSTRAINT uq_reservation_client;
CREATE UNIQUE INDEX uq_reservation_client_active
    ON reservation(trajet_id, client_id)
    WHERE statut <> 'ANNULEE';
