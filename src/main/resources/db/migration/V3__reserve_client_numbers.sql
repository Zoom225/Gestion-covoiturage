-- Lot 1: additive numbering only. No account, credential or login is changed.
-- Existing writers are excluded while their numbers are imported and sequences seeded.
LOCK TABLE client IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM client WHERE numero !~ '^[VCO][0-9]{4,}$') THEN
        RAISE EXCEPTION 'Client numbering requires an approved legacy-number mapping' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (SELECT 1 FROM client WHERE substring(numero FROM 2)::numeric >= 9223372036854775807) THEN
        RAISE EXCEPTION 'Client numbering exceeds the supported sequence range' USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE SEQUENCE client_numero_v_seq AS BIGINT START WITH 1 NO CYCLE;
CREATE SEQUENCE client_numero_c_seq AS BIGINT START WITH 1 NO CYCLE;
CREATE SEQUENCE client_numero_o_seq AS BIGINT START WITH 1 NO CYCLE;

-- Append-only allocation history; client_id is attached once and never reassigned.
-- A NULL client_id means reserved by a caller, not a second Client entity.
CREATE TABLE client_numero_reservation (
    numero TEXT PRIMARY KEY,
    prefixe CHAR(1) NOT NULL CHECK (prefixe IN ('V', 'C', 'O')),
    client_id BIGINT REFERENCES client(id),
    origine VARCHAR(20) NOT NULL CHECK (origine IN ('MIGRATION', 'GENERATOR', 'CLIENT_WRITE')),
    reserve_le TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reservation_tx XID8 NOT NULL DEFAULT pg_current_xact_id(),
    affecte_le TIMESTAMPTZ,
    retire_le TIMESTAMPTZ,
    CONSTRAINT ck_reservation_format CHECK (
        numero ~ '^[VCO][0-9]{4,}$' AND left(numero, 1) = prefixe
    ),
    CONSTRAINT ck_reservation_affectation CHECK (
        (client_id IS NULL AND affecte_le IS NULL AND retire_le IS NULL)
        OR (client_id IS NOT NULL AND affecte_le IS NOT NULL)
    )
);
CREATE INDEX ix_reservation_client ON client_numero_reservation(client_id);

-- Observation time at migration, not an invented historical creation date.
INSERT INTO client_numero_reservation(numero, prefixe, client_id, origine, affecte_le)
SELECT numero, left(numero, 1), id, 'MIGRATION', CURRENT_TIMESTAMP FROM client;

SELECT setval('client_numero_v_seq',
    GREATEST(1, COALESCE(max(substring(numero FROM 2)::bigint), 0)),
    COALESCE(max(substring(numero FROM 2)::bigint), 0) > 0)
FROM client_numero_reservation WHERE prefixe = 'V';
SELECT setval('client_numero_c_seq',
    GREATEST(1, COALESCE(max(substring(numero FROM 2)::bigint), 0)),
    COALESCE(max(substring(numero FROM 2)::bigint), 0) > 0)
FROM client_numero_reservation WHERE prefixe = 'C';
SELECT setval('client_numero_o_seq',
    GREATEST(1, COALESCE(max(substring(numero FROM 2)::bigint), 0)),
    COALESCE(max(substring(numero FROM 2)::bigint), 0) > 0)
FROM client_numero_reservation WHERE prefixe = 'O';

CREATE FUNCTION protect_client_number_history() RETURNS TRIGGER LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Client number allocations cannot be deleted' USING ERRCODE = '23514';
    END IF;
    IF NEW.numero IS DISTINCT FROM OLD.numero
        OR NEW.prefixe IS DISTINCT FROM OLD.prefixe
        OR NEW.origine IS DISTINCT FROM OLD.origine
        OR NEW.reserve_le IS DISTINCT FROM OLD.reserve_le
        OR NEW.reservation_tx IS DISTINCT FROM OLD.reservation_tx
        OR (OLD.client_id IS NOT NULL AND (
            NEW.client_id IS DISTINCT FROM OLD.client_id
            OR NEW.affecte_le IS DISTINCT FROM OLD.affecte_le))
        OR (OLD.retire_le IS NOT NULL AND NEW.retire_le IS DISTINCT FROM OLD.retire_le)
    THEN
        RAISE EXCEPTION 'Client number allocations are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_client_number_history
BEFORE UPDATE OR DELETE ON client_numero_reservation
FOR EACH ROW EXECUTE FUNCTION protect_client_number_history();

CREATE FUNCTION reserve_client_number(category TEXT) RETURNS TEXT LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    prefix TEXT;
    counter BIGINT;
    candidate TEXT;
    allocated TEXT;
BEGIN
    prefix := CASE category WHEN 'VIP' THEN 'V' WHEN 'LOCAL' THEN 'C'
                            WHEN 'OCCASIONNEL' THEN 'O' ELSE NULL END;
    IF prefix IS NULL THEN
        RAISE EXCEPTION 'Unsupported client category' USING ERRCODE = '23514';
    END IF;
    LOOP
        counter := nextval(CASE prefix WHEN 'V' THEN 'client_numero_v_seq'::regclass
                                     WHEN 'C' THEN 'client_numero_c_seq'::regclass
                                     ELSE 'client_numero_o_seq'::regclass END);
        -- lpad(..., 4) alone would truncate a five-digit counter.
        candidate := prefix || lpad(counter::text, greatest(4, length(counter::text)), '0');
        INSERT INTO client_numero_reservation(numero, prefixe, origine)
        VALUES (candidate, prefix, 'GENERATOR')
        ON CONFLICT (numero) DO NOTHING RETURNING numero INTO allocated;
        IF FOUND THEN
            RETURN allocated;
        END IF;
    END LOOP;
END;
$$;

-- Track all current numbers even for SQL/JPA writers that do not use the generator.
-- No prefix/category consistency or login constraint is enabled on existing clients/accounts.
CREATE FUNCTION track_client_number() RETURNS TRIGGER LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    allocation client_numero_reservation%ROWTYPE;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.numero IS NOT DISTINCT FROM OLD.numero THEN
        RETURN NEW;
    END IF;
    INSERT INTO client_numero_reservation(numero, prefixe, client_id, origine, affecte_le)
    VALUES (NEW.numero, left(NEW.numero, 1), NEW.id, 'CLIENT_WRITE', CURRENT_TIMESTAMP)
    ON CONFLICT (numero) DO NOTHING;

    SELECT * INTO STRICT allocation FROM client_numero_reservation
    WHERE numero = NEW.numero FOR UPDATE;

    IF allocation.retire_le IS NOT NULL
       OR (allocation.client_id IS NOT NULL AND allocation.client_id <> NEW.id)
       OR (allocation.client_id IS NULL AND allocation.reservation_tx <> pg_current_xact_id())
    THEN
        RAISE EXCEPTION 'Client number already reserved' USING ERRCODE = '23505';
    END IF;
    IF allocation.client_id IS NULL THEN
        UPDATE client_numero_reservation SET client_id = NEW.id, affecte_le = CURRENT_TIMESTAMP
        WHERE numero = NEW.numero;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        UPDATE client_numero_reservation SET retire_le = CURRENT_TIMESTAMP
        WHERE numero = OLD.numero AND client_id = NEW.id AND retire_le IS NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER track_client_number
AFTER INSERT OR UPDATE OF numero ON client
FOR EACH ROW EXECUTE FUNCTION track_client_number();
