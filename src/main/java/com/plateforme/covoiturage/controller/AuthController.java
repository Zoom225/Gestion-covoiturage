package com.plateforme.covoiturage.controller;

import com.plateforme.covoiturage.dto.request.LoginRequest;
import com.plateforme.covoiturage.dto.response.ProfileResponse;
import com.plateforme.covoiturage.dto.response.TokenResponse;
import com.plateforme.covoiturage.security.ComptePrincipal;
import com.plateforme.covoiturage.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me(@AuthenticationPrincipal ComptePrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.me(principal));
    }
}
