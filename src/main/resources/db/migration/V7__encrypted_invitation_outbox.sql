-- Outbox chiffrée uniquement ; aucune réécriture des comptes préexistants.
CREATE TABLE invitation_outbox (
    id UUID PRIMARY KEY,
    invitation_id BIGINT NOT NULL UNIQUE REFERENCES invitation(id),
    key_id VARCHAR(64) NOT NULL,
    nonce BYTEA NOT NULL,
    contenu_chiffre BYTEA,
    statut VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    cree_a TIMESTAMP WITH TIME ZONE NOT NULL,
    prochaine_tentative_a TIMESTAMP WITH TIME ZONE NOT NULL,
    tentatives INTEGER NOT NULL DEFAULT 0,
    envoyee_a TIMESTAMP WITH TIME ZONE,
    erreur_code VARCHAR(32),
    CONSTRAINT uk_outbox_nonce UNIQUE(key_id,nonce),
    CONSTRAINT ck_outbox_nonce CHECK (octet_length(nonce)=12),
    CONSTRAINT ck_outbox_statut CHECK (statut IN ('PENDING','SENT','CANCELLED')),
    CONSTRAINT ck_outbox_tentatives CHECK (tentatives >= 0),
    CONSTRAINT ck_outbox_envoi CHECK (
        (statut='SENT' AND envoyee_a IS NOT NULL)
        OR (statut <> 'SENT' AND envoyee_a IS NULL)),
    CONSTRAINT ck_outbox_chiffrement CHECK (
        (statut='PENDING' AND nonce IS NOT NULL AND octet_length(nonce)=12
          AND contenu_chiffre IS NOT NULL AND octet_length(contenu_chiffre)>=16)
        OR (statut <> 'PENDING' AND contenu_chiffre IS NULL))
);
CREATE INDEX ix_outbox_pending ON invitation_outbox(prochaine_tentative_a, cree_a, id)
    WHERE statut='PENDING';
