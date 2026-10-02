package com.libera.backend.controller;

import com.libera.backend.dto.request.ListingCreateRequestDTO;
import com.libera.backend.dto.request.ListingSearchCriteria;
import com.libera.backend.dto.response.ListingResponseDTO;
import com.libera.backend.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @PostMapping
    public ResponseEntity<ListingResponseDTO> createListing(
            @Valid @RequestBody ListingCreateRequestDTO request,
            Principal principal) {

        // Extract authenticated user ID from Security Context
        Long userId = Long.valueOf(principal.getName());

        ListingResponseDTO response = listingService.createListing(request, userId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /** Catálogo público: solo publicaciones con noches disponibles. Todos los filtros son opcionales. */
    @GetMapping
    public ResponseEntity<List<ListingResponseDTO>> searchListings(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) BigDecimal maxPrice) {
        ListingSearchCriteria criteria = ListingSearchCriteria.builder()
                .city(city)
                .hotelId(hotelId)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .maxPrice(maxPrice)
                .build();
        return ResponseEntity.ok(listingService.searchListings(criteria));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ListingResponseDTO>> getMyListings(Principal principal) {
        return ResponseEntity.ok(listingService.getListingsBySeller(Long.valueOf(principal.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponseDTO> getListing(@PathVariable Long id, Authentication authentication) {
        // Endpoint público: sin sesión, Spring entrega un usuario anónimo (sin id)
        boolean loggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        Long viewerId = loggedIn ? Long.valueOf(authentication.getName()) : null;
        boolean isAdmin = loggedIn && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(listingService.getListing(id, viewerId, isAdmin));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ListingResponseDTO> cancelListing(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(listingService.cancelListing(id, Long.valueOf(principal.getName())));
    }
}
