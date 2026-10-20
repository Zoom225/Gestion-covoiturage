package com.plateforme.covoiturage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.flywaydb.core.Flyway;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.repository.*;
import static org.assertj.core.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CovoiturageApplicationTests {

	@Autowired Flyway flyway;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager entityManager;
	@Autowired ZoneRepository zones;
	@Autowired ClientRepository clients;
	@Autowired VehiculeRepository vehicules;

	@Test
	void contextLoads() {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
		assertThat(flyway.migrate().migrationsExecuted).isZero();
		assertThat(jdbc.queryForObject("select version()", String.class)).contains("PostgreSQL 16.6");
	}

	@Test
	@Transactional
	void referentialsRoundTripThroughJpa() {
		Zone zone = zones.saveAndFlush(new Zone("Bruxelles"));
		Client client = clients.saveAndFlush(new Client("C0001", CategorieClient.LOCAL, true, zone));
		Vehicule vehicule = vehicules.saveAndFlush(new Vehicule(zone));
		entityManager.clear();
		assertThat(clients.findById(client.getId()).orElseThrow().getZone().getNom()).isEqualTo("Bruxelles");
		assertThat(vehicules.findById(vehicule.getId()).orElseThrow().getZone().getId()).isEqualTo(zone.getId());
	}

	@Test
	@Transactional
	void rejectsDuplicateClientNumber() {
		clients.saveAndFlush(new Client("V0001", CategorieClient.VIP, true, null));
		assertThatThrownBy(() -> clients.saveAndFlush(new Client("V0001", CategorieClient.VIP, true, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Transactional
	void rejectsLocalClientWithoutZone() {
		assertThatThrownBy(() -> clients.saveAndFlush(new Client("C0001", CategorieClient.LOCAL, true, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Transactional
	void rejectsUnknownCategory() {
		assertThatThrownBy(() -> jdbc.update("insert into client(numero, categorie, actif) values ('X0001', 'UNKNOWN', true)"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Transactional
	void rejectsVehicleWithUnknownZone() {
		assertThatThrownBy(() -> jdbc.update("insert into vehicule(zone_id) values (-1)"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Transactional
	void rejectsDeletionOfReferencedZone() {
		Zone zone = zones.saveAndFlush(new Zone("Namur"));
		vehicules.saveAndFlush(new Vehicule(zone));
		assertThatThrownBy(() -> jdbc.update("delete from zone where id = ?", zone.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
