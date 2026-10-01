package com.libera.backend.dto.response;

import java.time.LocalDate;

public record DateRangeDTO(LocalDate checkIn, LocalDate checkOut) {
}
