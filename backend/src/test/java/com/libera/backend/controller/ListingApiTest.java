package com.libera.backend.controller;

import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListingApiTest extends ApiTestBase {

    private static String body(Long bookingId, String price, Boolean split) {
        return """
                {"originalBookingId": %s, "listedTotalPrice": %s, "allowsSplitBooking": %s}
                """.formatted(bookingId, price, split);
    }

    @Nested
    class Create {

        @Test
        void createsListing() throws Exception {
            postJson("/api/v1/listings", body(integrationBooking.getId(), "1200.50", true), seller)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.originalBookingId").value(integrationBooking.getId()))
                    .andExpect(jsonPath("$.sellerId").value(seller.getId()))
                    .andExpect(jsonPath("$.hotelName").value("Hotel Boutique"))
                    .andExpect(jsonPath("$.hotelCity").value("Bariloche"))
                    .andExpect(jsonPath("$.nights").value(4))
                    .andExpect(jsonPath("$.originalPrice").value(1500.00))
                    .andExpect(jsonPath("$.listedTotalPrice").value(1200.50))
                    .andExpect(jsonPath("$.discountPercentage").value(19.97))
                    .andExpect(jsonPath("$.allowsSplitBooking").value(true))
                    .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                    .andExpect(jsonPath("$.pmsConfirmationCode").doesNotExist());

            assertThat(listingRepository.count()).isEqualTo(1);
        }

        @Test
        void requiresAuthentication() throws Exception {
            postJson("/api/v1/listings", body(integrationBooking.getId(), "100", false), null)
                    .andExpect(status().isUnauthorized());
            assertThat(listingRepository.count()).isZero();
        }

        @Test
        void forbidsListingSomeoneElsesBooking() throws Exception {
            postJson("/api/v1/listings", body(integrationBooking.getId(), "100", false), buyer)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.path").value("/api/v1/listings"));
            assertThat(listingRepository.count()).isZero();
        }

        @Test
        void rejectsSplitOnConvenioHotel() throws Exception {
            postJson("/api/v1/listings", body(convenioBooking.getId(), "100", true), seller)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("INTEGRATION")));
        }

        @Test
        void rejectsUnknownBooking() throws Exception {
            postJson("/api/v1/listings", body(999_999L, "100", false), seller)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Original booking not found"));
        }

        @Test
        void validatesRequiredFields() throws Exception {
            postJson("/api/v1/listings", "{}", seller)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(allOf(
                            containsString("originalBookingId"),
                            containsString("listedTotalPrice"),
                            containsString("allowsSplitBooking"))));
        }

        @Test
        void rejectsZeroNegativeOrAbovePaidPrice() throws Exception {
            postJson("/api/v1/listings", body(integrationBooking.getId(), "0", false), seller)
                    .andExpect(status().isBadRequest());
            postJson("/api/v1/listings", body(integrationBooking.getId(), "-50", false), seller)
                    .andExpect(status().isBadRequest());
            postJson("/api/v1/listings", body(integrationBooking.getId(), "1500.01", false), seller)
                    .andExpect(status().isBadRequest());
        }

        @Test
        void malformedJsonIsBadRequest() throws Exception {
            postJson("/api/v1/listings", "{ esto no es json", seller)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Malformed")));
        }

        @Test
        void listingSameBookingTwiceIsConflict() throws Exception {
            postJson("/api/v1/listings", body(integrationBooking.getId(), "100", false), seller).andExpect(status().isCreated());
            postJson("/api/v1/listings", body(integrationBooking.getId(), "100", false), seller)
                    .andExpect(status().isConflict());
            assertThat(listingRepository.count()).isEqualTo(1);
        }

        @Test
        void cancelledBookingCanBeListedAgain() throws Exception {
            Listing old = saveListing(integrationBooking, "1000.00", false);
            old.setStatus(ListingStatus.CANCELLED);
            listingRepository.save(old);

            postJson("/api/v1/listings", body(integrationBooking.getId(), "900", false), seller)
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    class Read {

        @Test
        void catalogIsPublicAndOnlyShowsPurchasableListings() throws Exception {
            Listing active = saveListing(integrationBooking, "1000.00", true);
            Listing cancelled = saveListing(convenioBooking, "800.00", false);
            cancelled.setStatus(ListingStatus.CANCELLED);
            listingRepository.save(cancelled);

            mvc.perform(get("/api/v1/listings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(active.getId()))
                    .andExpect(jsonPath("$[0].soldRanges", hasSize(0)));
        }

        @Test
        void catalogFiltersByCityPriceAndDates() throws Exception {
            saveListing(integrationBooking, "1000.00", true);
            saveListing(convenioBooking, "800.00", false);

            mvc.perform(get("/api/v1/listings").param("city", "bariloche"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].hotelName").value("Hotel Boutique"));
            mvc.perform(get("/api/v1/listings").param("maxPrice", "900"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].hotelName").value("Gran Cadena"));
            mvc.perform(get("/api/v1/listings").param("checkIn", STAY_IN.plusDays(1).toString()).param("checkOut", STAY_OUT.toString()))
                    .andExpect(jsonPath("$", hasSize(2)));
            mvc.perform(get("/api/v1/listings").param("checkIn", STAY_OUT.toString()).param("checkOut", STAY_OUT.plusDays(2).toString()))
                    .andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/v1/listings").param("hotelId", String.valueOf(convenioHotel.getId())))
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        void catalogHidesSplitListingsWhoseRequestedNightsAreSold() throws Exception {
            Listing split = saveListing(integrationBooking, "1000.00", true);
            split.setStatus(ListingStatus.PARTIALLY_SOLD);
            listingRepository.save(split);
            purchaseRepository.save(com.libera.backend.domain.entity.ResalePurchase.builder().listing(split).buyer(buyer)
                    .checkIn(STAY_IN).checkOut(STAY_IN.plusDays(2)).totalPrice(new java.math.BigDecimal("500.00")).buyerFee(new java.math.BigDecimal("37.50"))
                    .status(ResalePurchaseStatus.NAME_CHANGED).build());

            mvc.perform(get("/api/v1/listings").param("checkIn", STAY_IN.toString()).param("checkOut", STAY_IN.plusDays(1).toString()))
                    .andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/v1/listings").param("checkIn", STAY_IN.plusDays(2).toString()).param("checkOut", STAY_OUT.toString()))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].soldRanges[0].checkIn").value(STAY_IN.toString()))
                    .andExpect(jsonPath("$[0].soldRanges[0].checkOut").value(STAY_IN.plusDays(2).toString()));
        }

        @Test
        void detailIsPublic() throws Exception {
            Listing listing = saveListing(integrationBooking, "1000.00", true);

            mvc.perform(get("/api/v1/listings/{id}", listing.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hotelSlug").value("hotel-boutique"))
                    .andExpect(jsonPath("$.checkIn").value(STAY_IN.toString()));
            mvc.perform(get("/api/v1/listings/{id}", 999_999)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/listings/abc")).andExpect(status().isBadRequest());
        }

        @Test
        void mineRequiresAuthAndOnlyReturnsOwnListings() throws Exception {
            saveListing(integrationBooking, "1000.00", false);

            mvc.perform(get("/api/v1/listings/mine")).andExpect(status().isUnauthorized());
            getAs("/api/v1/listings/mine", seller).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
            getAs("/api/v1/listings/mine", buyer).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    class Cancel {

        @Test
        void sellerCancelsAndListingLeavesCatalog() throws Exception {
            Listing listing = saveListing(integrationBooking, "1000.00", false);

            postEmpty("/api/v1/listings/" + listing.getId() + "/cancel", seller)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
            mvc.perform(get("/api/v1/listings")).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void otherUserCannotCancel() throws Exception {
            Listing listing = saveListing(integrationBooking, "1000.00", false);
            postEmpty("/api/v1/listings/" + listing.getId() + "/cancel", buyer).andExpect(status().isForbidden());
        }

        @Test
        void cannotCancelSoldListing() throws Exception {
            Listing listing = saveListing(integrationBooking, "1000.00", false);
            listing.setStatus(ListingStatus.SOLD_OUT);
            listingRepository.save(listing);

            postEmpty("/api/v1/listings/" + listing.getId() + "/cancel", seller).andExpect(status().isConflict());
        }
    }

    @Test
    void unsupportedMethodIs405NotServerError() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/listings")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1")))
                .andExpect(status().isMethodNotAllowed());
    }
}
