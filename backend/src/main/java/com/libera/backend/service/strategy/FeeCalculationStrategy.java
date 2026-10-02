package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;

import java.math.BigDecimal;

public interface FeeCalculationStrategy {
    /**
     * @param salePrice          precio de las noches vendidas (sin la Garantía de Traspaso)
     * @param discountPercentage descuento que puso el vendedor sobre lo que pagó (define el tramo de la Garantía)
     */
    FeeCalculationResult calculateFees(BigDecimal salePrice, BigDecimal discountPercentage, Hotel hotel);

    PartnershipModel getSupportedModel();
}
