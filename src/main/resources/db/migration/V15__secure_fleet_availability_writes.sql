-- Décisions du 26 octobre 2026 : désactivation sans annulation des trajets existants.
-- Sérialiser aussi la vérification initiale face à d'éventuels anciens écrivains.
LOCK TABLE zone, vehicule, trajet, indisponibilite IN SHARE ROW EXCLUSIVE MODE;
-- Refuser une histoire incompatible plutôt que modifier silencieusement ses données.
DO $$
BEGIN
 IF EXISTS(SELECT 1 FROM indisponibilite p JOIN trajet t
   ON (p.zone_id=t.zone_id OR p.vehicule_id=t.vehicule_id)
   AND t.statut<>'ANNULE' AND p.debut<t.arrivee AND p.fin>t.depart)
 THEN RAISE EXCEPTION 'Existing availability conflict requires approved reconciliation'
  USING ERRCODE='23514'; END IF;
END $$;
ALTER TABLE vehicule ADD COLUMN actif boolean NOT NULL DEFAULT true;

-- Même ordre de sérialisation que JourneyLocks : zone, puis véhicule.
CREATE FUNCTION verifier_disponibilite_trajet() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE disponible boolean;
BEGIN
 IF NEW.statut = 'ANNULE' THEN RETURN NEW; END IF;
 IF TG_OP = 'UPDATE' THEN
  IF OLD.statut <> 'ANNULE'
    AND NEW.zone_id = OLD.zone_id AND NEW.vehicule_id = OLD.vehicule_id
    AND NEW.depart = OLD.depart AND NEW.arrivee = OLD.arrivee
  THEN RETURN NEW; END IF;
 END IF;
 PERFORM 1 FROM zone WHERE id=NEW.zone_id FOR UPDATE;
 SELECT actif INTO disponible FROM vehicule WHERE id=NEW.vehicule_id FOR UPDATE;
 IF disponible IS FALSE THEN
  RAISE EXCEPTION 'Inactive vehicle cannot receive new planning' USING ERRCODE='23514';
 END IF;
 IF EXISTS(SELECT 1 FROM indisponibilite p
   WHERE (p.zone_id=NEW.zone_id OR p.vehicule_id=NEW.vehicule_id)
   AND p.debut<NEW.arrivee AND p.fin>NEW.depart)
 THEN RAISE EXCEPTION 'Trip overlaps unavailable resource' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_trajet_disponibilite
BEFORE INSERT OR UPDATE OF zone_id,vehicule_id,depart,arrivee,statut ON trajet
FOR EACH ROW EXECUTE FUNCTION verifier_disponibilite_trajet();

CREATE FUNCTION verifier_indisponibilite_trajets() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE cible_zone bigint;
BEGIN
 cible_zone := NEW.zone_id;
 IF NEW.vehicule_id IS NOT NULL THEN
  SELECT zone_id INTO cible_zone FROM vehicule WHERE id=NEW.vehicule_id;
 END IF;
 PERFORM 1 FROM zone WHERE id=cible_zone FOR UPDATE;
 IF NEW.vehicule_id IS NOT NULL THEN
  PERFORM 1 FROM vehicule WHERE id=NEW.vehicule_id FOR UPDATE;
  IF NOT EXISTS(SELECT 1 FROM vehicule WHERE id=NEW.vehicule_id AND zone_id=cible_zone)
  THEN RAISE EXCEPTION 'Resource changed concurrently' USING ERRCODE='23514'; END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM trajet t WHERE t.statut<>'ANNULE'
   AND t.zone_id=cible_zone AND (NEW.vehicule_id IS NULL OR t.vehicule_id=NEW.vehicule_id)
   AND t.depart<NEW.fin AND t.arrivee>NEW.debut)
 THEN RAISE EXCEPTION 'Unavailability conflicts with existing trip' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_indisponibilite_trajets BEFORE INSERT OR UPDATE ON indisponibilite
FOR EACH ROW EXECUTE FUNCTION verifier_indisponibilite_trajets();
