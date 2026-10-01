package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.PartnershipModel;
import com.libera.backend.dto.request.ListingCreateRequestDTO;
import com.libera.backend.dto.response.ListingResponseDTO;
import com.libera.backend.exception.BusinessConflictException;
import com.libera.backend.exception.InvalidSplitBookingException;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.mapper.ListingMapperImpl;
import com.libera.backend.repository.ListingRepository;
import com.libera.backend.repository.OriginalBookingRepository;
import com.libera.backend.repository.ResalePurchaseRepository;
import com.libera.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceImplTest {

    private static final long OWNER_ID = 1L;
    private static final long BOOKING_ID = 100L;
    private static final long LISTING_ID = 55L;

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private OriginalBookingRepository originalBookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResalePurchaseRepository resalePurchaseRepository;

    private ListingServiceImpl service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ListingServiceImpl(listingRepository, originalBookingRepository, userRepository,
                resalePurchaseRepository, new ListingMapperImpl());
        owner = User.builder().id(OWNER_ID).firstName("Ana").lastName("Pérez").build();
    }

    private OriginalBooking bookingAt(PartnershipModel model) {
        Hotel hotel = Hotel.builder().id(7L).name("Hotel Boutique").partnershipModel(model).build();
        return OriginalBooking.builder().id(BOOKING_ID).hotel(hotel).originalGuest(owner)
                .checkIn(LocalDate.now().plusDays(10)).checkOut(LocalDate.now().plusDays(14))
                .roomType("Doble").totalAmountPaid(new BigDecimal("1500.00")).build();
    }

    private static ListingCreateRequestDTO request(boolean split, String price) {
        ListingCreateRequestDTO dto = new ListingCreateRequestDTO();
        dto.setOriginalBookingId(BOOKING_ID);
        dto.setListedTotalPrice(new BigDecimal(price));
        dto.setAllowsSplitBooking(split);
        return dto;
    }

    @Nested
    class CreateListing {

        private void stubHappyPath(PartnershipModel model) {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(bookingAt(model)));
            when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
            when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
                Listing l = inv.getArgument(0);
                l.setId(LISTING_ID);
                return l;
            });
        }

        @Test
        void ownerCanListBookingWithSplitOnIntegrationHotel() {
            stubHappyPath(PartnershipModel.INTEGRATION);

            ListingResponseDTO response = service.createListing(request(true, "1200.00"), OWNER_ID);

            assertThat(response.getId()).isEqualTo(LISTING_ID);
            assertThat(response.getOriginalBookingId()).isEqualTo(BOOKING_ID);
            assertThat(response.getSellerId()).isEqualTo(OWNER_ID);
            assertThat(response.getHotelName()).isEqualTo("Hotel Boutique");
            assertThat(response.getNights()).isEqualTo(4);
            assertThat(response.getListedTotalPrice()).isEqualByComparingTo("1200.00");
            assertThat(response.getAllowsSplitBooking()).isTrue();
            assertThat(response.getStatus()).isEqualTo(ListingStatus.ACTIVE);
            assertThat(response.getSoldRanges()).isEmpty();

            ArgumentCaptor<Listing> saved = ArgumentCaptor.forClass(Listing.class);
            verify(listingRepository).save(saved.capture());
            assertThat(saved.getValue().getSeller()).isSameAs(owner);
        }

        @Test
        void calculatesDiscountAgainstAmountPaid() {
            stubHappyPath(PartnershipModel.CONVENIO);

            // pagó 1500, publica a 1200 -> 20% de descuento
            ListingResponseDTO response = service.createListing(request(false, "1200.00"), OWNER_ID);

            assertThat(response.getDiscountPercentage()).isEqualByComparingTo("20.00");
        }

        @Test
        void ownerCanListWithoutSplitOnConvenioHotel() {
            stubHappyPath(PartnershipModel.CONVENIO);

            ListingResponseDTO response = service.createListing(request(false, "1000.00"), OWNER_ID);

            assertThat(response.getAllowsSplitBooking()).isFalse();
        }

        @Test
        void rejectsUnknownBooking() {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createListing(request(false, "100"), OWNER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Original booking not found");
            verify(listingRepository, never()).save(any());
        }

        @Test
        void rejectsUserWhoIsNotTheOriginalGuest() {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(bookingAt(PartnershipModel.INTEGRATION)));

            assertThatThrownBy(() -> service.createListing(request(false, "100"), 999L))
                    .isInstanceOf(SecurityException.class);
            verify(listingRepository, never()).save(any());
        }

        @Test
        void rejectsBookingWhoseCheckInAlreadyPassed() {
            OriginalBooking booking = bookingAt(PartnershipModel.INTEGRATION);
            booking.setCheckIn(LocalDate.now().minusDays(1));
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> service.createListing(request(false, "100"), OWNER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already passed");
        }

        @Test
        void rejectsSplitBookingOnConvenioHotel() {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(bookingAt(PartnershipModel.CONVENIO)));

            assertThatThrownBy(() -> service.createListing(request(true, "100"), OWNER_ID))
                    .isInstanceOf(InvalidSplitBookingException.class)
                    .hasMessageContaining("INTEGRATION");
            verify(listingRepository, never()).save(any());
        }

        @Test
        void rejectsSplitBookingOnNonPartnerHotel() {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(bookingAt(PartnershipModel.NONE)));

            assertThatThrownBy(() -> service.createListing(request(true, "100"), OWNER_ID))
                    .isInstanceOf(InvalidSplitBookingException.class);
        }

        @Test
        void rejectsPriceAboveAmountPaid() {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(bookingAt(PartnershipModel.INTEGRATION)));

            assertThatThrownBy(() -> service.createListing(request(false, "1500.01"), OWNER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot exceed");
        }

        @Test
        void rejectsSecondListingForSameBooking() {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(bookingAt(PartnershipModel.INTEGRATION)));
            when(listingRepository.existsByOriginalBookingIdAndStatusNot(BOOKING_ID, ListingStatus.CANCELLED)).thenReturn(true);

            assertThatThrownBy(() -> service.createListing(request(false, "100"), OWNER_ID))
                    .isInstanceOf(BusinessConflictException.class);
            verify(listingRepository, never()).save(any());
        }

        @Test
        void rejectsWhenSellerUserDoesNotExist() {
            when(originalBookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(bookingAt(PartnershipModel.INTEGRATION)));
            when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createListing(request(false, "100"), OWNER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Seller not found");
        }
    }

    @Nested
    class CancelListing {

        private Listing listing(ListingStatus status) {
            return Listing.builder().id(LISTING_ID).originalBooking(bookingAt(PartnershipModel.INTEGRATION)).seller(owner)
                    .listedTotalPrice(new BigDecimal("1200.00")).allowsSplitBooking(false).status(status).build();
        }

        @Test
        void sellerCanCancelActiveListing() {
            when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing(ListingStatus.ACTIVE)));
            when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.cancelListing(LISTING_ID, OWNER_ID).getStatus()).isEqualTo(ListingStatus.CANCELLED);
        }

        @Test
        void otherUserCannotCancel() {
            when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing(ListingStatus.ACTIVE)));

            assertThatThrownBy(() -> service.cancelListing(LISTING_ID, 999L)).isInstanceOf(SecurityException.class);
            verify(listingRepository, never()).save(any());
        }

        @Test
        void cannotCancelListingWithSales() {
            when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing(ListingStatus.PARTIALLY_SOLD)));

            assertThatThrownBy(() -> service.cancelListing(LISTING_ID, OWNER_ID)).isInstanceOf(BusinessConflictException.class);
        }

        @Test
        void cannotCancelTwice() {
            when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing(ListingStatus.CANCELLED)));

            assertThatThrownBy(() -> service.cancelListing(LISTING_ID, OWNER_ID)).isInstanceOf(BusinessConflictException.class);
        }
    }
}
