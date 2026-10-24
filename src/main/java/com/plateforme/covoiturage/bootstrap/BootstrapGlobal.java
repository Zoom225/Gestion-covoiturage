package com.plateforme.covoiturage.bootstrap;

import com.plateforme.covoiturage.CovoiturageApplication;
import com.plateforme.covoiturage.service.CompteService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/**
 * Point d'entrée opérateur séparé, jamais découvert comme runner ou endpoint.
 * Refuse toute exécution sans opt-in explicite ; aucun identifiant par défaut.
 */
public final class BootstrapGlobal {
    private BootstrapGlobal() {}
    public static void main(String[] args) {
        if (!"true".equals(System.getenv("DRIVECONNECT_BOOTSTRAP_ENABLED"))) {
            throw new IllegalStateException("Amorçage opérateur non activé");
        }
        String email=System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        if (email==null || email.isBlank()) throw new IllegalStateException("Adresse opérateur requise");
        var app=new SpringApplication(CovoiturageApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        // Le processus ponctuel ne livre pas de message pendant la transaction d'amorçage.
        try (var context=app.run("--spring.main.web-application-type=none", "--driveconnect.delivery.enabled=false")) {
            context.getBean(CompteService.class).bootstrapGlobal(email);
        }
    }
}
