package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;

import java.math.BigDecimal;

public interface FeeCalculationStrategy {
    FeeCalculationResult calculateFees(BigDecimal totalPaidByBuyer, Hotel hotel);
    PartnershipModel getSupportedModel();
}
