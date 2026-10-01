package com.libera.backend.controller;

import com.libera.backend.dto.response.HotelResponseDTO;
import com.libera.backend.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    /** Buscador de hoteles (paso "Tu hotel" del wizard de venta). */
    @GetMapping
    public ResponseEntity<List<HotelResponseDTO>> searchHotels(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(hotelService.searchHotels(q, city));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HotelResponseDTO> getHotel(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getHotel(id));
    }
}
