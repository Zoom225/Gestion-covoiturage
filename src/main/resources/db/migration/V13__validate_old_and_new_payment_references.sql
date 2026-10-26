-- Strengthen the existing deferred proofs without rewriting V1-V12 or payment data.
-- Refuse pre-existing inconsistent history instead of silently reconciling it.
LOCK TABLE paiement_place, reservation, dette_trajet, paiement_dette IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM reservation r
        WHERE (r.statut = 'CONFIRMEE') <>
              EXISTS (SELECT 1 FROM paiement_place p WHERE p.reservation_id = r.id)
    ) OR EXISTS (
        SELECT 1 FROM dette_trajet d
        WHERE d.regle <> (SELECT coalesce(sum(p.montant), 0)
                         FROM paiement_dette p WHERE p.dette_id = d.id)
    ) THEN
        RAISE EXCEPTION 'Approved reconciliation required before payment integrity upgrade'
            USING ERRCODE = '23514';
    END IF;
END $$;

-- The four V12 constraint triggers remain DEFERRABLE INITIALLY DEFERRED.
-- UPDATE must validate both sides of a reassignment against the final transaction state.
CREATE OR REPLACE FUNCTION verifier_paiement_confirmation() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    affected bigint[];
    booking bigint;
    state varchar(12);
    paid boolean;
BEGIN
    IF TG_TABLE_NAME = 'reservation' THEN
        IF TG_OP = 'INSERT' THEN affected := ARRAY[NEW.id];
        ELSIF TG_OP = 'DELETE' THEN affected := ARRAY[OLD.id];
        ELSE affected := ARRAY[OLD.id, NEW.id];
        END IF;
    ELSE
        IF TG_OP = 'INSERT' THEN affected := ARRAY[NEW.reservation_id];
        ELSIF TG_OP = 'DELETE' THEN affected := ARRAY[OLD.reservation_id];
        ELSE affected := ARRAY[OLD.reservation_id, NEW.reservation_id];
        END IF;
    END IF;

    FOR booking IN SELECT DISTINCT id FROM unnest(affected) AS refs(id) ORDER BY id LOOP
        SELECT statut INTO state FROM reservation WHERE id = booking FOR UPDATE;
        IF NOT FOUND THEN CONTINUE; END IF;
        SELECT EXISTS (SELECT 1 FROM paiement_place WHERE reservation_id = booking) INTO paid;
        IF (state = 'CONFIRMEE') <> paid THEN
            RAISE EXCEPTION 'Payment and confirmation must be atomic' USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION verifier_reglement_dette() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    affected bigint[];
    debt bigint;
    total numeric;
    settled numeric;
BEGIN
    IF TG_TABLE_NAME = 'dette_trajet' THEN
        IF TG_OP = 'INSERT' THEN affected := ARRAY[NEW.id];
        ELSIF TG_OP = 'DELETE' THEN affected := ARRAY[OLD.id];
        ELSE affected := ARRAY[OLD.id, NEW.id];
        END IF;
    ELSE
        IF TG_OP = 'INSERT' THEN affected := ARRAY[NEW.dette_id];
        ELSIF TG_OP = 'DELETE' THEN affected := ARRAY[OLD.dette_id];
        ELSE affected := ARRAY[OLD.dette_id, NEW.dette_id];
        END IF;
    END IF;

    FOR debt IN SELECT DISTINCT id FROM unnest(affected) AS refs(id) ORDER BY id LOOP
        SELECT regle INTO settled FROM dette_trajet WHERE id = debt FOR UPDATE;
        IF NOT FOUND THEN CONTINUE; END IF;
        SELECT coalesce(sum(montant), 0) INTO total FROM paiement_dette WHERE dette_id = debt;
        IF total <> settled THEN
            RAISE EXCEPTION 'Debt balance must match settlements' USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NULL;
END $$;
