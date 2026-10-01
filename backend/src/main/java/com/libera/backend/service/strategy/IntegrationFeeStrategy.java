package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class IntegrationFeeStrategy implements FeeCalculationStrategy {

    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% total platform fee
    private static final BigDecimal BUYER_FEE_PERCENTAGE = new BigDecimal("0.05"); // 5% fee for buyer

    @Override
    public FeeCalculationResult calculateFees(BigDecimal totalPaidByBuyer, Hotel hotel) {
        // Base price calculation (total = base + buyerFee) -> base = total / (1 + BUYER_FEE)
        BigDecimal basePrice = totalPaidByBuyer.divide(BigDecimal.ONE.add(BUYER_FEE_PERCENTAGE), 2, RoundingMode.HALF_UP);
        BigDecimal buyerFee = totalPaidByBuyer.subtract(basePrice);
        
        // Seller pays a percentage of the base price
        BigDecimal sellerFee = basePrice.multiply(PLATFORM_FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerPayout = basePrice.subtract(sellerFee);
        
        // In INTEGRATION, hotel gets a revenue share based on their markup fee percentage from the total fees
        BigDecimal totalFeesCollected = buyerFee.add(sellerFee);
        BigDecimal hotelSharePercentage = hotel.getMarkupFee() != null ? hotel.getMarkupFee().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal hotelRevenueShare = totalFeesCollected.multiply(hotelSharePercentage).setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal liberaNetRevenue = totalFeesCollected.subtract(hotelRevenueShare);

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
        return PartnershipModel.INTEGRATION;
    }
}
