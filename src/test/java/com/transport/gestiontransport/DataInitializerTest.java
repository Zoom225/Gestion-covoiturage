package com.transport.gestiontransport;

import com.transport.gestiontransport.entity.*;
import com.transport.gestiontransport.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {
    @Mock private AgenceRepository agenceRepository;
    @Mock private VehiculeRepository vehiculeRepository;
    @Mock private VoyageurRepository voyageurRepository;
    @Mock private AdministrateurRepository administrateurRepository;
    @Mock private IndisponibiliteServiceRepository indisponibiliteServiceRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private RestrictionRepository restrictionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private DataInitializer dataInitializer;

    @Test
    void run_preserveLesComptesEtPaiementsDeTouteBaseNonVide() {
        when(agenceRepository.count()).thenReturn(1L);
        dataInitializer.run();
        verify(voyageurRepository, never()).save(any());
        verify(voyageurRepository, never()).saveAll(any());
        verify(administrateurRepository, never()).save(any());
        verify(paiementRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void refuseUneBaseMetierMemeVide() {
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://localhost:5440/transportService");
        assertThatThrownBy(() -> dataInitializer.run()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void demonstrationIsoleeNeReserveAucunePlacePourLeChauffeur() {
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://localhost:5440/transport_demo_test");
        ReflectionTestUtils.setField(dataInitializer, "demoPassword", "OnlyForThisTest123!");
        when(passwordEncoder.encode(any())).thenReturn("hash-test");
        when(agenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehiculeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(voyageurRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(restrictionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(agenceRepository.count()).thenReturn(0L, 1L);
        dataInitializer.run();
        dataInitializer.run();
        verify(trajetRepository, times(8)).save(argThat(t -> t.getChauffeur() != null
                && Boolean.TRUE.equals(t.getChauffeur().getPeutProposerTrajet()) && t.getNbPassagersActuels() == 0
                && t.getVilleDepart() != t.getVilleArrivee() && t.getPrixParPassager() == 25.0));
        verify(reservationRepository, never()).save(any());
        verify(paiementRepository, never()).save(any());
        verify(restrictionRepository).save(any());
    }

    @Test
    void demonstrationUtiliseLesTroisComptesTransportEtDesMotsDePasseDistinctsParRole() {
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://localhost:5541/transport_demo_connexions");
        when(passwordEncoder.encode("Admin1234!")).thenReturn("hash-admin");
        when(passwordEncoder.encode("Voyage1234!")).thenReturn("hash-voyageur");
        when(agenceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(vehiculeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(voyageurRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(restrictionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        dataInitializer.run();
        for (String email : new String[]{"admin@transp.com", "admin.lyon@transp.com", "admin.paris@transp.com"}) {
            verify(administrateurRepository).save(argThat(a -> email.equals(a.getEmail()) && "hash-admin".equals(a.getPasswordHash())));
        }
        verify(voyageurRepository, times(5)).save(argThat(v -> "hash-voyageur".equals(v.getPasswordHash())));
        verify(restrictionRepository).save(any());
    }

    @Test
    void refuseLaMiseAJourDesComptesSurLaBaseMetier() {
        when(agenceRepository.count()).thenReturn(1L);
        ReflectionTestUtils.setField(dataInitializer, "updateCredentials", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://localhost:5440/transportService");
        assertThatThrownBy(() -> dataInitializer.run()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(passwordEncoder);
        verify(administrateurRepository, never()).findAll();
    }

    @Test
    void refuseUnDoublonDeMatriculeAvantTouteEcriture() {
        when(agenceRepository.count()).thenReturn(1L);
        ReflectionTestUtils.setField(dataInitializer, "updateCredentials", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://127.0.0.1:5541/transport_schema_copy_test");
        when(administrateurRepository.findAll()).thenReturn(java.util.List.of(
                Administrateur.builder().email("admin@padel.com").build(),
                Administrateur.builder().email("admin.lyon@padel.com").build(),
                Administrateur.builder().email("admin.paris@padel.com").build()));
        Voyageur ancien = Voyageur.builder().matricule("G1001").typeVoyageur(com.transport.gestiontransport.entity.enums.TypeVoyageur.VIP).build();
        ancien.setId(1L);
        Voyageur nouveau = Voyageur.builder().matricule("V1001").typeVoyageur(com.transport.gestiontransport.entity.enums.TypeVoyageur.VIP).build();
        nouveau.setId(2L);
        when(voyageurRepository.findAll()).thenReturn(java.util.List.of(ancien, nouveau));
        assertThatThrownBy(() -> dataInitializer.run()).isInstanceOf(IllegalStateException.class).hasMessageContaining("doublon");
        verify(voyageurRepository, never()).saveAllAndFlush(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void prepareDeuxVehiculesDeDemonstrationSansModifierLesRessourcesHistoriques() {
        when(agenceRepository.count()).thenReturn(1L);
        ReflectionTestUtils.setField(dataInitializer, "prepareVehicles", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://127.0.0.1:5541/transport_schema_copy_test");
        Agence agence = Agence.builder().nom("Agence test").build();
        agence.setId(1L);
        Vehicule historique = Vehicule.builder().nom("Ressource historique").disponible(false).build();
        var vehicules = new java.util.ArrayList<Vehicule>();
        vehicules.add(historique);
        when(agenceRepository.findAll()).thenReturn(java.util.List.of(agence));
        when(vehiculeRepository.findByAgenceId(1L)).thenReturn(vehicules);
        when(vehiculeRepository.save(any())).thenAnswer(i -> {Vehicule v=i.getArgument(0); vehicules.add(v); return v;});
        dataInitializer.run();
        dataInitializer.run();
        verify(vehiculeRepository, times(2)).save(argThat(v -> v.getDisponible() && v.getAgence()==agence));
        assertThat(historique.getDisponible()).isFalse();
        verifyNoInteractions(passwordEncoder);
        verify(administrateurRepository, never()).save(any());
    }

    @Test
    void renommeLesVoituresDeDemonstrationSansRecreationNiChangementDeDisponibilite() {
        when(agenceRepository.count()).thenReturn(1L);
        ReflectionTestUtils.setField(dataInitializer, "prepareVehicles", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://127.0.0.1:5541/transport_schema_copy_test");
        Agence agence = Agence.builder().nom("Agence test").build();
        agence.setId(1L);
        Vehicule clio = Vehicule.builder().nom("Voiture DÉMONSTRATION 1").disponible(false).agence(agence).build();
        clio.setId(10L);
        Vehicule peugeot = Vehicule.builder().nom("Voiture DÉMONSTRATION 2").disponible(true).agence(agence).build();
        peugeot.setId(11L);
        when(agenceRepository.findAll()).thenReturn(java.util.List.of(agence));
        when(vehiculeRepository.findByAgenceId(1L)).thenReturn(java.util.List.of(clio, peugeot));
        dataInitializer.run();
        dataInitializer.run();
        verify(vehiculeRepository).save(clio);
        verify(vehiculeRepository).save(peugeot);
        assertThat(clio.getId()).isEqualTo(10L);
        assertThat(clio.getNom()).isEqualTo("Renault Clio");
        assertThat(clio.getDisponible()).isFalse();
        assertThat(peugeot.getId()).isEqualTo(11L);
        assertThat(peugeot.getNom()).isEqualTo("Peugeot 308");
        assertThat(peugeot.getDisponible()).isTrue();
    }

    @Test
    void refuseLaPreparationDesVehiculesSurLaBaseMetier() {
        when(agenceRepository.count()).thenReturn(1L);
        ReflectionTestUtils.setField(dataInitializer, "prepareVehicles", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://localhost:5440/transportService");
        assertThatThrownBy(() -> dataInitializer.run()).isInstanceOf(IllegalStateException.class);
        verify(vehiculeRepository, never()).save(any());
    }

    @Test
    void renommeLesAgencesTransportSansChangerLeursRattachements() {
        when(agenceRepository.count()).thenReturn(2L);
        ReflectionTestUtils.setField(dataInitializer, "prepareAgencies", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://127.0.0.1:5541/transport_schema_copy_test");
        Agence lyon = Agence.builder().nom("Padel Club Lyon").adresse("Adresse conservée").build();
        lyon.setId(1L);
        Agence paris = Agence.builder().nom("Padel Club Paris").build();
        paris.setId(2L);
        when(agenceRepository.findAll()).thenReturn(java.util.List.of(lyon, paris));
        dataInitializer.run();
        dataInitializer.run();
        verify(agenceRepository).save(lyon);
        verify(agenceRepository).save(paris);
        assertThat(lyon.getNom()).isEqualTo("Agence Transport Élégant");
        assertThat(paris.getNom()).isEqualTo("Agence Transport Design");
        assertThat(lyon.getId()).isEqualTo(1L);
        assertThat(paris.getId()).isEqualTo(2L);
        assertThat(lyon.getAdresse()).isEqualTo("Adresse conservée");
        verify(vehiculeRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void renommeAussiLesAgencesDejaNommeesTransportSansToucherAuxAutres() {
        when(agenceRepository.count()).thenReturn(3L);
        ReflectionTestUtils.setField(dataInitializer, "prepareAgencies", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://127.0.0.1:5541/transport_schema_copy_test");
        Agence elegant = Agence.builder().nom("Agence Transport Lyon").build();
        Agence design = Agence.builder().nom("Agence Transport Paris").build();
        Agence autre = Agence.builder().nom("Agence indépendante").build();
        when(agenceRepository.findAll()).thenReturn(java.util.List.of(elegant, design, autre));
        dataInitializer.run();
        dataInitializer.run();
        assertThat(elegant.getNom()).isEqualTo("Agence Transport Élégant");
        assertThat(design.getNom()).isEqualTo("Agence Transport Design");
        assertThat(autre.getNom()).isEqualTo("Agence indépendante");
        verify(agenceRepository).save(elegant);
        verify(agenceRepository).save(design);
        verify(agenceRepository, never()).save(autre);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void refuseLeRenommageDesAgencesSurLaBaseMetier() {
        when(agenceRepository.count()).thenReturn(1L);
        ReflectionTestUtils.setField(dataInitializer, "prepareAgencies", true);
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://localhost:5440/transportService");
        assertThatThrownBy(() -> dataInitializer.run()).isInstanceOf(IllegalStateException.class);
        verify(agenceRepository, never()).save(any());
    }

    @Test
    void refuseUnMotDePasseDepassantLaLimiteBcryptAvantTouteInsertion() {
        ReflectionTestUtils.setField(dataInitializer, "databaseUrl", "jdbc:postgresql://localhost:5440/transport_demo_test");
        ReflectionTestUtils.setField(dataInitializer, "demoPassword", "a".repeat(73));
        assertThatThrownBy(() -> dataInitializer.run()).isInstanceOf(IllegalStateException.class).hasMessageContaining("72 octets");
        verify(agenceRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
