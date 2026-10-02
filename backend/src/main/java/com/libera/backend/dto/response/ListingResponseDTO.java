package com.libera.backend.dto.response;

import com.libera.backend.domain.enums.ListingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ListingResponseDTO {
    private Long id;
    private Long originalBookingId;
    private Long sellerId;
    private Long hotelId;
    private String hotelSlug;
    private String hotelName;
    private String hotelCity;
    private String hotelCountry;
    private String roomType;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Long nights;
    /** Lo que pagó el titular originalmente (el "antes" del precio tachado). */
    private BigDecimal originalPrice;
    private BigDecimal listedTotalPrice;
    private BigDecimal discountPercentage;
    private Boolean allowsSplitBooking;
    private ListingStatus status;
    /** Motivo del rechazo, si un administrador la rechazó. */
    private String reviewNote;
    /** Rangos de noches ya vendidos (relevante para Split Booking). */
    private List<DateRangeDTO> soldRanges;
}
