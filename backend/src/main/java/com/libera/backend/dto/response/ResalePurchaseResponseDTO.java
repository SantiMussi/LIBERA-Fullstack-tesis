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
    /** Precio de las noches compradas. */
    private BigDecimal totalPrice;
    /** Garantía de Traspaso que paga el comprador encima del precio. */
    private BigDecimal buyerFee;
    /** Lo que pagó el comprador en total: totalPrice + buyerFee. */
    private BigDecimal totalPaid;
    private ResalePurchaseStatus status;
}
