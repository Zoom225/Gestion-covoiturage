package com.transport.gestiontransport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transport.gestiontransport.config.JwtConfig;
import com.transport.gestiontransport.entity.enums.StatutTrajet;
import com.transport.gestiontransport.entity.enums.TypeTrajet;
import com.transport.gestiontransport.dto.request.CreateTrajetRequest;
import com.transport.gestiontransport.dto.response.TrajetDto;
import com.transport.gestiontransport.repository.AdministrateurRepository;
import com.transport.gestiontransport.repository.VoyageurRepository;
import com.transport.gestiontransport.service.TrajetService;
import com.transport.gestiontransport.service.impl.AdminAuthorizationService;
import com.transport.gestiontransport.service.impl.VoyageurCourantService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Étape 1 : Importer la configuration de sécurité
//@Import(SecurityConfig.class)
// Étape 2 : Cibler le contrôleur
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = TrajetController.class)
@DisplayName("Tests de TrajetController")
class TrajetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrajetService trajetService;
    @MockitoBean
    private AdminAuthorizationService adminAuthorizationService;
    @MockitoBean
    private VoyageurCourantService currentVoyageurService;

    // Étape 3 : Mocker les dépendances de la chaîne de sécurité (JwtAuthenticationFilter)
    @MockitoBean
    private JwtConfig jwtConfig;
    @MockitoBean
    private AdministrateurRepository administrateurRepository;
    @MockitoBean
    private VoyageurRepository voyageurRepository;


    private CreateTrajetRequest validCreateTrajetRequest;
    private TrajetDto trajetDto;

    @BeforeEach
    void setUp() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(10).withNano(0);
        validCreateTrajetRequest = new CreateTrajetRequest(1L, startTime, "PUBLIC",
                com.transport.gestiontransport.entity.enums.VilleBelge.MONS,
                com.transport.gestiontransport.entity.enums.VilleBelge.BRUXELLES, false, null);

        trajetDto = new TrajetDto(
                1L,                                        // id
                1L,                                        // vehiculeId
                "Court Central",                           // vehiculeNom
                "Agence Lyon",                         // agenceNom
                10L,                                       // organisateurId
                "John Doe",                                // organisateurNom
                startTime.toLocalDate(),                   // date
                startTime.toLocalTime(),                   // heureDebut
                startTime.plusMinutes(90).toLocalTime(),   // heureFin
                TypeTrajet.PUBLIC,                          // typeTrajet
                StatutTrajet.PLANIFIE,                      // statut
                1,                                         // nbPassagersActuels
                15.0,                                      // prixParPassager
                null                                       // dateConversionPublic
        );
    }

    @Test
    @WithMockUser(username = "voyageur.test", roles = "LIBRE")
    @DisplayName("POST /api/trajets - Doit créer un trajet pour un rôle autorisé")
    void createTrajet_WithAuthorizedRole_ShouldReturnCreated() throws Exception {
        when(trajetService.createTrajet(any(CreateTrajetRequest.class), anyString())).thenReturn(trajetDto);

        mockMvc.perform(post("/api/trajets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateTrajetRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(trajetDto.id()));
    }




    @Test
    @DisplayName("GET /api/trajets/public - Doit retourner les trajets publics sans authentification")
    void getPublicTrajets_WithoutAuthentication_ShouldReturnOk() throws Exception {
        when(trajetService.searchPublicTrajets(null, null, null)).thenReturn(List.of(trajetDto));

        mockMvc.perform(get("/api/trajets/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(trajetDto.id()));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/trajets - Doit retourner tous les trajets pour un utilisateur authentifié")
    void getAllTrajets_WithAuthenticatedUser_ShouldReturnOk() throws Exception {
        when(trajetService.findAllTrajets()).thenReturn(List.of(trajetDto));
        when(adminAuthorizationService.filterTrajetDtos(any())).thenReturn(List.of(trajetDto));

        mockMvc.perform(get("/api/trajets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(trajetDto.id()));
    }
}
