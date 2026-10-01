package com.libera.backend.controller;

import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseApiTest extends ApiTestBase {

    private static String body(Long listingId, LocalDate in, LocalDate out) {
        return """
                {"listingId": %s, "checkIn": "%s", "checkOut": "%s"}
                """.formatted(listingId, in, out);
    }

    private ListingStatus statusOf(Listing listing) {
        return listingRepository.findById(listing.getId()).orElseThrow().getStatus();
    }

    @Nested
    class Create {

        @Test
        void buyingFullStayFromRoibackHotelChangesNameAndSellsOut() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", false);

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN, STAY_OUT), buyer)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.listingId").value(listing.getId()))
                    .andExpect(jsonPath("$.buyerId").value(buyer.getId()))
                    .andExpect(jsonPath("$.hotelName").value("Hotel Boutique"))
                    .andExpect(jsonPath("$.checkIn").value(STAY_IN.toString()))
                    .andExpect(jsonPath("$.nights").value(4))
                    .andExpect(jsonPath("$.totalPrice").value(1050.00))
                    .andExpect(jsonPath("$.status").value("NAME_CHANGED"));

            assertThat(statusOf(listing)).isEqualTo(ListingStatus.SOLD_OUT);
        }

        @Test
        void requiresAuthentication() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", false);

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN, STAY_OUT), null)
                    .andExpect(status().isUnauthorized());
            assertThat(purchaseRepository.count()).isZero();
        }

        @Test
        void rejectsPastDates() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", false);

            postJson("/api/v1/purchases", body(listing.getId(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(2)), buyer)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("checkIn")));
        }

        @Test
        void rejectsUnknownListing() throws Exception {
            postJson("/api/v1/purchases", body(999_999L, STAY_IN, STAY_OUT), buyer)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Listing not found"));
        }

        @Test
        void rejectsCheckOutBeforeCheckIn() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", true);

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN.plusDays(3), STAY_IN), buyer)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Check-out must be after check-in")));
            assertThat(purchaseRepository.count()).isZero();
        }

        @Test
        void rejectsDatesOutsideOriginalBooking() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", true);
            LocalDate in = LocalDate.now().plusDays(100);

            postJson("/api/v1/purchases", body(listing.getId(), in, in.plusDays(2)), buyer)
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsPartialStayWhenSplitNotAllowed() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", false);

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN.plusDays(1), STAY_IN.plusDays(2)), buyer)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("full stay")));
        }

        @Test
        void sellerCannotBuyOwnListing() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", false);

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN, STAY_OUT), seller)
                    .andExpect(status().isBadRequest());
            assertThat(purchaseRepository.count()).isZero();
        }

        @Test
        void cannotBuyAlreadySoldListing() throws Exception {
            Listing listing = saveListing(integrationBooking, "1050.00", false);

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN, STAY_OUT), buyer).andExpect(status().isCreated());
            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN, STAY_OUT), buyer).andExpect(status().isConflict());
            assertThat(purchaseRepository.count()).isEqualTo(1);
        }

        @Test
        void splitBookingSellsNightsSeparatelyUntilSoldOut() throws Exception {
            Listing listing = saveListing(integrationBooking, "1000.00", true);

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN, STAY_IN.plusDays(1)), buyer)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalPrice").value(250.00));
            assertThat(statusOf(listing)).isEqualTo(ListingStatus.PARTIALLY_SOLD);

            // las mismas noches ya no se pueden vender
            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN, STAY_IN.plusDays(2)), admin)
                    .andExpect(status().isConflict());

            postJson("/api/v1/purchases", body(listing.getId(), STAY_IN.plusDays(1), STAY_OUT), admin)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalPrice").value(750.00));
            assertThat(statusOf(listing)).isEqualTo(ListingStatus.SOLD_OUT);
        }
    }

    @Nested
    class Read {

        @Test
        void buyerSeesPurchasesAndSellerSeesSales() throws Exception {
            ResalePurchase purchase = savePurchase(saveListing(integrationBooking, "1000.00", false), ResalePurchaseStatus.NAME_CHANGED);

            getAs("/api/v1/purchases/mine", buyer).andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(purchase.getId()));
            getAs("/api/v1/purchases/mine", seller).andExpect(jsonPath("$", hasSize(0)));
            getAs("/api/v1/purchases/sales", seller).andExpect(jsonPath("$", hasSize(1)));
            getAs("/api/v1/purchases/sales", buyer).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void detailOnlyForBuyerSellerOrAdmin() throws Exception {
            ResalePurchase purchase = savePurchase(saveListing(integrationBooking, "1000.00", false), ResalePurchaseStatus.NAME_CHANGED);
            String url = "/api/v1/purchases/" + purchase.getId();
            var stranger = userRepository.save(com.libera.backend.domain.entity.User.builder().email("otro@libera.test")
                    .passwordHash("x").firstName("Otro").lastName("Usuario").build());

            getAs(url, buyer).andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(1000.00));
            getAs(url, seller).andExpect(status().isOk());
            getAs(url, admin).andExpect(status().isOk());
            getAs(url, stranger).andExpect(status().isForbidden());
            getAs("/api/v1/purchases/999999", buyer).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Dispute {

        @Test
        void buyerOpensDisputeOnce() throws Exception {
            ResalePurchase purchase = savePurchase(saveListing(convenioBooking, "1000.00", false), ResalePurchaseStatus.PAYMENT_HELD);
            String url = "/api/v1/purchases/" + purchase.getId() + "/dispute";

            postEmpty(url, seller).andExpect(status().isForbidden());
            postEmpty(url, buyer).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPUTED"));
            postEmpty(url, buyer).andExpect(status().isConflict());
        }

        @Test
        void cannotDisputeLiquidatedPurchase() throws Exception {
            ResalePurchase purchase = savePurchase(saveListing(convenioBooking, "1000.00", false), ResalePurchaseStatus.LIQUIDATED);

            postEmpty("/api/v1/purchases/" + purchase.getId() + "/dispute", buyer).andExpect(status().isConflict());
        }
    }
}
