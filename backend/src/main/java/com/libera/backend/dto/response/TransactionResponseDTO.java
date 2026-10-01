package com.libera.backend.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionResponseDTO {
    private Long id;
    private Long resalePurchaseId;
    private BigDecimal totalPaidByBuyer;
    private BigDecimal buyerFeeAmount;
    private BigDecimal sellerFeeAmount;
    private BigDecimal sellerPayoutAmount;
    private BigDecimal hotelRevenueShareAmount;
    private BigDecimal liberaNetRevenue;
}
