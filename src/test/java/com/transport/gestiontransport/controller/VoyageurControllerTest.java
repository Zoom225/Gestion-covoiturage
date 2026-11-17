package com.transport.gestiontransport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transport.gestiontransport.config.JwtConfig;
import com.transport.gestiontransport.config.SecurityConfig;
import com.transport.gestiontransport.dto.request.VoyageurLoginRequest; // Added import
import com.transport.gestiontransport.dto.request.VoyageurRequest;
import com.transport.gestiontransport.dto.response.VoyageurResponse;
import com.transport.gestiontransport.entity.Voyageur;
import com.transport.gestiontransport.entity.enums.TypeVoyageur;
import com.transport.gestiontransport.mapper.VoyageurMapper;
import com.transport.gestiontransport.repository.AdministrateurRepository;
import com.transport.gestiontransport.repository.VoyageurRepository;
import com.transport.gestiontransport.service.VoyageurService;
import com.transport.gestiontransport.service.AgenceService;
import com.transport.gestiontransport.service.impl.AdminAuthorizationService;
import com.transport.gestiontransport.service.impl.VoyageurCourantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WebMvcTest(controllers = VoyageurController.class)
@DisplayName("Tests de VoyageurController")
class VoyageurControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VoyageurService voyageurService;
    @MockitoBean
    private AgenceService agenceService;
    @MockitoBean
    private VoyageurMapper voyageurMapper;
    @MockitoBean
    private AdminAuthorizationService adminAuthorizationService;
    @MockitoBean
    private VoyageurCourantService currentVoyageurService;

    @MockitoBean
    private JwtConfig jwtConfig;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private AdministrateurRepository administrateurRepository;
    @MockitoBean
    private VoyageurRepository voyageurRepository;

    private VoyageurRequest voyageurRequest;
    private VoyageurResponse voyageurResponse;
    private Voyageur voyageurEntity; // Added for authenticate mock

    @BeforeEach
    void setUp() {
        voyageurRequest = new VoyageurRequest();
        voyageurRequest.setMatricule("L12345");
        voyageurRequest.setNom("Doe");
        voyageurRequest.setPrenom("John");
        voyageurRequest.setPassword("OnlyForThisTest123!");
        voyageurRequest.setTypeVoyageur(TypeVoyageur.VIP);

        voyageurResponse = new VoyageurResponse();
        voyageurResponse.setId(1L);
        voyageurResponse.setMatricule("L12345");

        voyageurEntity = new Voyageur(); // Initialize voyageurEntity
        voyageurEntity.setId(1L);
        voyageurEntity.setMatricule("L12345");
        voyageurEntity.setTypeVoyageur(TypeVoyageur.LIBRE);
    }

    @Test
    @DisplayName("POST /api/voyageurs - Doit refuser la creation sans authentification")
    void createVoyageur_WithoutAuthentication_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/voyageurs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voyageurRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VOYAGEUR")
    @DisplayName("POST /api/voyageurs - Doit refuser la creation avec un role voyageur")
    void createVoyageur_WithVoyageurRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/voyageurs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voyageurRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refuseUneCreationSansMotDePasseInitialPersonnel() throws Exception {
        voyageurRequest.setPassword(null);
        mockMvc.perform(post("/api/voyageurs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(voyageurRequest)))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(passwordEncoder);
        org.mockito.Mockito.verify(voyageurService, org.mockito.Mockito.never()).create(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/voyageurs - Doit creer un voyageur avec un role admin")
    void createVoyageur_WithAdminRole_ShouldReturnCreated() throws Exception {
        when(voyageurService.create(any(Voyageur.class))).thenReturn(new Voyageur());
        when(voyageurMapper.toEntity(any(VoyageurRequest.class))).thenReturn(new Voyageur());
        when(voyageurMapper.toResponse(any(Voyageur.class))).thenReturn(voyageurResponse);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");

        mockMvc.perform(post("/api/voyageurs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voyageurRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/voyageurs/{id} - Doit mettre à jour un voyageur avec un rôle autorisé")
    void updateVoyageur_WithAuthorizedRole_ShouldReturnOk() throws Exception {
        when(voyageurService.update(anyLong(), any(Voyageur.class))).thenReturn(new Voyageur());
        when(voyageurMapper.toEntity(any(VoyageurRequest.class))).thenReturn(new Voyageur());
        when(voyageurMapper.toResponse(any(Voyageur.class))).thenReturn(voyageurResponse);

        mockMvc.perform(put("/api/voyageurs/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voyageurRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VOYAGEUR")
    @DisplayName("PUT /api/voyageurs/{id} - Doit retourner 403 pour un rôle non autorisé")
    void updateVoyageur_WithUnauthorizedRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(put("/api/voyageurs/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voyageurRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/voyageurs/{id} - Doit supprimer un voyageur avec un rôle autorisé")
    void deleteVoyageur_WithAuthorizedRole_ShouldReturnNoContent() throws Exception {
        doNothing().when(voyageurService).delete(1L);

        mockMvc.perform(delete("/api/voyageurs/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/voyageurs/login - Doit authentifier un voyageur et retourner un token")
    void loginVoyageur_ShouldAuthenticateAndReturnToken() throws Exception {
        VoyageurLoginRequest loginRequest = new VoyageurLoginRequest("L12345", "Voyageur1234!");
        when(voyageurService.authenticate("L12345", "Voyageur1234!")).thenReturn(voyageurEntity);
        when(voyageurMapper.toResponse(any(Voyageur.class))).thenReturn(voyageurResponse);
        when(jwtConfig.generateToken(any(String.class), any(String.class))).thenReturn("mocked-jwt-token");

        mockMvc.perform(post("/api/voyageurs/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricule").value("L12345"))
                .andExpect(jsonPath("$.token").value("mocked-jwt-token"));
    }
}
