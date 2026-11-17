package com.transport.gestiontransport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transport.gestiontransport.config.JwtAuthenticationFilter;
import com.transport.gestiontransport.config.JwtConfig;
import com.transport.gestiontransport.config.SecurityConfig;
import com.transport.gestiontransport.dto.request.ReservationRequest;
import com.transport.gestiontransport.dto.response.ReservationResponse;
import com.transport.gestiontransport.entity.Voyageur;
import com.transport.gestiontransport.entity.Reservation;
import com.transport.gestiontransport.mapper.ReservationMapper;
import com.transport.gestiontransport.repository.AdministrateurRepository;
import com.transport.gestiontransport.repository.VoyageurRepository;
import com.transport.gestiontransport.service.ReservationService;
import com.transport.gestiontransport.service.impl.VoyageurCourantService;
import jakarta.servlet.ServletException;
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

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityConfig.class)
@WebMvcTest(controllers = ReservationController.class)
@DisplayName("Tests de ReservationController")
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationService reservationService;
    @MockitoBean
    private ReservationMapper reservationMapper;
    @MockitoBean
    private VoyageurCourantService currentVoyageurService;

    @MockitoBean
    private JwtConfig jwtConfig;
    @MockitoBean
    private AdministrateurRepository administrateurRepository;
    @MockitoBean
    private VoyageurRepository voyageurRepository;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private ReservationRequest reservationRequest;
    private ReservationResponse reservationResponse;
    private Voyageur currentVoyageur;
    private Reservation reservation;

    @BeforeEach
    void setUp() throws ServletException, IOException {
        // ← mettre doAnswer ici comme toi
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        reservationRequest = new ReservationRequest();
        reservationRequest.setTrajetId(1L);
        reservationRequest.setVoyageurId(1L);
        reservationRequest.setRequesterId(1L);

        reservationResponse = new ReservationResponse();
        reservationResponse.setId(1L);

        currentVoyageur = Voyageur.builder().matricule("G1001").nom("Martin").prenom("Lucas").build();
        currentVoyageur.setId(1L);

        reservation = Reservation.builder().voyageur(currentVoyageur).build();
        reservation.setId(1L);
    }

    @Test
    @WithMockUser(roles = "VOYAGEUR")
    @DisplayName("POST /api/reservations - Doit créer une réservation pour un utilisateur authentifié")
    void createReservation_WithAuthenticatedUser_ShouldReturnCreated() throws Exception {
        when(reservationService.create(anyLong(), anyLong(), anyLong())).thenReturn(new Reservation());
        when(reservationMapper.toResponse(any(Reservation.class))).thenReturn(reservationResponse);
        when(currentVoyageurService.currentVoyageur()).thenReturn(currentVoyageur);

        mockMvc.perform(post("/api/reservations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reservationRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("POST /api/reservations - Doit retourner 401 si non authentifié")
    void createReservation_WithoutAuthentication_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reservationRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VOYAGEUR")
    @DisplayName("PATCH /api/reservations/{id}/cancel - Doit annuler une réservation pour un utilisateur authentifié")

    void cancelReservation_WithAuthenticatedUser_ShouldReturnOk() throws Exception {

        doNothing().when(reservationService).cancelForUser(1L, 1L);
        when(currentVoyageurService.currentVoyageurId()).thenReturn(1L);

        mockMvc.perform(patch("/api/reservations/1/cancel")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/reservations/{id}/cancel - Doit retourner 401 si non authentifié")
    void cancelReservation_WithoutAuthentication_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(patch("/api/reservations/1/cancel")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
