package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FeeStrategyFactory {

    private final Map<PartnershipModel, FeeCalculationStrategy> strategies;

    public FeeStrategyFactory(List<FeeCalculationStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(FeeCalculationStrategy::getSupportedModel, Function.identity()));
    }

    public FeeCalculationStrategy getStrategy(PartnershipModel model) {
        FeeCalculationStrategy strategy = strategies.get(model);
        if (strategy == null) {
            // Fallback or default strategy if NONE or unknown
            return new FeeCalculationStrategy() {
                @Override
                public FeeCalculationResult calculateFees(java.math.BigDecimal totalPaidByBuyer, Hotel hotel) {
                    return FeeCalculationResult.builder()
                            .buyerFeeAmount(java.math.BigDecimal.ZERO)
                            .sellerFeeAmount(java.math.BigDecimal.ZERO)
                            .sellerPayoutAmount(totalPaidByBuyer)
                            .hotelRevenueShareAmount(java.math.BigDecimal.ZERO)
                            .liberaNetRevenue(java.math.BigDecimal.ZERO)
                            .build();
                }

                @Override
                public PartnershipModel getSupportedModel() {
                    return PartnershipModel.NONE;
                }
            };
        }
        return strategy;
    }
}
