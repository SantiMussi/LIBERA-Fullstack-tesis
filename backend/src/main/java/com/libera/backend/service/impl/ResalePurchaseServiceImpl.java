package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.domain.entity.Transaction;
import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import com.libera.backend.dto.request.PmsCheckInWebhookDTO;
import com.libera.backend.dto.request.ResalePurchaseRequestDTO;
import com.libera.backend.dto.response.ResalePurchaseResponseDTO;
import com.libera.backend.exception.BusinessConflictException;
import com.libera.backend.exception.InvalidSplitBookingException;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.mapper.ResalePurchaseMapper;
import com.libera.backend.repository.ListingRepository;
import com.libera.backend.repository.ResalePurchaseRepository;
import com.libera.backend.repository.TransactionRepository;
import com.libera.backend.repository.UserRepository;
import com.libera.backend.service.ResalePurchaseService;
import com.libera.backend.service.port.PmsIntegrationPort;
import com.libera.backend.service.strategy.FeeCalculationResult;
import com.libera.backend.service.strategy.FeeCalculationStrategy;
import com.libera.backend.service.strategy.FeeStrategyFactory;
import com.libera.backend.util.StayDates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResalePurchaseServiceImpl implements ResalePurchaseService {

    private final ResalePurchaseRepository resalePurchaseRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ResalePurchaseMapper resalePurchaseMapper;

    private final List<PmsIntegrationPort> pmsIntegrationPorts;
    private final FeeStrategyFactory feeStrategyFactory;

    @Override
    @Transactional
    public ResalePurchaseResponseDTO createPurchase(ResalePurchaseRequestDTO request, Long authenticatedUserId) {
        log.info("User {} is attempting to purchase listing {}", authenticatedUserId, request.getListingId());

        // Pessimistic lock: two buyers cannot take the same nights concurrently
        Listing listing = listingRepository.findByIdForUpdate(request.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (listing.getSeller().getId().equals(authenticatedUserId)) {
            throw new IllegalArgumentException("You cannot purchase your own listing.");
        }
        if (listing.getStatus() != ListingStatus.ACTIVE && listing.getStatus() != ListingStatus.PARTIALLY_SOLD) {
            throw new BusinessConflictException("Listing is not available for purchase.");
        }

        OriginalBooking booking = listing.getOriginalBooking();
        LocalDate checkIn = request.getCheckIn();
        LocalDate checkOut = request.getCheckOut();

        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out must be after check-in.");
        }
        if (checkIn.isBefore(booking.getCheckIn()) || checkOut.isAfter(booking.getCheckOut())) {
            throw new IllegalArgumentException("Requested dates must be within the booking's stay ("
                    + booking.getCheckIn() + " to " + booking.getCheckOut() + ").");
        }

        boolean isFullStay = checkIn.equals(booking.getCheckIn()) && checkOut.equals(booking.getCheckOut());
        if (!isFullStay && !listing.getAllowsSplitBooking()) {
            throw new InvalidSplitBookingException("This listing does not allow split booking: the full stay must be purchased.");
        }

        List<ResalePurchase> existingPurchases = resalePurchaseRepository.findByListingId(listing.getId());
        boolean nightsTaken = existingPurchases.stream()
                .anyMatch(p -> StayDates.overlaps(p.getCheckIn(), p.getCheckOut(), checkIn, checkOut));
        if (nightsTaken) {
            throw new BusinessConflictException("Some of the requested nights have already been sold.");
        }

        User buyer = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));

        long totalNights = StayDates.nights(booking.getCheckIn(), booking.getCheckOut());
        long soldNightsBefore = existingPurchases.stream().mapToLong(p -> StayDates.nights(p.getCheckIn(), p.getCheckOut())).sum();
        long requestedNights = StayDates.nights(checkIn, checkOut);
        boolean completesListing = soldNightsBefore + requestedNights == totalNights;

        // Critical Rule: Initial state MUST be PAYMENT_HELD
        ResalePurchase purchase = ResalePurchase.builder()
                .listing(listing)
                .buyer(buyer)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .totalPrice(priceFor(listing, existingPurchases, requestedNights, totalNights, completesListing))
                .status(ResalePurchaseStatus.PAYMENT_HELD)
                .build();

        purchase = resalePurchaseRepository.save(purchase);
        log.info("Resale purchase {} created with status PAYMENT_HELD", purchase.getId());

        listing.setStatus(completesListing ? ListingStatus.SOLD_OUT : ListingStatus.PARTIALLY_SOLD);
        listingRepository.save(listing);

        // B2B Integration: Call PMS to change guest name
        Hotel hotel = booking.getHotel();
        String pmsProvider = hotel.getPmsProvider();

        PmsIntegrationPort pmsPort = pmsIntegrationPorts.stream()
                .filter(port -> port.supports(pmsProvider))
                .findFirst()
                .orElse(null);

        if (pmsPort != null) {
            try {
                String confirmCode = booking.getPmsConfirmationCode();
                pmsPort.changeGuestName(confirmCode, buyer.getFirstName(), buyer.getLastName(), buyer.getDocumentNumber());

                // Update status if successful
                purchase.setStatus(ResalePurchaseStatus.NAME_CHANGED);
                purchase = resalePurchaseRepository.save(purchase);
                log.info("Name changed successfully in PMS for purchase {}", purchase.getId());
            } catch (Exception e) {
                log.error("Failed to change guest name in PMS for purchase {}. Keeping status as PAYMENT_HELD.", purchase.getId(), e);
                // Could throw exception to rollback or keep it as PAYMENT_HELD for manual retry
            }
        } else {
            log.warn("No PMS integration port found for provider: {}", pmsProvider);
        }

        return resalePurchaseMapper.toDto(purchase);
    }

    /**
     * Estadía completa: el precio publicado. Split Booking: proporcional a las noches. La compra que completa
     * el listing se lleva el resto, así la suma de todas las compras da exactamente el precio publicado.
     */
    private static BigDecimal priceFor(Listing listing, List<ResalePurchase> existingPurchases,
                                       long requestedNights, long totalNights, boolean completesListing) {
        BigDecimal listedPrice = listing.getListedTotalPrice();
        if (completesListing) {
            BigDecimal alreadyCharged = existingPurchases.stream()
                    .map(ResalePurchase::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return listedPrice.subtract(alreadyCharged);
        }
        return listedPrice.multiply(BigDecimal.valueOf(requestedNights))
                .divide(BigDecimal.valueOf(totalNights), 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public void processCheckInWebhook(PmsCheckInWebhookDTO webhookDTO) {
        log.info("Processing Check-In Webhook for purchase {}", webhookDTO.getResalePurchaseId());

        ResalePurchase purchase = resalePurchaseRepository.findByIdForUpdate(webhookDTO.getResalePurchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Resale purchase not found"));

        // Ensure webhook is valid by verifying PMS confirmation code
        if (!purchase.getListing().getOriginalBooking().getPmsConfirmationCode().equals(webhookDTO.getPmsConfirmationCode())) {
            log.warn("Security Alert: Invalid PMS confirmation code provided in webhook for purchase {}", purchase.getId());
            throw new SecurityException("Invalid PMS confirmation code");
        }

        liquidate(purchase);
    }

    @Override
    @Transactional
    public ResalePurchaseResponseDTO confirmCheckInManually(Long purchaseId) {
        ResalePurchase purchase = resalePurchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Resale purchase not found"));
        log.info("Admin manually confirming check-in for purchase {}", purchaseId);
        liquidate(purchase);
        return resalePurchaseMapper.toDto(purchase);
    }

    /**
     * Libera el pago retenido: registra la transacción y pasa la compra a LIQUIDATED.
     * Es idempotente: si el PMS reintenta el webhook, una compra ya liquidada no se vuelve a cobrar.
     */
    private void liquidate(ResalePurchase purchase) {
        if (purchase.getStatus() == ResalePurchaseStatus.LIQUIDATED || transactionRepository.existsByResalePurchaseId(purchase.getId())) {
            log.info("Purchase {} was already liquidated; ignoring duplicate check-in confirmation.", purchase.getId());
            purchase.setStatus(ResalePurchaseStatus.LIQUIDATED);
            return;
        }

        // Release Payments: transition to CHECKED_IN
        purchase.setStatus(ResalePurchaseStatus.CHECKED_IN);
        resalePurchaseRepository.save(purchase);

        Hotel hotel = purchase.getListing().getOriginalBooking().getHotel();
        BigDecimal totalPaid = purchase.getTotalPrice();

        // Strategy Pattern for Fee Calculation
        FeeCalculationStrategy feeStrategy = feeStrategyFactory.getStrategy(hotel.getPartnershipModel());
        FeeCalculationResult feeResult = feeStrategy.calculateFees(totalPaid, hotel);

        // Record Transaction
        Transaction transaction = Transaction.builder()
                .resalePurchase(purchase)
                .totalPaidByBuyer(totalPaid)
                .buyerFeeAmount(feeResult.getBuyerFeeAmount())
                .sellerFeeAmount(feeResult.getSellerFeeAmount())
                .sellerPayoutAmount(feeResult.getSellerPayoutAmount())
                .hotelRevenueShareAmount(feeResult.getHotelRevenueShareAmount())
                .liberaNetRevenue(feeResult.getLiberaNetRevenue())
                .build();

        transactionRepository.save(transaction);

        // Finally liquidate
        purchase.setStatus(ResalePurchaseStatus.LIQUIDATED);
        resalePurchaseRepository.save(purchase);

        log.info("Check-in processed for purchase {}. Transaction recorded and funds liquidated.", purchase.getId());
    }

    @Override
    @Transactional
    public ResalePurchaseResponseDTO openDispute(Long purchaseId, Long authenticatedUserId) {
        ResalePurchase purchase = resalePurchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Resale purchase not found"));

        if (!purchase.getBuyer().getId().equals(authenticatedUserId)) {
            throw new SecurityException("Only the buyer can open a dispute for this purchase.");
        }
        if (purchase.getStatus() == ResalePurchaseStatus.LIQUIDATED) {
            throw new BusinessConflictException("The purchase has already been liquidated and cannot be disputed.");
        }
        if (purchase.getStatus() == ResalePurchaseStatus.DISPUTED) {
            throw new BusinessConflictException("A dispute is already open for this purchase.");
        }

        // The payment stays held until an administrator resolves the dispute
        purchase.setStatus(ResalePurchaseStatus.DISPUTED);
        log.warn("Buyer {} opened a dispute for purchase {}", authenticatedUserId, purchaseId);
        return resalePurchaseMapper.toDto(resalePurchaseRepository.save(purchase));
    }

    @Override
    @Transactional(readOnly = true)
    public ResalePurchaseResponseDTO getPurchase(Long purchaseId, Long authenticatedUserId, boolean isAdmin) {
        ResalePurchase purchase = resalePurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Resale purchase not found"));

        boolean isBuyer = purchase.getBuyer().getId().equals(authenticatedUserId);
        boolean isSeller = purchase.getListing().getSeller().getId().equals(authenticatedUserId);
        if (!isAdmin && !isBuyer && !isSeller) {
            throw new SecurityException("You do not have permission to view this purchase.");
        }
        return resalePurchaseMapper.toDto(purchase);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResalePurchaseResponseDTO> getPurchasesByBuyer(Long buyerId) {
        return resalePurchaseRepository.findByBuyerIdOrderByIdDesc(buyerId).stream()
                .map(resalePurchaseMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResalePurchaseResponseDTO> getSalesBySeller(Long sellerId) {
        return resalePurchaseRepository.findByListingSellerIdOrderByIdDesc(sellerId).stream()
                .map(resalePurchaseMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResalePurchaseResponseDTO> getAllPurchases(ResalePurchaseStatus status) {
        List<ResalePurchase> purchases = status == null
                ? resalePurchaseRepository.findAllByOrderByIdDesc()
                : resalePurchaseRepository.findByStatusOrderByIdDesc(status);
        return purchases.stream().map(resalePurchaseMapper::toDto).toList();
    }
}
