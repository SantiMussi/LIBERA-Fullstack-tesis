package com.libera.backend.service.strategy;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeeStrategiesTest {

    private static Hotel hotel(PartnershipModel model, String markupFee) {
        return Hotel.builder()
                .partnershipModel(model)
                .markupFee(markupFee == null ? null : new BigDecimal(markupFee))
                .build();
    }

    /** El dinero del comprador tiene que quedar 100% repartido: vendedor + hotel + LIBERA. */
    private static void assertMoneyIsConserved(BigDecimal total, FeeCalculationResult r) {
        BigDecimal distributed = r.getSellerPayoutAmount()
                .add(r.getHotelRevenueShareAmount())
                .add(r.getLiberaNetRevenue());
        assertThat(distributed).isEqualByComparingTo(total);
    }

    @Nested
    class Integration {

        private final IntegrationFeeStrategy strategy = new IntegrationFeeStrategy();

        @Test
        void calculatesFeesWithHotelRevenueShare() {
            BigDecimal total = new BigDecimal("1050.00");

            FeeCalculationResult r = strategy.calculateFees(total, hotel(PartnershipModel.INTEGRATION, "20.00"));

            // base = 1050 / 1.05 = 1000 -> fee comprador 50, fee vendedor 15% de 1000 = 150
            assertThat(r.getBuyerFeeAmount()).isEqualByComparingTo("50.00");
            assertThat(r.getSellerFeeAmount()).isEqualByComparingTo("150.00");
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo("850.00");
            // fees totales 200, el hotel se lleva el 20% = 40
            assertThat(r.getHotelRevenueShareAmount()).isEqualByComparingTo("40.00");
            assertThat(r.getLiberaNetRevenue()).isEqualByComparingTo("160.00");
            assertMoneyIsConserved(total, r);
        }

        @Test
        void nullMarkupMeansNoHotelShare() {
            BigDecimal total = new BigDecimal("1050.00");

            FeeCalculationResult r = strategy.calculateFees(total, hotel(PartnershipModel.INTEGRATION, null));

            assertThat(r.getHotelRevenueShareAmount()).isEqualByComparingTo("0");
            assertThat(r.getLiberaNetRevenue()).isEqualByComparingTo("200.00");
            assertMoneyIsConserved(total, r);
        }

        @Test
        void conservesMoneyWithAwkwardAmounts() {
            for (String amount : List.of("0.01", "1.00", "99.99", "333.33", "1234.57", "98765.43")) {
                BigDecimal total = new BigDecimal(amount);
                assertMoneyIsConserved(total, strategy.calculateFees(total, hotel(PartnershipModel.INTEGRATION, "12.50")));
            }
        }

        @Test
        void supportsIntegrationModel() {
            assertThat(strategy.getSupportedModel()).isEqualTo(PartnershipModel.INTEGRATION);
        }
    }

    @Nested
    class Convenio {

        private final ConvenioFeeStrategy strategy = new ConvenioFeeStrategy();

        @Test
        void chargesOnlyTheSellerTenPercent() {
            BigDecimal total = new BigDecimal("1000.00");

            FeeCalculationResult r = strategy.calculateFees(total, hotel(PartnershipModel.CONVENIO, "20.00"));

            assertThat(r.getBuyerFeeAmount()).isEqualByComparingTo("0");
            assertThat(r.getSellerFeeAmount()).isEqualByComparingTo("100.00");
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo("900.00");
            // aunque el hotel tenga markup cargado, en Convenio no hay revenue share
            assertThat(r.getHotelRevenueShareAmount()).isEqualByComparingTo("0");
            assertThat(r.getLiberaNetRevenue()).isEqualByComparingTo("100.00");
            assertMoneyIsConserved(total, r);
        }

        @Test
        void roundsHalfUp() {
            FeeCalculationResult r = strategy.calculateFees(new BigDecimal("0.05"), hotel(PartnershipModel.CONVENIO, null));

            assertThat(r.getSellerFeeAmount()).isEqualByComparingTo("0.01");
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo("0.04");
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
        void fallbackForNoneChargesNothing() {
            FeeCalculationStrategy fallback = factory.getStrategy(PartnershipModel.NONE);
            BigDecimal total = new BigDecimal("500.00");

            FeeCalculationResult r = fallback.calculateFees(total, hotel(PartnershipModel.NONE, null));

            assertThat(fallback.getSupportedModel()).isEqualTo(PartnershipModel.NONE);
            assertThat(r.getSellerPayoutAmount()).isEqualByComparingTo(total);
            assertThat(r.getLiberaNetRevenue()).isEqualByComparingTo("0");
            assertMoneyIsConserved(total, r);
        }

        @Test
        void nullModelAlsoFallsBack() {
            assertThat(factory.getStrategy(null).getSupportedModel()).isEqualTo(PartnershipModel.NONE);
        }
    }
}
