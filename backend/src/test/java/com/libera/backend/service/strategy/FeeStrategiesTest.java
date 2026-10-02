package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fees públicos (documento del proyecto): el comprador paga precio + Garantía de Traspaso por tramos
 * (7,5% / 10% / 12,5% según el descuento del vendedor) y el vendedor paga 7,5% fijo.
 */
class FeeStrategiesTest {

    private static final BigDecimal D20 = new BigDecimal("20.00");

    private static Hotel hotel(PartnershipModel model, String markupFee) {
        return Hotel.builder()
                .partnershipModel(model)
                .markupFee(markupFee == null ? null : new BigDecimal(markupFee))
                .build();
    }

    /** Todo lo que paga el comprador se reparte: vendedor + hotel + LIBERA. */
    private static void assertMoneyIsConserved(FeeCalculationResult r) {
        BigDecimal distributed = r.getSellerPayoutAmount()
                .add(r.getHotelRevenueShareAmount())
                .add(r.getLiberaNetRevenue());
        assertThat(distributed).isEqualByComparingTo(r.getTotalPaidByBuyer());
    }

    @Nested
    class GuaranteeTiers {

        @ParameterizedTest(name = "descuento {0}% -> Garantía {1}")
        @CsvSource({"0, 0.075", "10, 0.075", "34.99, 0.075", "35, 0.10", "54.99, 0.10", "55, 0.125", "80, 0.125"})
        void tierDependsOnSellerDiscount(String discount, String expectedRate) {
            assertThat(PublicFees.buyerFeeRate(new BigDecimal(discount))).isEqualByComparingTo(expectedRate);
        }

        @Test
        void missingDiscountUsesLowestTier() {
            assertThat(PublicFees.buyerFeeRate(null)).isEqualByComparingTo("0.075");
        }
    }

    @Nested
    class Integration {

        private final IntegrationFeeStrategy strategy = new IntegrationFeeStrategy();

        @Test
        void publicFeesPlusHotelRevenueShare() {
            FeeCalculationResult r = strategy.calculateFees(new BigDecimal("1000.00"), D20, hotel(PartnershipModel.INTEGRATION, "20.00"));

            // comprador: 1000 + 7,5% de Garantía
            assertThat(r.getBuyerFeeAmount()).isEqualByComparingTo("75.00");
            assertThat(r.getTotalPaidByBuyer()).isEqualByComparingTo("1075.00");
            // vendedor: 7,5% de 1000
            assertThat(r.getSellerFeeAmount()).isEqualByComparingTo("75.00");
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo("925.00");
            // LIBERA cobra 150 y le da el 20% al hotel
            assertThat(r.getHotelRevenueShareAmount()).isEqualByComparingTo("30.00");
            assertThat(r.getLiberaNetRevenue()).isEqualByComparingTo("120.00");
            assertMoneyIsConserved(r);
        }

        @Test
        void higherDiscountRaisesTheGuarantee() {
            FeeCalculationResult r = strategy.calculateFees(new BigDecimal("400.00"), new BigDecimal("60"), hotel(PartnershipModel.INTEGRATION, "20.00"));

            assertThat(r.getBuyerFeeAmount()).isEqualByComparingTo("50.00"); // 12,5% (tope)
            assertThat(r.getTotalPaidByBuyer()).isEqualByComparingTo("450.00");
            assertMoneyIsConserved(r);
        }

        @Test
        void nullMarkupMeansNoHotelShare() {
            FeeCalculationResult r = strategy.calculateFees(new BigDecimal("1000.00"), D20, hotel(PartnershipModel.INTEGRATION, null));

            assertThat(r.getHotelRevenueShareAmount()).isEqualByComparingTo("0");
            assertThat(r.getLiberaNetRevenue()).isEqualByComparingTo("150.00");
            assertMoneyIsConserved(r);
        }

        @Test
        void conservesMoneyWithAwkwardAmounts() {
            for (String amount : List.of("0.01", "1.00", "99.99", "333.33", "1234.57", "98765.43")) {
                for (String discount : List.of("10", "40", "70")) {
                    assertMoneyIsConserved(strategy.calculateFees(new BigDecimal(amount), new BigDecimal(discount),
                            hotel(PartnershipModel.INTEGRATION, "12.50")));
                }
            }
        }
    }

    @Nested
    class Convenio {

        private final ConvenioFeeStrategy strategy = new ConvenioFeeStrategy();

        @Test
        void samePublicFeesWithoutRevenueShare() {
            FeeCalculationResult r = strategy.calculateFees(new BigDecimal("1000.00"), new BigDecimal("40"), hotel(PartnershipModel.CONVENIO, "20.00"));

            assertThat(r.getBuyerFeeAmount()).isEqualByComparingTo("100.00"); // 10%
            assertThat(r.getSellerFeeAmount()).isEqualByComparingTo("75.00");
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo("925.00");
            // aunque el hotel tenga markup cargado, en Convenio no hay revenue share
            assertThat(r.getHotelRevenueShareAmount()).isEqualByComparingTo("0");
            assertThat(r.getLiberaNetRevenue()).isEqualByComparingTo("175.00");
            assertMoneyIsConserved(r);
        }

        @Test
        void roundsHalfUp() {
            FeeCalculationResult r = strategy.calculateFees(new BigDecimal("0.10"), D20, hotel(PartnershipModel.CONVENIO, null));

            assertThat(r.getSellerFeeAmount()).isEqualByComparingTo("0.01"); // 0,0075 -> 0,01
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo("0.09");
        }
    }

    @Nested
    class Factory {

        private final FeeStrategyFactory factory =
                new FeeStrategyFactory(List.of(new IntegrationFeeStrategy(), new ConvenioFeeStrategy()));

        @Test
        void returnsStrategyForEachModel() {
            assertThat(factory.getStrategy(PartnershipModel.INTEGRATION)).isInstanceOf(IntegrationFeeStrategy.class);
            assertThat(factory.getStrategy(PartnershipModel.CONVENIO)).isInstanceOf(ConvenioFeeStrategy.class);
        }

        @Test
        void hotelsWithoutPartnershipPayPublicFeesWithoutShare() {
            FeeCalculationStrategy none = factory.getStrategy(PartnershipModel.NONE);

            FeeCalculationResult r = none.calculateFees(new BigDecimal("500.00"), D20, hotel(PartnershipModel.NONE, null));

            assertThat(none.getSupportedModel()).isEqualTo(PartnershipModel.NONE);
            assertThat(r.getBuyerFeeAmount()).isEqualByComparingTo("37.50");
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo("462.50");
            assertThat(r.getHotelRevenueShareAmount()).isEqualByComparingTo("0");
            assertMoneyIsConserved(r);
        }

        @Test
        void nullModelAlsoFallsBack() {
            assertThat(factory.getStrategy(null).getSupportedModel()).isEqualTo(PartnershipModel.NONE);
        }
    }
}
