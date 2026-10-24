-- No backfill or activation of existing accounts. The contact is not a login for CLIENT.
ALTER TABLE compte ADD COLUMN invitation_email VARCHAR(254);
ALTER TABLE compte ADD CONSTRAINT ck_compte_invitation_email CHECK (
    invitation_email IS NULL OR (
        invitation_email = lower(invitation_email)
        AND invitation_email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    )
);
-- No UNIQUE constraint: a contact address is not an account identity.
