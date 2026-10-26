-- Une réservation annulée peut garder la preuve de son paiement de place.
-- Une réservation en attente reste impayée et une confirmation reste payée.
LOCK TABLE reservation, paiement_place, trajet IN SHARE ROW EXCLUSIVE MODE;
DO $$
BEGIN
 IF EXISTS (
  SELECT 1 FROM reservation r WHERE
   (r.statut='EN_ATTENTE' AND EXISTS(SELECT 1 FROM paiement_place p WHERE p.reservation_id=r.id))
   OR (r.statut='CONFIRMEE' AND NOT EXISTS(SELECT 1 FROM paiement_place p WHERE p.reservation_id=r.id))
 ) THEN
  RAISE EXCEPTION 'Approved reconciliation required before cancellation integrity upgrade'
   USING ERRCODE='23514';
 END IF;
END $$;

CREATE OR REPLACE FUNCTION verifier_paiement_confirmation() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE affected bigint[]; booking bigint; state varchar(12); paid boolean;
BEGIN
 IF TG_TABLE_NAME='reservation' THEN
  IF TG_OP='INSERT' THEN affected := ARRAY[NEW.id];
  ELSIF TG_OP='DELETE' THEN affected := ARRAY[OLD.id];
  ELSE affected := ARRAY[OLD.id,NEW.id]; END IF;
 ELSE
  IF TG_OP='INSERT' THEN affected := ARRAY[NEW.reservation_id];
  ELSIF TG_OP='DELETE' THEN affected := ARRAY[OLD.reservation_id];
  ELSE affected := ARRAY[OLD.reservation_id,NEW.reservation_id]; END IF;
 END IF;
 FOR booking IN SELECT DISTINCT id FROM unnest(affected) AS refs(id) ORDER BY id LOOP
  SELECT statut INTO state FROM reservation WHERE id=booking FOR UPDATE;
  IF NOT FOUND THEN CONTINUE; END IF;
  SELECT EXISTS(SELECT 1 FROM paiement_place WHERE reservation_id=booking) INTO paid;
  IF (state='CONFIRMEE' AND NOT paid) OR (state='EN_ATTENTE' AND paid) THEN
   RAISE EXCEPTION 'Payment and reservation state must be atomic' USING ERRCODE='23514';
  END IF;
 END LOOP;
 RETURN NULL;
END $$;

-- Le statut ANNULEE appartient à l'histoire : la réinscription crée une ligne neuve.
CREATE FUNCTION proteger_reservation_annulee() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 IF OLD.statut='ANNULEE' AND NEW.statut<>'ANNULEE' THEN
  RAISE EXCEPTION 'Cancelled reservation cannot be reactivated' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_reservation_annulee_immuable
BEFORE UPDATE OF statut ON reservation FOR EACH ROW
EXECUTE FUNCTION proteger_reservation_annulee();

-- Le trigger de capacité V12 verrouille déjà le trajet avant ce recalcul.
CREATE FUNCTION recalculer_trajet_apres_annulation_place() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 IF OLD.statut='CONFIRMEE' AND NEW.statut='ANNULEE' THEN
  UPDATE trajet SET statut='PLANIFIE' WHERE id=NEW.trajet_id AND statut='COMPLET'
   AND (SELECT count(*) FROM reservation WHERE trajet_id=NEW.trajet_id AND statut='CONFIRMEE')<4;
 END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER trg_place_liberee
AFTER UPDATE OF statut ON reservation FOR EACH ROW
EXECUTE FUNCTION recalculer_trajet_apres_annulation_place();
