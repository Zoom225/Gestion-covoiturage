package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.delivery.InvitationPayloadCipher.Encrypted;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InvitationOutboxRepository {
    private final JdbcTemplate jdbc;

    public record Pending(UUID id, long invitationId, String keyId, byte[] nonce, byte[] ciphertext,
                          Instant expiresAt, boolean consumed, boolean revoked) {
        @Override public String toString() { return "Pending[REDACTED]"; }
    }

    public void insert(UUID id, long invitationId, Encrypted content, Instant now) {
        jdbc.update("insert into invitation_outbox(id,invitation_id,key_id,nonce,contenu_chiffre,cree_a,prochaine_tentative_a)"
                        + " values(?,?,?,?,?,?,?)", id, invitationId, content.keyId(), content.nonce(),
                content.ciphertext(), Timestamp.from(now), Timestamp.from(now));
    }

    public Optional<UUID> next(Instant now) {
        return jdbc.query("select id from invitation_outbox where statut='PENDING' and prochaine_tentative_a<=?"
                        + " order by cree_a,id limit 1", (rs, n) -> rs.getObject(1, UUID.class), Timestamp.from(now))
                .stream().findFirst();
    }

    public Optional<Long> compteId(UUID id) {
        return jdbc.query("select i.compte_id from invitation_outbox o join invitation i on i.id=o.invitation_id"
                + " where o.id=?", (rs, n) -> rs.getLong(1), id).stream().findFirst();
    }

    public Optional<Pending> lock(UUID id, Instant now) {
        return jdbc.query("""
                select o.id,o.invitation_id,o.key_id,o.nonce,o.contenu_chiffre,
                       i.expire_a,i.consommee_a,i.revoquee_a
                from invitation_outbox o join invitation i on i.id=o.invitation_id
                where o.id=? and o.statut='PENDING' and o.prochaine_tentative_a<=?
                for update of o skip locked
                """, (rs, n) -> new Pending(rs.getObject("id", UUID.class), rs.getLong("invitation_id"),
                rs.getString("key_id"), rs.getBytes("nonce"), rs.getBytes("contenu_chiffre"),
                rs.getTimestamp("expire_a").toInstant(), rs.getTimestamp("consommee_a") != null,
                rs.getTimestamp("revoquee_a") != null), id, Timestamp.from(now)).stream().findFirst();
    }

    public void sent(UUID id, Instant now) {
        jdbc.update("update invitation_outbox set statut='SENT',envoyee_a=?,contenu_chiffre=null,"
                + "tentatives=tentatives+1,erreur_code=null where id=?", Timestamp.from(now), id);
    }

    public void cancel(UUID id) {
        jdbc.update("update invitation_outbox set statut='CANCELLED',contenu_chiffre=null,"
                + "erreur_code=null where id=?", id);
    }

    public void retry(UUID id, Instant when) {
        jdbc.update("update invitation_outbox set tentatives=tentatives+1,prochaine_tentative_a=?,"
                + "erreur_code='DELIVERY_RETRY' where id=?", Timestamp.from(when), id);
    }

    public void cancelForAccount(Long compteId) {
        jdbc.update("update invitation_outbox set statut='CANCELLED',contenu_chiffre=null,erreur_code=null"
                + " where statut='PENDING' and invitation_id in"
                + " (select id from invitation where compte_id=?)", compteId);
    }
}
