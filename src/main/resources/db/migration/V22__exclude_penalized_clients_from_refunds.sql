-- Conserver la situation au moment de l'annulation, sans réécrire l'historique.
ALTER TABLE reservation
 ADD COLUMN annule_avec_penalite boolean NOT NULL DEFAULT false,
 ADD CONSTRAINT ck_annulation_penalite
 CHECK (NOT annule_avec_penalite OR statut = 'ANNULEE');

-- Aucune nouvelle restitution si une pénalité est active, ou l'était à l'annulation.
CREATE FUNCTION verifier_remboursement_sans_penalite() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE client_concerne bigint;
DECLARE penalite_a_annulation boolean;
BEGIN
 IF NEW.rembourse_le IS NULL THEN RETURN NEW; END IF;
 IF TG_OP = 'UPDATE' AND OLD.rembourse_le IS NOT NULL THEN RETURN NEW; END IF;

 SELECT client_id, annule_avec_penalite INTO client_concerne, penalite_a_annulation
 FROM reservation WHERE id = NEW.reservation_id;
 PERFORM 1 FROM client WHERE id = client_concerne FOR UPDATE;
 IF penalite_a_annulation OR EXISTS (
  SELECT 1 FROM dette_trajet WHERE client_id = client_concerne AND statut = 'ACTIVE'
 ) THEN
  RAISE EXCEPTION 'Refund forbidden for penalized client' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;

CREATE TRIGGER trg_remboursement_sans_penalite
 BEFORE INSERT OR UPDATE OF rembourse_le ON paiement_place
 FOR EACH ROW EXECUTE FUNCTION verifier_remboursement_sans_penalite();
