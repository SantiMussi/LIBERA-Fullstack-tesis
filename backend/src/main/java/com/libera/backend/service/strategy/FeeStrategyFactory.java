package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FeeStrategyFactory {

    /** Hoteles sin convenio (o modelo desconocido): se cobran los fees públicos y no hay revenue share. */
    private static final FeeCalculationStrategy NO_PARTNERSHIP = new FeeCalculationStrategy() {
        @Override
        public FeeCalculationResult calculateFees(BigDecimal salePrice, BigDecimal discountPercentage, Hotel hotel) {
            return PublicFees.calculate(salePrice, discountPercentage, BigDecimal.ZERO);
        }

        @Override
        public PartnershipModel getSupportedModel() {
            return PartnershipModel.NONE;
        }
    };

    private final Map<PartnershipModel, FeeCalculationStrategy> strategies;

    public FeeStrategyFactory(List<FeeCalculationStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(FeeCalculationStrategy::getSupportedModel, Function.identity()));
    }

    public FeeCalculationStrategy getStrategy(PartnershipModel model) {
        return model == null ? NO_PARTNERSHIP : strategies.getOrDefault(model, NO_PARTNERSHIP);
    }
}
