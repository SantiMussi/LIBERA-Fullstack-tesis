package com.libera.backend.dto.response;

import com.libera.backend.domain.enums.ResalePurchaseStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ResalePurchaseResponseDTO {
    private Long id;
    private Long listingId;
    private Long buyerId;
    private String hotelName;
    private String roomType;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Long nights;
    private BigDecimal totalPrice;
    private ResalePurchaseStatus status;
}
