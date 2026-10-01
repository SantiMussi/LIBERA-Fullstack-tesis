package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ConvenioFeeStrategy implements FeeCalculationStrategy {

    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("0.10"); // 10% fee for Convenio
    
    @Override
    public FeeCalculationResult calculateFees(BigDecimal totalPaidByBuyer, Hotel hotel) {
        // In CONVENIO, we might just charge a flat fee to the seller, no buyer fee, no hotel rev share
        BigDecimal buyerFee = BigDecimal.ZERO;
        BigDecimal sellerFee = totalPaidByBuyer.multiply(PLATFORM_FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerPayout = totalPaidByBuyer.subtract(sellerFee);
        
        BigDecimal hotelRevenueShare = BigDecimal.ZERO; // No revenue share in Convenio
        BigDecimal liberaNetRevenue = sellerFee;

        return FeeCalculationResult.builder()
                .buyerFeeAmount(buyerFee)
                .sellerFeeAmount(sellerFee)
                .sellerPayoutAmount(sellerPayout)
                .hotelRevenueShareAmount(hotelRevenueShare)
                .liberaNetRevenue(liberaNetRevenue)
                .build();
    }

    @Override
    public PartnershipModel getSupportedModel() {
        return PartnershipModel.CONVENIO;
    }
}
