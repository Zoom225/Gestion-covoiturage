package com.plateforme.covoiturage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class BootstrapContextIntegrationTests {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Test void operatorContextStartsWithoutHttpOrAutomaticAccountCreation(){
        assertThat(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from compte",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from invitation",Integer.class)).isZero();
    }
}
