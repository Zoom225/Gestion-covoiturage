package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.entity.enums.CategorieClient;
import com.plateforme.covoiturage.repository.ClientNumberRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientNumberGenerator {
    private final ClientNumberRepository numbers;

    // The future Client/Compte creation service owns the complete write transaction.
    /**
     * Réserve atomiquement un matricule distinct par catégorie dans la transaction de création, sans réutiliser une valeur consommée lors d'un échec.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String reserve(CategorieClient category) {
        return numbers.reserve(Objects.requireNonNull(category, "Client category is required"));
    }
}
