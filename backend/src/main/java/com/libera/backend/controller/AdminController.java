package com.libera.backend.controller;

import com.libera.backend.domain.entity.BookingVoucher;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import com.libera.backend.dto.request.ListingReviewRequestDTO;
import com.libera.backend.dto.response.AdminListingResponseDTO;
import com.libera.backend.dto.response.AdminPurchaseResponseDTO;
import com.libera.backend.dto.response.ContactRequestResponseDTO;
import com.libera.backend.service.ContactService;
import com.libera.backend.service.ListingService;
import com.libera.backend.service.OriginalBookingService;
import com.libera.backend.service.ResalePurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Operaciones de backoffice. SecurityConfig restringe /api/v1/admin/** al rol ADMIN. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ResalePurchaseService resalePurchaseService;
    private final ListingService listingService;
    private final OriginalBookingService originalBookingService;
    private final ContactService contactService;

    // ---------------------------------------------------------------- Compras

    @GetMapping("/purchases")
    public ResponseEntity<List<AdminPurchaseResponseDTO>> getPurchases(
            @RequestParam(required = false) ResalePurchaseStatus status) {
        return ResponseEntity.ok(resalePurchaseService.getAllPurchases(status));
    }

    /**
     * Confirma el check-in a mano (hoteles sin PMS integrado, o resolución de una disputa a favor del vendedor)
     * y libera el pago al vendedor.
     */
    @PostMapping("/purchases/{id}/check-in")
    public ResponseEntity<AdminPurchaseResponseDTO> confirmCheckIn(@PathVariable Long id) {
        return ResponseEntity.ok(resalePurchaseService.confirmCheckInManually(id));
    }

    // ---------------------------------------------------------------- Revisión de publicaciones

    /** Por defecto, las publicaciones que esperan revisión. */
    @GetMapping("/listings")
    public ResponseEntity<List<AdminListingResponseDTO>> getListings(
            @RequestParam(required = false) ListingStatus status) {
        return ResponseEntity.ok(listingService.getListingsForReview(status));
    }

    @PostMapping("/listings/{id}/approve")
    public ResponseEntity<AdminListingResponseDTO> approveListing(@PathVariable Long id) {
        return ResponseEntity.ok(listingService.approveListing(id));
    }

    @PostMapping("/listings/{id}/reject")
    public ResponseEntity<AdminListingResponseDTO> rejectListing(@PathVariable Long id,
                                                                 @Valid @RequestBody ListingReviewRequestDTO request) {
        return ResponseEntity.ok(listingService.rejectListing(id, request.getNote()));
    }

    /** Comprobante que subió el vendedor, para revisarlo antes de aprobar la publicación. */
    @GetMapping("/bookings/{id}/voucher")
    public ResponseEntity<byte[]> getVoucher(@PathVariable Long id) {
        BookingVoucher voucher = originalBookingService.getVoucher(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(voucher.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(voucher.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(voucher.getData());
    }

    // ---------------------------------------------------------------- Contacto

    @GetMapping("/contact-requests")
    public ResponseEntity<List<ContactRequestResponseDTO>> getContactRequests() {
        return ResponseEntity.ok(contactService.getAllRequests());
    }
}
