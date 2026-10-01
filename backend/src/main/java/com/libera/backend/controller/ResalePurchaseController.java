package com.libera.backend.controller;

import com.libera.backend.dto.request.ResalePurchaseRequestDTO;
import com.libera.backend.dto.response.ResalePurchaseResponseDTO;
import com.libera.backend.service.ResalePurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class ResalePurchaseController {

    private final ResalePurchaseService resalePurchaseService;

    @PostMapping
    public ResponseEntity<ResalePurchaseResponseDTO> createPurchase(
            @Valid @RequestBody ResalePurchaseRequestDTO request,
            Principal principal) {

        // Extract authenticated user ID from Security Context
        Long userId = Long.valueOf(principal.getName());

        ResalePurchaseResponseDTO response = resalePurchaseService.createPurchase(request, userId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /** Compras del usuario autenticado. */
    @GetMapping("/mine")
    public ResponseEntity<List<ResalePurchaseResponseDTO>> getMyPurchases(Principal principal) {
        return ResponseEntity.ok(resalePurchaseService.getPurchasesByBuyer(Long.valueOf(principal.getName())));
    }

    /** Ventas sobre las publicaciones del usuario autenticado. */
    @GetMapping("/sales")
    public ResponseEntity<List<ResalePurchaseResponseDTO>> getMySales(Principal principal) {
        return ResponseEntity.ok(resalePurchaseService.getSalesBySeller(Long.valueOf(principal.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResalePurchaseResponseDTO> getPurchase(@PathVariable Long id, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(resalePurchaseService.getPurchase(id, Long.valueOf(authentication.getName()), isAdmin));
    }

    /** El comprador reporta un problema (ej. el hotel no reconoce el traspaso): el pago sigue retenido. */
    @PostMapping("/{id}/dispute")
    public ResponseEntity<ResalePurchaseResponseDTO> openDispute(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(resalePurchaseService.openDispute(id, Long.valueOf(principal.getName())));
    }
}
