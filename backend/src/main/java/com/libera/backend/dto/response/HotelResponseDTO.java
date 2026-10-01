package com.libera.backend.dto.response;

import lombok.Data;

/**
 * Vista pública de un hotel. No expone datos confidenciales (modelo de convenio, PMS, markup, datos fiscales):
 * solo si es socio de LIBERA y si admite Split Booking.
 */
@Data
public class HotelResponseDTO {
    private Long id;
    private String slug;
    private String name;
    private String city;
    private String country;
    private Boolean liberaPartner;
    private Boolean splitBookingAvailable;
}
