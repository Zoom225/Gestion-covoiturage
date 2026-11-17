package com.transport.gestiontransport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transport.gestiontransport.config.JwtAuthenticationFilter;
import com.transport.gestiontransport.config.JwtConfig;
import com.transport.gestiontransport.dto.request.IndisponibiliteServiceRequest;
import com.transport.gestiontransport.entity.Administrateur;
import com.transport.gestiontransport.entity.IndisponibiliteService;
import com.transport.gestiontransport.entity.Agence;
import com.transport.gestiontransport.entity.enums.TypeAdministrateur;
import com.transport.gestiontransport.repository.AdministrateurRepository;
import com.transport.gestiontransport.repository.IndisponibiliteServiceRepository;
import com.transport.gestiontransport.repository.VoyageurRepository;
import com.transport.gestiontransport.service.AgenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = IndisponibiliteServiceController.class)
@DisplayName("Tests de IndisponibiliteServiceController")
class IndisponibiliteServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IndisponibiliteServiceRepository indisponibiliteServiceRepository;

    @MockitoBean
    private AgenceService agenceService;

    @MockitoBean
    private AdministrateurRepository administrateurRepository;

    @MockitoBean
    private VoyageurRepository voyageurRepository;

    @MockitoBean
    private JwtConfig jwtConfig;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private Agence agenceLyon;
    private Agence agenceParis;

    @BeforeEach
    void setUp() {
        agenceLyon = Agence.builder().nom("Agence Lyon").build();
        agenceLyon.setId(1L);
        agenceParis = Agence.builder().nom("Agence Paris").build();
        agenceParis.setId(2L);
    }

    @Test
    @WithMockUser(username = "admin@transport.com", roles = "ADMIN")
    @DisplayName("Un admin GLOBAL peut créer une indisponibilité globale")
    void globalAdminCanCreateGlobalClosingDay() throws Exception {
        when(administrateurRepository.findByEmail("admin@transport.com"))
                .thenReturn(Optional.of(admin(TypeAdministrateur.GLOBAL, null)));
        when(indisponibiliteServiceRepository.save(any(IndisponibiliteService.class))).thenAnswer(invocation -> {
            IndisponibiliteService jour = invocation.getArgument(0);
            jour.setId(10L);
            return jour;
        });

        IndisponibiliteServiceRequest request = IndisponibiliteServiceRequest.builder()
                .date(LocalDate.of(2026, 12, 25))
                .raison("Noël")
                .global(true)
                .build();

        mockMvc.perform(post("/api/indisponibilites-service")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.global").value(true));
    }

    @Test
    @WithMockUser(username = "admin.lyon@transport.com", roles = "ADMIN")
    @DisplayName("Un admin AGENCE ne peut pas créer une indisponibilité globale")
    void agenceAdminCannotCreateGlobalClosingDay() throws Exception {
        when(administrateurRepository.findByEmail("admin.lyon@transport.com"))
                .thenReturn(Optional.of(admin(TypeAdministrateur.AGENCE, agenceLyon)));

        IndisponibiliteServiceRequest request = IndisponibiliteServiceRequest.builder()
                .date(LocalDate.of(2026, 12, 25))
                .raison("Noël")
                .global(true)
                .build();

        mockMvc.perform(post("/api/indisponibilites-service")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Seul un admin GLOBAL peut créer une indisponibilité globale."));
    }

    @Test
    @WithMockUser(username = "admin.lyon@transport.com", roles = "ADMIN")
    @DisplayName("Un admin AGENCE peut créer une indisponibilité sur son agence")
    void agenceAdminCanCreateOwnAgenceClosingDay() throws Exception {
        when(administrateurRepository.findByEmail("admin.lyon@transport.com"))
                .thenReturn(Optional.of(admin(TypeAdministrateur.AGENCE, agenceLyon)));
        when(agenceService.getById(1L)).thenReturn(agenceLyon);
        when(indisponibiliteServiceRepository.save(any(IndisponibiliteService.class))).thenAnswer(invocation -> {
            IndisponibiliteService jour = invocation.getArgument(0);
            jour.setId(11L);
            return jour;
        });

        IndisponibiliteServiceRequest request = IndisponibiliteServiceRequest.builder()
                .date(LocalDate.of(2026, 7, 14))
                .raison("Maintenance")
                .global(false)
                .agenceId(1L)
                .build();

        mockMvc.perform(post("/api/indisponibilites-service")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agenceId").value(1L));
    }

    @Test
    @WithMockUser(username = "admin.lyon@transport.com", roles = "ADMIN")
    @DisplayName("Un admin AGENCE ne peut pas supprimer une indisponibilité globale")
    void agenceAdminCannotDeleteGlobalClosingDay() throws Exception {
        when(administrateurRepository.findByEmail("admin.lyon@transport.com"))
                .thenReturn(Optional.of(admin(TypeAdministrateur.AGENCE, agenceLyon)));
        when(indisponibiliteServiceRepository.findById(10L))
                .thenReturn(Optional.of(IndisponibiliteService.builder().global(true).build()));

        mockMvc.perform(delete("/api/indisponibilites-service/10").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Seul un admin GLOBAL peut supprimer une indisponibilité globale."));
    }

    @Test
    @WithMockUser(username = "admin.lyon@transport.com", roles = "ADMIN")
    @DisplayName("Un admin AGENCE ne peut pas supprimer une indisponibilité d'un autre agence")
    void agenceAdminCannotDeleteOtherAgenceClosingDay() throws Exception {
        when(administrateurRepository.findByEmail("admin.lyon@transport.com"))
                .thenReturn(Optional.of(admin(TypeAdministrateur.AGENCE, agenceLyon)));
        when(indisponibiliteServiceRepository.findById(12L))
                .thenReturn(Optional.of(IndisponibiliteService.builder().global(false).agence(agenceParis).build()));

        mockMvc.perform(delete("/api/indisponibilites-service/12").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Un admin AGENCE ne peut agir que sur sa propre agence."));
    }

    @Test
    @WithMockUser(username = "admin@transport.com", roles = "ADMIN")
    @DisplayName("Un admin GLOBAL peut supprimer une indisponibilité")
    void globalAdminCanDeleteClosingDay() throws Exception {
        when(administrateurRepository.findByEmail("admin@transport.com"))
                .thenReturn(Optional.of(admin(TypeAdministrateur.GLOBAL, null)));
        IndisponibiliteService jour = IndisponibiliteService.builder().global(true).build();
        when(indisponibiliteServiceRepository.findById(10L)).thenReturn(Optional.of(jour));
        doNothing().when(indisponibiliteServiceRepository).delete(jour);

        mockMvc.perform(delete("/api/indisponibilites-service/10").with(csrf()))
                .andExpect(status().isNoContent());
    }

    private Administrateur admin(TypeAdministrateur type, Agence agence) {
        return Administrateur.builder()
                .typeAdministrateur(type)
                .agence(agence)
                .build();
    }
}
