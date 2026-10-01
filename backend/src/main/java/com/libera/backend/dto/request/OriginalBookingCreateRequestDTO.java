package com.libera.backend.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OriginalBookingCreateRequestDTO {

    @NotNull(message = "Hotel ID is required")
    private Long hotelId;

    @NotBlank(message = "PMS confirmation code is required")
    @Size(max = 100)
    private String pmsConfirmationCode;

    @NotNull(message = "Check-in date is required")
    @FutureOrPresent(message = "Check-in date cannot be in the past")
    private LocalDate checkIn;

    @NotNull(message = "Check-out date is required")
    private LocalDate checkOut;

    @NotBlank(message = "Room type is required")
    @Size(max = 100)
    private String roomType;

    @NotNull(message = "Total amount paid is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Amount must have at most 8 integer digits and 2 decimals")
    private BigDecimal totalAmountPaid;

    @AssertTrue(message = "Check-out must be after check-in")
    public boolean isCheckOutAfterCheckIn() {
        return checkIn == null || checkOut == null || checkOut.isAfter(checkIn);
    }
}
