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
}
