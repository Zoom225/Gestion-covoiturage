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
	@Autowired javax.sql.DataSource dataSource;
	@Autowired org.springframework.core.env.Environment environment;

	@Test
	void documentationIsDisabledByDefault() {
		assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
		assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
	}

	@Test
	void upgradesV1WithoutChangingExistingReferentials() {
		// Schéma isolé de la même base éphémère pour simuler une installation du lot 1.
		var versionOne = Flyway.configure().dataSource(dataSource).schemas("upgrade_test")
			.defaultSchema("upgrade_test").target("1").load();
		versionOne.migrate();
		jdbc.update("insert into upgrade_test.zone(id, nom) values (1, 'Zone existante')");
		jdbc.update("insert into upgrade_test.client(numero, categorie, actif, zone_id) values ('C0001', 'LOCAL', true, 1)");
		var versionTwo = Flyway.configure().dataSource(dataSource).schemas("upgrade_test")
			.defaultSchema("upgrade_test").target("2").load();
		assertThat(versionTwo.migrate().migrationsExecuted).isEqualTo(1);
		assertThat(versionTwo.validateWithResult().validationSuccessful).isTrue();
		assertThat(jdbc.queryForObject("select numero from upgrade_test.client", String.class)).isEqualTo("C0001");
		assertThat(jdbc.queryForObject("select count(*) from upgrade_test.compte", Long.class)).isZero();
	}

	@Test
	void contextLoads() {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
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
