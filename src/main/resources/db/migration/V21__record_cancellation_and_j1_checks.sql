-- Les anciennes annulations restent non datées : aucune date historique n'est inventée.
ALTER TABLE reservation ADD COLUMN annule_le timestamptz,
 ADD CONSTRAINT ck_reservation_annulation_date CHECK(annule_le IS NULL OR statut='ANNULEE');

-- La date existante mémorise désormais aussi un contrôle réussi sur un trajet PRIVE.
ALTER TABLE trajet DROP CONSTRAINT ck_j1_public,
 ADD COLUMN verification_j1_requise boolean NOT NULL DEFAULT false,
 ADD CONSTRAINT ck_j1_verification CHECK(NOT(verification_j1_requise AND traite_j1_le IS NOT NULL));
