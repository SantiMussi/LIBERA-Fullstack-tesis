package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.domain.entity.Transaction;
import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.PartnershipModel;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import com.libera.backend.dto.request.PmsCheckInWebhookDTO;
import com.libera.backend.dto.request.ResalePurchaseRequestDTO;
import com.libera.backend.dto.response.ResalePurchaseResponseDTO;
import com.libera.backend.exception.BusinessConflictException;
import com.libera.backend.exception.InvalidSplitBookingException;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.dto.response.AdminPurchaseResponseDTO;
import com.libera.backend.mapper.ResalePurchaseMapperImpl;
import com.libera.backend.mapper.TransactionMapperImpl;
import com.libera.backend.repository.ListingRepository;
import com.libera.backend.repository.ResalePurchaseRepository;
import com.libera.backend.repository.TransactionRepository;
import com.libera.backend.repository.UserRepository;
import com.libera.backend.service.port.PmsIntegrationPort;
import com.libera.backend.service.strategy.ConvenioFeeStrategy;
import com.libera.backend.service.strategy.FeeStrategyFactory;
import com.libera.backend.service.strategy.IntegrationFeeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResalePurchaseServiceImplTest {

    private static final long SELLER_ID = 1L;
    private static final long BUYER_ID = 2L;
    private static final long LISTING_ID = 30L;
    private static final long PURCHASE_ID = 40L;
    private static final String CONFIRMATION_CODE = "RB-12345";
    /** Estadía original: 4 noches, del día +10 al +14. */
    private static final LocalDate STAY_IN = LocalDate.now().plusDays(10);
    private static final LocalDate STAY_OUT = LocalDate.now().plusDays(14);

    @Mock
    private ResalePurchaseRepository resalePurchaseRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PmsIntegrationPort roibackPort;

    private ResalePurchaseServiceImpl service;
    private User seller;
    private User buyer;

    @BeforeEach
    void setUp() {
        FeeStrategyFactory factory = new FeeStrategyFactory(List.of(new IntegrationFeeStrategy(), new ConvenioFeeStrategy()));
        service = new ResalePurchaseServiceImpl(resalePurchaseRepository, listingRepository, userRepository,
                transactionRepository, new ResalePurchaseMapperImpl(), List.of(roibackPort), factory, new TransactionMapperImpl());

        seller = User.builder().id(SELLER_ID).firstName("Ana").lastName("Pérez").build();
        buyer = User.builder().id(BUYER_ID).firstName("Juan").lastName("Gómez").documentNumber("30111222").build();
        lenient().when(roibackPort.supports(anyString())).thenAnswer(inv -> "ROIBACK".equalsIgnoreCase(inv.getArgument(0)));
    }

    private Listing listing(PartnershipModel model, String pmsProvider, String price, boolean split) {
        Hotel hotel = Hotel.builder().id(7L).name("Hotel Boutique").partnershipModel(model).pmsProvider(pmsProvider)
                .markupFee(new BigDecimal("20.00")).build();
        OriginalBooking booking = OriginalBooking.builder().id(100L).hotel(hotel).pmsConfirmationCode(CONFIRMATION_CODE)
                .checkIn(STAY_IN).checkOut(STAY_OUT).roomType("Doble").build();
        return Listing.builder().id(LISTING_ID).originalBooking(booking).seller(seller)
                .listedTotalPrice(new BigDecimal(price)).discountPercentage(new BigDecimal("20.00"))
                .allowsSplitBooking(split).status(ListingStatus.ACTIVE).build();
    }

    private static ResalePurchase existingPurchase(Listing listing, LocalDate in, LocalDate out, String price) {
        return ResalePurchase.builder().id(99L).listing(listing).checkIn(in).checkOut(out)
                .totalPrice(new BigDecimal(price)).status(ResalePurchaseStatus.NAME_CHANGED).build();
    }

    @Nested
    class CreatePurchase {

        /** Guarda una copia del estado en cada save(), para poder ver la secuencia de estados. */
        private final List<ResalePurchaseStatus> savedStatuses = new ArrayList<>();

        @BeforeEach
        void stubSave() {
            lenient().when(resalePurchaseRepository.save(any(ResalePurchase.class))).thenAnswer(inv -> {
                ResalePurchase p = inv.getArgument(0);
                if (p.getId() == null) {
                    p.setId(PURCHASE_ID);
                }
                savedStatuses.add(p.getStatus());
                return p;
            });
            lenient().when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));
        }

        private ResalePurchaseRequestDTO request(LocalDate in, LocalDate out) {
            ResalePurchaseRequestDTO dto = new ResalePurchaseRequestDTO();
            dto.setListingId(LISTING_ID);
            dto.setCheckIn(in);
            dto.setCheckOut(out);
            return dto;
        }

        private ResalePurchaseRequestDTO fullStay() {
            return request(STAY_IN, STAY_OUT);
        }

        private Listing givenListing(Listing listing, ResalePurchase... existing) {
            when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing));
            lenient().when(resalePurchaseRepository.findByListingId(LISTING_ID)).thenReturn(List.of(existing));
            return listing;
        }

        @Test
        void changesGuestNameInPmsWhenProviderIsSupported() {
            Listing listing = givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", false));

            ResalePurchaseResponseDTO response = service.createPurchase(fullStay(), BUYER_ID);

            verify(roibackPort).changeGuestName(CONFIRMATION_CODE, "Juan", "Gómez", "30111222");
            assertThat(savedStatuses).containsExactly(ResalePurchaseStatus.PAYMENT_HELD, ResalePurchaseStatus.NAME_CHANGED);
            assertThat(response.getId()).isEqualTo(PURCHASE_ID);
            assertThat(response.getListingId()).isEqualTo(LISTING_ID);
            assertThat(response.getBuyerId()).isEqualTo(BUYER_ID);
            assertThat(response.getNights()).isEqualTo(4);
            assertThat(response.getTotalPrice()).isEqualByComparingTo("1050.00");
            // Garantía de Traspaso del 7,5% encima del precio
            assertThat(response.getBuyerFee()).isEqualByComparingTo("78.75");
            assertThat(response.getTotalPaid()).isEqualByComparingTo("1128.75");
            assertThat(response.getStatus()).isEqualTo(ResalePurchaseStatus.NAME_CHANGED);
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
        }

        @Test
        void staysPaymentHeldWhenNoPmsAdapterSupportsTheProvider() {
            givenListing(listing(PartnershipModel.CONVENIO, "OPERA", "1000.00", false));

            ResalePurchaseResponseDTO response = service.createPurchase(fullStay(), BUYER_ID);

            verify(roibackPort, never()).changeGuestName(any(), any(), any(), any());
            assertThat(response.getStatus()).isEqualTo(ResalePurchaseStatus.PAYMENT_HELD);
        }

        @Test
        void staysPaymentHeldWhenHotelHasNoPmsProvider() {
            givenListing(listing(PartnershipModel.NONE, null, "1000.00", false));

            assertThat(service.createPurchase(fullStay(), BUYER_ID).getStatus()).isEqualTo(ResalePurchaseStatus.PAYMENT_HELD);
        }

        @Test
        void staysPaymentHeldWhenPmsCallFails() {
            givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", false));
            doThrow(new RuntimeException("PMS caído")).when(roibackPort).changeGuestName(any(), any(), any(), any());

            ResalePurchaseResponseDTO response = service.createPurchase(fullStay(), BUYER_ID);

            assertThat(response.getStatus()).isEqualTo(ResalePurchaseStatus.PAYMENT_HELD);
            assertThat(savedStatuses).containsExactly(ResalePurchaseStatus.PAYMENT_HELD);
        }

        @Test
        void rejectsUnknownListing() {
            when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPurchase(fullStay(), BUYER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Listing not found");
            verify(resalePurchaseRepository, never()).save(any());
        }

        @Test
        void rejectsUnknownBuyer() {
            givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", false));
            when(userRepository.findById(BUYER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPurchase(fullStay(), BUYER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Buyer not found");
        }

        @Test
        void sellerCannotBuyOwnListing() {
            givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", false));

            assertThatThrownBy(() -> service.createPurchase(fullStay(), SELLER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("own listing");
            verify(resalePurchaseRepository, never()).save(any());
        }

        @Test
        void rejectsListingThatIsNotForSale() {
            Listing listing = listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", false);
            for (ListingStatus status : List.of(ListingStatus.SOLD_OUT, ListingStatus.CANCELLED)) {
                listing.setStatus(status);
                givenListing(listing);

                assertThatThrownBy(() -> service.createPurchase(fullStay(), BUYER_ID))
                        .isInstanceOf(BusinessConflictException.class);
            }
            verify(resalePurchaseRepository, never()).save(any());
        }

        @Test
        void rejectsCheckOutNotAfterCheckIn() {
            givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", true));

            assertThatThrownBy(() -> service.createPurchase(request(STAY_IN.plusDays(2), STAY_IN.plusDays(1)), BUYER_ID))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.createPurchase(request(STAY_IN, STAY_IN), BUYER_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsDatesOutsideOriginalStay() {
            givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", true));

            assertThatThrownBy(() -> service.createPurchase(request(STAY_IN.minusDays(1), STAY_OUT), BUYER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("within the booking");
            assertThatThrownBy(() -> service.createPurchase(request(STAY_IN, STAY_OUT.plusDays(1)), BUYER_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsPartialStayWhenSplitNotAllowed() {
            givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", false));

            assertThatThrownBy(() -> service.createPurchase(request(STAY_IN, STAY_IN.plusDays(2)), BUYER_ID))
                    .isInstanceOf(InvalidSplitBookingException.class);
        }

        @Test
        void splitPurchaseIsProratedAndLeavesListingPartiallySold() {
            Listing listing = givenListing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1000.00", true));

            // 1 de 4 noches -> 250
            ResalePurchaseResponseDTO response = service.createPurchase(request(STAY_IN, STAY_IN.plusDays(1)), BUYER_ID);

            assertThat(response.getNights()).isEqualTo(1);
            assertThat(response.getTotalPrice()).isEqualByComparingTo("250.00");
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.PARTIALLY_SOLD);
        }

        @Test
        void lastSplitPurchaseTakesTheRemainderSoTotalsMatchListedPrice() {
            Listing listing = listing(PartnershipModel.INTEGRATION, "ROIBACK", "1000.00", true);
            listing.setListedTotalPrice(new BigDecimal("1000.00"));
            listing.getOriginalBooking().setCheckOut(STAY_IN.plusDays(3)); // 3 noches: 333.33 c/u no cierra
            listing.setStatus(ListingStatus.PARTIALLY_SOLD);
            givenListing(listing,
                    existingPurchase(listing, STAY_IN, STAY_IN.plusDays(1), "333.33"),
                    existingPurchase(listing, STAY_IN.plusDays(1), STAY_IN.plusDays(2), "333.33"));

            ResalePurchaseResponseDTO response = service.createPurchase(request(STAY_IN.plusDays(2), STAY_IN.plusDays(3)), BUYER_ID);

            assertThat(response.getTotalPrice()).isEqualByComparingTo("333.34");
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
        }

        @Test
        void rejectsNightsAlreadySold() {
            Listing listing = listing(PartnershipModel.INTEGRATION, "ROIBACK", "1000.00", true);
            listing.setStatus(ListingStatus.PARTIALLY_SOLD);
            givenListing(listing, existingPurchase(listing, STAY_IN, STAY_IN.plusDays(2), "500.00"));

            assertThatThrownBy(() -> service.createPurchase(request(STAY_IN.plusDays(1), STAY_IN.plusDays(3)), BUYER_ID))
                    .isInstanceOf(BusinessConflictException.class);
            verify(resalePurchaseRepository, never()).save(any());
        }

        @Test
        void allowsAdjacentNightsAfterAnotherSplitPurchase() {
            Listing listing = listing(PartnershipModel.INTEGRATION, "ROIBACK", "1000.00", true);
            listing.setStatus(ListingStatus.PARTIALLY_SOLD);
            givenListing(listing, existingPurchase(listing, STAY_IN, STAY_IN.plusDays(2), "500.00"));

            ResalePurchaseResponseDTO response = service.createPurchase(request(STAY_IN.plusDays(2), STAY_OUT), BUYER_ID);

            assertThat(response.getTotalPrice()).isEqualByComparingTo("500.00");
            assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
        }
    }

    @Nested
    class CheckIn {

        private ResalePurchase purchase;

        @BeforeEach
        void setUpPurchase() {
            purchase = ResalePurchase.builder().id(PURCHASE_ID).buyer(buyer)
                    .listing(listing(PartnershipModel.INTEGRATION, "ROIBACK", "1050.00", false))
                    .checkIn(STAY_IN).checkOut(STAY_OUT).totalPrice(new BigDecimal("1050.00"))
                    .status(ResalePurchaseStatus.NAME_CHANGED).build();
        }

        private PmsCheckInWebhookDTO webhook(String code) {
            PmsCheckInWebhookDTO dto = new PmsCheckInWebhookDTO();
            dto.setResalePurchaseId(PURCHASE_ID);
            dto.setPmsConfirmationCode(code);
            return dto;
        }

        @Test
        void recordsTransactionAndLiquidates() {
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(purchase));

            service.processCheckInWebhook(webhook(CONFIRMATION_CODE));

            ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(tx.capture());
            Transaction t = tx.getValue();
            assertThat(t.getResalePurchase()).isSameAs(purchase);
            // precio 1050 + Garantía 7,5% = 1128,75; vendedor paga 7,5%; el hotel se lleva el 20% de lo que cobra LIBERA
            assertThat(t.getTotalPaidByBuyer()).isEqualByComparingTo("1128.75");
            assertThat(t.getBuyerFeeAmount()).isEqualByComparingTo("78.75");
            assertThat(t.getSellerFeeAmount()).isEqualByComparingTo("78.75");
            assertThat(t.getSellerPayoutAmount()).isEqualByComparingTo("971.25");
            assertThat(t.getHotelRevenueShareAmount()).isEqualByComparingTo("31.50");
            assertThat(t.getLiberaNetRevenue()).isEqualByComparingTo("126.00");
            assertThat(purchase.getStatus()).isEqualTo(ResalePurchaseStatus.LIQUIDATED);
        }

        @Test
        void feesAreCalculatedOnThePurchasePriceNotTheWholeListing() {
            purchase.setTotalPrice(new BigDecimal("525.00")); // compra Split de la mitad de las noches
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(purchase));

            service.processCheckInWebhook(webhook(CONFIRMATION_CODE));

            ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(tx.capture());
            assertThat(tx.getValue().getTotalPaidByBuyer()).isEqualByComparingTo("564.38");
            assertThat(tx.getValue().getSellerPayoutAmount()).isEqualByComparingTo("485.62");
        }

        @Test
        void usesConvenioFeesForConvenioHotel() {
            purchase.setListing(listing(PartnershipModel.CONVENIO, "WINPAX", "1000.00", false));
            purchase.setTotalPrice(new BigDecimal("1000.00"));
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(purchase));

            service.processCheckInWebhook(webhook(CONFIRMATION_CODE));

            ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(tx.capture());
            assertThat(tx.getValue().getSellerPayoutAmount()).isEqualByComparingTo("925.00");
            assertThat(tx.getValue().getHotelRevenueShareAmount()).isEqualByComparingTo("0");
            assertThat(tx.getValue().getLiberaNetRevenue()).isEqualByComparingTo("150.00");
        }

        @Test
        void duplicateWebhookIsIgnored() {
            purchase.setStatus(ResalePurchaseStatus.LIQUIDATED);
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(purchase));

            service.processCheckInWebhook(webhook(CONFIRMATION_CODE));

            verify(transactionRepository, never()).save(any());
            assertThat(purchase.getStatus()).isEqualTo(ResalePurchaseStatus.LIQUIDATED);
        }

        @Test
        void rejectsWrongConfirmationCode() {
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(purchase));

            assertThatThrownBy(() -> service.processCheckInWebhook(webhook("CODIGO-FALSO")))
                    .isInstanceOf(SecurityException.class);
            verify(transactionRepository, never()).save(any());
            assertThat(purchase.getStatus()).isEqualTo(ResalePurchaseStatus.NAME_CHANGED);
        }

        @Test
        void rejectsUnknownPurchase() {
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.processCheckInWebhook(webhook(CONFIRMATION_CODE)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Resale purchase not found");
        }

        @Test
        void adminCanConfirmCheckInManually() {
            purchase.setStatus(ResalePurchaseStatus.PAYMENT_HELD);
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(purchase));

            AdminPurchaseResponseDTO response = service.confirmCheckInManually(PURCHASE_ID);

            assertThat(response.getPurchase().getStatus()).isEqualTo(ResalePurchaseStatus.LIQUIDATED);
            assertThat(response.getBuyerName()).isEqualTo("Juan Gómez");
            assertThat(response.getSellerName()).isEqualTo("Ana Pérez");
            assertThat(response.getTransaction().getSellerPayoutAmount()).isEqualByComparingTo("971.25");
            verify(transactionRepository).save(any(Transaction.class));
        }
    }

    @Nested
    class Dispute {

        private ResalePurchase purchase;

        @BeforeEach
        void setUpPurchase() {
            purchase = ResalePurchase.builder().id(PURCHASE_ID).buyer(buyer)
                    .listing(listing(PartnershipModel.CONVENIO, null, "1000.00", false))
                    .checkIn(STAY_IN).checkOut(STAY_OUT).totalPrice(new BigDecimal("1000.00"))
                    .status(ResalePurchaseStatus.PAYMENT_HELD).build();
            when(resalePurchaseRepository.findByIdForUpdate(PURCHASE_ID)).thenReturn(Optional.of(purchase));
        }

        @Test
        void buyerCanOpenDispute() {
            when(resalePurchaseRepository.save(any(ResalePurchase.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.openDispute(PURCHASE_ID, BUYER_ID).getStatus()).isEqualTo(ResalePurchaseStatus.DISPUTED);
        }

        @Test
        void onlyBuyerCanOpenDispute() {
            assertThatThrownBy(() -> service.openDispute(PURCHASE_ID, SELLER_ID)).isInstanceOf(SecurityException.class);
        }

        @Test
        void cannotDisputeLiquidatedPurchase() {
            purchase.setStatus(ResalePurchaseStatus.LIQUIDATED);

            assertThatThrownBy(() -> service.openDispute(PURCHASE_ID, BUYER_ID)).isInstanceOf(BusinessConflictException.class);
        }
    }
}
