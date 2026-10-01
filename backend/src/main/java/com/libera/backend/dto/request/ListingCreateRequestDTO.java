package com.libera.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ListingCreateRequestDTO {

    @NotNull(message = "Original booking ID is required")
    private Long originalBookingId;

    @NotNull(message = "Listed total price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimals")
    private BigDecimal listedTotalPrice;

    @NotNull(message = "Allows split booking is required")
    private Boolean allowsSplitBooking;
}
