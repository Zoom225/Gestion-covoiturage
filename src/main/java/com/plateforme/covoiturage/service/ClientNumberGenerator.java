package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.repository.ClientNumberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

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
