package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Modelo Integración: fees públicos + revenue share con el hotel (su markupFee, en % de lo que cobra LIBERA). */
@Component
public class IntegrationFeeStrategy implements FeeCalculationStrategy {

    @Override
    public FeeCalculationResult calculateFees(BigDecimal salePrice, BigDecimal discountPercentage, Hotel hotel) {
        return PublicFees.calculate(salePrice, discountPercentage, hotel.getMarkupFee());
    }

    @Override
    public PartnershipModel getSupportedModel() {
        return PartnershipModel.INTEGRATION;
    }
}
