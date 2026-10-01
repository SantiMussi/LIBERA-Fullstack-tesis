package com.libera.backend.controller;

import com.libera.backend.dto.request.OriginalBookingCreateRequestDTO;
import com.libera.backend.dto.response.OriginalBookingResponseDTO;
import com.libera.backend.service.OriginalBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class OriginalBookingController {

    private final OriginalBookingService originalBookingService;

    /** El titular carga su reserva original (paso previo a publicarla). */
    @PostMapping
    public ResponseEntity<OriginalBookingResponseDTO> registerBooking(
            @Valid @RequestBody OriginalBookingCreateRequestDTO request,
            Principal principal) {
        OriginalBookingResponseDTO response = originalBookingService.registerBooking(request, Long.valueOf(principal.getName()));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<OriginalBookingResponseDTO>> getMyBookings(Principal principal) {
        return ResponseEntity.ok(originalBookingService.getBookingsByGuest(Long.valueOf(principal.getName())));
    }
}
