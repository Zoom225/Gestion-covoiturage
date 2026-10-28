-- Remboursement intégral uniquement : le montant initial de 20 EUR reste inchangé.
-- NULL signifie qu'aucun remboursement n'a été effectué, y compris pour l'historique existant.
ALTER TABLE paiement_place ADD COLUMN rembourse_le timestamptz,
 ADD CONSTRAINT ck_remboursement_apres_paiement CHECK(rembourse_le IS NULL OR rembourse_le>=valide_le);
