package com.libera.backend.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ResalePurchaseRequestDTO {

    @NotNull(message = "Listing ID is required")
    private Long listingId;

    @NotNull(message = "Check-in date is required")
    @FutureOrPresent(message = "Check-in date cannot be in the past")
    private LocalDate checkIn;

    @NotNull(message = "Check-out date is required")
    @FutureOrPresent(message = "Check-out date cannot be in the past")
    private LocalDate checkOut;

    @AssertTrue(message = "Check-out must be after check-in")
    public boolean isCheckOutAfterCheckIn() {
        return checkIn == null || checkOut == null || checkOut.isAfter(checkIn);
    }
}
