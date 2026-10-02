package com.libera.backend.service.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fees públicos de LIBERA, iguales para todos los modelos de hotel:
 * <ul>
 *   <li>Comprador: paga el precio + la <b>Garantía de Traspaso</b>, que va por tramos según el descuento
 *       que puso el vendedor: 7,5% (descuento menor a 35%), 10% (35% a 54,99%) y 12,5% de tope (55% o más).</li>
 *   <li>Vendedor: 7,5% fijo sobre el precio de venta.</li>
 * </ul>
 * Lo que cambia entre modelos es si una parte de lo que cobra LIBERA va al hotel (revenue share).
 */
public final class PublicFees {

    public static final BigDecimal SELLER_FEE_RATE = new BigDecimal("0.075");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PublicFees() {
    }

    /** Porcentaje de la Garantía de Traspaso según el descuento del vendedor (en %, ej. 26.36). */
    public static BigDecimal buyerFeeRate(BigDecimal discountPercentage) {
        BigDecimal discount = discountPercentage == null ? BigDecimal.ZERO : discountPercentage;
        if (discount.compareTo(new BigDecimal("55")) >= 0) {
            return new BigDecimal("0.125");
        }
        if (discount.compareTo(new BigDecimal("35")) >= 0) {
            return new BigDecimal("0.10");
        }
        return new BigDecimal("0.075");
    }

    public static BigDecimal buyerFee(BigDecimal salePrice, BigDecimal discountPercentage) {
        return salePrice.multiply(buyerFeeRate(discountPercentage)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Reparte la operación. {@code hotelSharePercentage} es la parte (en %) de lo que cobra LIBERA que va
     * al hotel; 0 si el modelo no tiene revenue share.
     * Siempre se cumple: pago del vendedor + parte del hotel + neto de LIBERA = total que pagó el comprador.
     */
    public static FeeCalculationResult calculate(BigDecimal salePrice, BigDecimal discountPercentage, BigDecimal hotelSharePercentage) {
        BigDecimal buyerFee = buyerFee(salePrice, discountPercentage);
        BigDecimal sellerFee = salePrice.multiply(SELLER_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal liberaGross = buyerFee.add(sellerFee);

        BigDecimal sharePct = hotelSharePercentage == null ? BigDecimal.ZERO : hotelSharePercentage;
        BigDecimal hotelShare = liberaGross.multiply(sharePct).divide(HUNDRED, 2, RoundingMode.HALF_UP);

        return FeeCalculationResult.builder()
                .totalPaidByBuyer(salePrice.add(buyerFee))
                .buyerFeeAmount(buyerFee)
                .sellerFeeAmount(sellerFee)
                .sellerPayoutAmount(salePrice.subtract(sellerFee))
                .hotelRevenueShareAmount(hotelShare)
                .liberaNetRevenue(liberaGross.subtract(hotelShare))
                .build();
    }
}
