package com.libera.backend.dto.request;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Filtros opcionales del catálogo público. Un campo null significa "sin filtro". */
@Data
@Builder
public class ListingSearchCriteria {
    private String city;
    private Long hotelId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private BigDecimal maxPrice;
}
