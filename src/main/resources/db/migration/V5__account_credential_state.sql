-- Lot 3C.1 : aucun compte, secret ou mot de passe de secours n'est créé.
-- Refuser un hash préexistant incompatible sans afficher sa valeur ni le réécrire.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM compte
        WHERE password_hash IS NULL
           OR password_hash !~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'
    ) THEN
        RAISE EXCEPTION 'V5: hashes existants incompatibles; audit et décision explicite requis';
    END IF;
END $$;

ALTER TABLE compte
    ADD COLUMN etat_identifiants VARCHAR(10) NOT NULL DEFAULT 'INITIALISE',
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE compte
    ADD CONSTRAINT ck_compte_etat_identifiants CHECK (
        (etat_identifiants = 'INVITE' AND password_hash IS NULL)
        OR (etat_identifiants = 'INITIALISE' AND password_hash IS NOT NULL
            AND password_hash ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$')
    );
