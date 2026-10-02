package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Modelo Convenio: fees públicos, sin revenue share para el hotel. */
@Component
public class ConvenioFeeStrategy implements FeeCalculationStrategy {

    @Override
    public FeeCalculationResult calculateFees(BigDecimal salePrice, BigDecimal discountPercentage, Hotel hotel) {
        return PublicFees.calculate(salePrice, discountPercentage, BigDecimal.ZERO);
    }

    @Override
    public PartnershipModel getSupportedModel() {
        return PartnershipModel.CONVENIO;
    }
}
