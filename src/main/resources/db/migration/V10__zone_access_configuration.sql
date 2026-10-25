ALTER TABLE zone ADD COLUMN actif boolean NOT NULL DEFAULT true,
 ADD COLUMN fuseau_horaire varchar(80), ADD COLUMN ouverte_occasionnels boolean NOT NULL DEFAULT false;
ALTER TABLE zone ADD CONSTRAINT ck_zone_timezone_nonblank CHECK (fuseau_horaire IS NULL OR length(trim(fuseau_horaire)) > 0);
-- Existing zones retain their identity; their timezone must be explicitly configured before trips.
CREATE TABLE autorisation_zone_vip (
 client_id bigint NOT NULL REFERENCES client(id),
 zone_id bigint NOT NULL REFERENCES zone(id),
 PRIMARY KEY(client_id,zone_id)
);
CREATE INDEX ix_autorisation_zone ON autorisation_zone_vip(zone_id,client_id);
CREATE FUNCTION verifier_autorisation_vip() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 PERFORM 1 FROM client WHERE id=NEW.client_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM client WHERE id=NEW.client_id AND categorie='VIP') THEN
  RAISE EXCEPTION 'VIP category required' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_autorisation_vip BEFORE INSERT OR UPDATE ON autorisation_zone_vip
 FOR EACH ROW EXECUTE FUNCTION verifier_autorisation_vip();
CREATE FUNCTION proteger_categorie_vip_autorisee() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 IF NEW.categorie <> 'VIP' AND EXISTS(SELECT 1 FROM autorisation_zone_vip WHERE client_id=NEW.id) THEN
  RAISE EXCEPTION 'Remove zone grants before category transition' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_categorie_autorisee BEFORE UPDATE OF categorie ON client
 FOR EACH ROW EXECUTE FUNCTION proteger_categorie_vip_autorisee();
