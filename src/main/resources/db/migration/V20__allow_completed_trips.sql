-- Étendre seulement les statuts autorisés, sans modifier les lignes ni les migrations précédentes.
ALTER TABLE trajet DROP CONSTRAINT trajet_statut_check,
 ADD CONSTRAINT trajet_statut_check CHECK(statut IN ('PLANIFIE','COMPLET','ANNULE','REALISE'));
