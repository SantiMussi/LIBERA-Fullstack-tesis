package com.libera.backend.controller;

import com.libera.backend.domain.enums.ResalePurchaseStatus;
import com.libera.backend.dto.response.ResalePurchaseResponseDTO;
import com.libera.backend.service.ResalePurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Operaciones de backoffice. SecurityConfig restringe /api/v1/admin/** al rol ADMIN. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ResalePurchaseService resalePurchaseService;

    @GetMapping("/purchases")
    public ResponseEntity<List<ResalePurchaseResponseDTO>> getPurchases(
            @RequestParam(required = false) ResalePurchaseStatus status) {
        return ResponseEntity.ok(resalePurchaseService.getAllPurchases(status));
    }

    /**
     * Confirma el check-in a mano (hoteles sin PMS integrado, o resolución de una disputa a favor del vendedor)
     * y libera el pago al vendedor.
     */
    @PostMapping("/purchases/{id}/check-in")
    public ResponseEntity<ResalePurchaseResponseDTO> confirmCheckIn(@PathVariable Long id) {
        return ResponseEntity.ok(resalePurchaseService.confirmCheckInManually(id));
    }
}
