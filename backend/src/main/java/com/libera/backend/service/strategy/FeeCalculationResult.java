package com.libera.backend.service.strategy;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FeeCalculationResult {
    private BigDecimal buyerFeeAmount;
    private BigDecimal sellerFeeAmount;
    private BigDecimal sellerPayoutAmount;
    private BigDecimal hotelRevenueShareAmount;
    private BigDecimal liberaNetRevenue;
}
