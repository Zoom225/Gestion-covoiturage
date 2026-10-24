-- DEC-COMPTES-003, lot 2. No existing account, number, ID or hash is rewritten.
-- All writers are excluded between the audit and creation of the constraints.
LOCK TABLE client, compte IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM compte GROUP BY lower(identifiant) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V4 blocked: normalized login collisions require an approved mapping'
            USING ERRCODE = '23514';
    END IF;
    IF EXISTS (SELECT 1 FROM client WHERE numero !~ '^[VCO][0-9]{4,}$') THEN
        RAISE EXCEPTION 'V4 blocked: noncanonical client numbers require an approved mapping'
            USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM compte WHERE role = 'ADMIN'
        AND (identifiant <> lower(identifiant)
             OR identifiant !~ '^[^[:space:]@]+@[^[:space:]@]+$')
    ) THEN
        RAISE EXCEPTION 'V4 blocked: administrator logins require approved canonical email addresses'
            USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM compte c JOIN client cl ON cl.id = c.client_id
        WHERE c.role = 'CLIENT' AND c.identifiant IS DISTINCT FROM cl.numero
    ) THEN
        RAISE EXCEPTION 'V4 blocked: client logins require an approved number mapping'
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE UNIQUE INDEX uk_compte_identifiant_casefold ON compte(lower(identifiant));

ALTER TABLE client ADD CONSTRAINT ck_client_numero_canonique
    CHECK (numero ~ '^[VCO][0-9]{4,}$');
-- This referenced key must be non-deferrable; the referencing FK is deferred.
ALTER TABLE client ADD CONSTRAINT uk_client_id_numero UNIQUE (id, numero);

ALTER TABLE compte ADD CONSTRAINT ck_compte_identifiant_canonique CHECK (
    (role = 'ADMIN' AND identifiant = lower(identifiant)
        AND identifiant ~ '^[^[:space:]@]+@[^[:space:]@]+$')
    OR (role = 'CLIENT' AND identifiant ~ '^[VCO][0-9]{4,}$')
);
-- MATCH SIMPLE skips ADMIN rows (client_id is NULL), as already enforced by V2.
-- No cascading login update: the future authorized service must update both rows.
ALTER TABLE compte ADD CONSTRAINT fk_compte_client_numero
    FOREIGN KEY (client_id, identifiant) REFERENCES client(id, numero)
    MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
    DEFERRABLE INITIALLY DEFERRED;
