package com.libera.backend.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OriginalBookingResponseDTO {
    private Long id;
    private Long hotelId;
    private String hotelName;
    private Long originalGuestId;
    private String pmsConfirmationCode;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Long nights;
    private String roomType;
    private BigDecimal totalAmountPaid;
    private Boolean isLiberaRate;
    /** Si el hotel admite Split Booking (Modelo Integración). */
    private Boolean splitBookingAvailable;
    /** Si el titular ya subió el comprobante. */
    private Boolean hasVoucher;
    /** Estado de la publicación vigente de esta reserva (null si nunca se publicó o se canceló). */
    private String listingStatus;
}
