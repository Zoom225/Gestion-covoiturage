package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.enums.CategorieClient;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClientNumberRepository {
    private final JdbcTemplate jdbc;

    public String reserve(CategorieClient category) {
        return jdbc.queryForObject("select reserve_client_number(?)", String.class, category.name());
    }
}
