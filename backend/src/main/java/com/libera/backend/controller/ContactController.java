package com.libera.backend.controller;

import com.libera.backend.dto.request.ContactRequestDTO;
import com.libera.backend.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Formulario "Agendar demo" de la vista Hoteles. Público: lo completa un hotel sin cuenta. */
@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @PostMapping
    public ResponseEntity<Void> createRequest(@Valid @RequestBody ContactRequestDTO request) {
        contactService.createRequest(request);
        return ResponseEntity.status(201).build();
    }
}
