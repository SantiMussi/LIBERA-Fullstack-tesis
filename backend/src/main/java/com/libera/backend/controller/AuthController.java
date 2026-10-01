package com.libera.backend.controller;

import com.libera.backend.dto.request.LoginRequestDTO;
import com.libera.backend.dto.request.RegisterRequestDTO;
import com.libera.backend.dto.response.AuthResponseDTO;
import com.libera.backend.dto.response.UserResponseDTO;
import com.libera.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return new ResponseEntity<>(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(Principal principal) {
        return ResponseEntity.ok(authService.getCurrentUser(Long.valueOf(principal.getName())));
    }
}
