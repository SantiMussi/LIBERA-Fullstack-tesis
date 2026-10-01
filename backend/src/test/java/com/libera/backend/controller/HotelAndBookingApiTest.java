package com.libera.backend.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotelAndBookingApiTest extends ApiTestBase {

    @Nested
    class Hotels {

        @Test
        void searchIsPublicAndHidesConfidentialData() throws Exception {
            mvc.perform(get("/api/v1/hotels"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].name").value("Gran Cadena"))
                    .andExpect(jsonPath("$[0].liberaPartner").value(true))
                    .andExpect(jsonPath("$[0].splitBookingAvailable").value(false))
                    .andExpect(jsonPath("$[1].splitBookingAvailable").value(true))
                    .andExpect(jsonPath("$[0].partnershipModel").doesNotExist())
                    .andExpect(jsonPath("$[0].markupFee").doesNotExist())
                    .andExpect(jsonPath("$[0].pmsProvider").doesNotExist())
                    .andExpect(jsonPath("$[0].taxId").doesNotExist());
        }

        @Test
        void searchByNameAndCity() throws Exception {
            mvc.perform(get("/api/v1/hotels").param("q", "bouti")).andExpect(jsonPath("$", hasSize(1)));
            // q también busca por ciudad y país, como el buscador del wizard de venta
            mvc.perform(get("/api/v1/hotels").param("q", "barilo")).andExpect(jsonPath("$", hasSize(1)));
            mvc.perform(get("/api/v1/hotels").param("q", "argentina")).andExpect(jsonPath("$", hasSize(2)));
            mvc.perform(get("/api/v1/hotels").param("city", "MENDOZA")).andExpect(jsonPath("$", hasSize(1)));
            mvc.perform(get("/api/v1/hotels").param("q", "zzz")).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void detail() throws Exception {
            mvc.perform(get("/api/v1/hotels/{id}", integrationHotel.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("hotel-boutique"));
            mvc.perform(get("/api/v1/hotels/{id}", 999_999)).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Bookings {

        private String body(Long hotelId, String code, LocalDate in, LocalDate out, String amount) {
            return """
                    {"hotelId": %s, "pmsConfirmationCode": "%s", "checkIn": "%s", "checkOut": "%s",
                     "roomType": "Suite", "totalAmountPaid": %s}
                    """.formatted(hotelId, code, in, out, amount);
        }

        @Test
        void userRegistersBookingAndSeesItInMine() throws Exception {
            postJson("/api/v1/bookings", body(integrationHotel.getId(), "RB-999", STAY_IN, STAY_OUT, "2000"), buyer)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.hotelName").value("Hotel Boutique"))
                    .andExpect(jsonPath("$.originalGuestId").value(buyer.getId()))
                    .andExpect(jsonPath("$.nights").value(4))
                    .andExpect(jsonPath("$.isLiberaRate").value(true));

            getAs("/api/v1/bookings/mine", buyer)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].pmsConfirmationCode").value("RB-999"));
        }

        @Test
        void convenioBookingIsNotLiberaRate() throws Exception {
            postJson("/api/v1/bookings", body(convenioHotel.getId(), "WP-999", STAY_IN, STAY_OUT, "2000"), buyer)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.isLiberaRate").value(false));
        }

        @Test
        void rejectsDuplicateConfirmationCodeForSameHotel() throws Exception {
            postJson("/api/v1/bookings", body(integrationHotel.getId(), "RB-111", STAY_IN, STAY_OUT, "2000"), buyer)
                    .andExpect(status().isConflict());
        }

        @Test
        void validatesDatesAndAmounts() throws Exception {
            postJson("/api/v1/bookings", body(integrationHotel.getId(), "X1", STAY_OUT, STAY_IN, "2000"), buyer)
                    .andExpect(status().isBadRequest());
            postJson("/api/v1/bookings", body(integrationHotel.getId(), "X2", LocalDate.now().minusDays(3), STAY_IN, "2000"), buyer)
                    .andExpect(status().isBadRequest());
            postJson("/api/v1/bookings", body(integrationHotel.getId(), "X3", STAY_IN, STAY_OUT, "0"), buyer)
                    .andExpect(status().isBadRequest());
            postJson("/api/v1/bookings", body(999_999L, "X4", STAY_IN, STAY_OUT, "100"), buyer)
                    .andExpect(status().isNotFound());
        }

        @Test
        void requiresAuthentication() throws Exception {
            postJson("/api/v1/bookings", body(integrationHotel.getId(), "RB-999", STAY_IN, STAY_OUT, "2000"), null)
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/bookings/mine")).andExpect(status().isUnauthorized());
            assertThat(bookingRepository.count()).isEqualTo(2);
        }
    }
}
