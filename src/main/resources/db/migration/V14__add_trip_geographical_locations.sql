-- Keep historical trips without inventing geographical information.
ALTER TABLE trajet
 ADD COLUMN origine varchar(255),
 ADD COLUMN destination varchar(255),
 ADD CONSTRAINT ck_trajet_lieux CHECK (
  (origine IS NULL AND destination IS NULL)
  OR (origine IS NOT NULL AND destination IS NOT NULL
      AND origine ~ '[^[:space:]]' AND destination ~ '[^[:space:]]')
 );

-- Only rows already missing both locations may retain that historical state.
-- Existing J-1/payment/status updates remain possible without a fictitious backfill.
CREATE FUNCTION verifier_lieux_trajet() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 IF NEW.origine IS NULL OR NEW.destination IS NULL THEN
  IF TG_OP = 'UPDATE' THEN
   IF OLD.origine IS NULL AND OLD.destination IS NULL
      AND NEW.origine IS NULL AND NEW.destination IS NULL THEN
    RETURN NEW;
   END IF;
  END IF;
  RAISE EXCEPTION 'Both geographical locations are required for a new or completed trip'
   USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;

CREATE TRIGGER trg_trajet_lieux
 BEFORE INSERT OR UPDATE OF origine,destination ON trajet
 FOR EACH ROW EXECUTE FUNCTION verifier_lieux_trajet();
