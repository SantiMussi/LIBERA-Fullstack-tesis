package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.PartnershipModel;
import com.libera.backend.dto.request.ListingCreateRequestDTO;
import com.libera.backend.dto.request.ListingSearchCriteria;
import com.libera.backend.dto.response.AdminListingResponseDTO;
import com.libera.backend.dto.response.DateRangeDTO;
import com.libera.backend.dto.response.ListingResponseDTO;
import com.libera.backend.exception.BusinessConflictException;
import com.libera.backend.exception.InvalidSplitBookingException;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.mapper.ListingMapper;
import com.libera.backend.repository.BookingVoucherRepository;
import com.libera.backend.repository.ListingRepository;
import com.libera.backend.repository.OriginalBookingRepository;
import com.libera.backend.repository.ResalePurchaseRepository;
import com.libera.backend.repository.UserRepository;
import com.libera.backend.service.ListingService;
import com.libera.backend.util.StayDates;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private static final List<ListingStatus> PURCHASABLE_STATUSES = List.of(ListingStatus.ACTIVE, ListingStatus.PARTIALLY_SOLD);

    private final ListingRepository listingRepository;
    private final OriginalBookingRepository originalBookingRepository;
    private final UserRepository userRepository;
    private final ResalePurchaseRepository resalePurchaseRepository;
    private final ListingMapper listingMapper;
    private final BookingVoucherRepository bookingVoucherRepository;

    @Override
    @Transactional
    public ListingResponseDTO createListing(ListingCreateRequestDTO request, Long authenticatedUserId) {
        log.info("User {} is attempting to create a listing for booking {}", authenticatedUserId, request.getOriginalBookingId());

        OriginalBooking originalBooking = originalBookingRepository.findByIdForUpdate(request.getOriginalBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Original booking not found"));

        // RBAC / Least Privilege: Only the original guest can list the booking
        if (!originalBooking.getOriginalGuest().getId().equals(authenticatedUserId)) {
            log.warn("Security violation: User {} attempted to list booking owned by User {}", authenticatedUserId, originalBooking.getOriginalGuest().getId());
            throw new SecurityException("You do not have permission to list this booking.");
        }

        if (originalBooking.getCheckIn().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot list a booking whose check-in date has already passed.");
        }

        // Business Rule: Split Booking only allowed if hotel is INTEGRATION
        if (request.getAllowsSplitBooking() && originalBooking.getHotel().getPartnershipModel() != PartnershipModel.INTEGRATION) {
            throw new InvalidSplitBookingException("Split bookings are only allowed for hotels with INTEGRATION partnership model.");
        }

        BigDecimal amountPaid = originalBooking.getTotalAmountPaid();
        if (request.getListedTotalPrice().compareTo(amountPaid) > 0) {
            throw new IllegalArgumentException("Listed price cannot exceed the amount originally paid for the booking.");
        }

        // Business Rule: a booking can only have one listing in force at a time (a cancelled one can be re-listed)
        if (listingRepository.existsByOriginalBookingIdAndStatusNot(originalBooking.getId(), ListingStatus.CANCELLED)) {
            throw new BusinessConflictException("This booking already has a listing.");
        }

        User seller = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        BigDecimal discountPercentage = amountPaid.subtract(request.getListedTotalPrice())
                .multiply(new BigDecimal("100"))
                .divide(amountPaid, 2, RoundingMode.HALF_UP);

        Listing listing = Listing.builder()
                .originalBooking(originalBooking)
                .seller(seller)
                .listedTotalPrice(request.getListedTotalPrice())
                .discountPercentage(discountPercentage)
                .allowsSplitBooking(request.getAllowsSplitBooking())
                // Toda publicación nueva la revisa un administrador antes de que aparezca en el catálogo
                .status(ListingStatus.PENDING_REVIEW)
                .build();

        Listing savedListing = listingRepository.save(listing);
        log.info("Listing {} successfully created", savedListing.getId());
        return toDto(savedListing, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListingResponseDTO> searchListings(ListingSearchCriteria criteria) {
        Specification<Listing> spec = (root, query, cb) -> {
            Join<Listing, OriginalBooking> booking = root.join("originalBooking");
            Join<OriginalBooking, Hotel> hotel = booking.join("hotel");
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(root.get("status").in(PURCHASABLE_STATUSES));
            predicates.add(cb.greaterThan(booking.get("checkOut"), LocalDate.now()));
            if (criteria.getCity() != null && !criteria.getCity().isBlank()) {
                predicates.add(cb.equal(cb.lower(hotel.get("city")), criteria.getCity().trim().toLowerCase()));
            }
            if (criteria.getHotelId() != null) {
                predicates.add(cb.equal(hotel.get("id"), criteria.getHotelId()));
            }
            if (criteria.getCheckIn() != null) {
                predicates.add(cb.lessThanOrEqualTo(booking.get("checkIn"), criteria.getCheckIn()));
            }
            if (criteria.getCheckOut() != null) {
                predicates.add(cb.greaterThanOrEqualTo(booking.get("checkOut"), criteria.getCheckOut()));
            }
            if (criteria.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("listedTotalPrice"), criteria.getMaxPrice()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<Listing> listings = listingRepository.findAll(spec, Sort.by("originalBooking.checkIn", "id"));
        Map<Long, List<ResalePurchase>> purchasesByListing = purchasesByListing(listings);

        return listings.stream()
                .filter(l -> requestedNightsAreFree(l, criteria, purchasesByListing.getOrDefault(l.getId(), List.of())))
                .map(l -> toDto(l, purchasesByListing.getOrDefault(l.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ListingResponseDTO getListing(Long listingId, Long viewerId, boolean viewerIsAdmin) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        boolean underReview = listing.getStatus() == ListingStatus.PENDING_REVIEW || listing.getStatus() == ListingStatus.REJECTED;
        if (underReview && !viewerIsAdmin && !listing.getSeller().getId().equals(viewerId)) {
            // Para el resto del público todavía no existe
            throw new ResourceNotFoundException("Listing not found");
        }
        return toDto(listing, resalePurchaseRepository.findByListingId(listingId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListingResponseDTO> getListingsBySeller(Long sellerId) {
        List<Listing> listings = listingRepository.findBySellerIdOrderByIdDesc(sellerId);
        Map<Long, List<ResalePurchase>> purchasesByListing = purchasesByListing(listings);
        return listings.stream()
                .map(l -> toDto(l, purchasesByListing.getOrDefault(l.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public ListingResponseDTO cancelListing(Long listingId, Long authenticatedUserId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        if (!listing.getSeller().getId().equals(authenticatedUserId)) {
            throw new SecurityException("You do not have permission to cancel this listing.");
        }
        if (listing.getStatus() == ListingStatus.CANCELLED) {
            throw new BusinessConflictException("Listing is already cancelled.");
        }
        if (listing.getStatus() != ListingStatus.ACTIVE && listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new BusinessConflictException("A listing with sold nights cannot be cancelled.");
        }

        listing.setStatus(ListingStatus.CANCELLED);
        log.info("Listing {} cancelled by seller {}", listingId, authenticatedUserId);
        return toDto(listingRepository.save(listing), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminListingResponseDTO> getListingsForReview(ListingStatus status) {
        return listingRepository.findByStatusOrderByIdAsc(status == null ? ListingStatus.PENDING_REVIEW : status).stream()
                .map(this::toAdminDto)
                .toList();
    }

    @Override
    @Transactional
    public AdminListingResponseDTO approveListing(Long listingId) {
        Listing listing = pendingListing(listingId);
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setReviewNote(null);
        log.info("Listing {} approved by an administrator", listingId);
        return toAdminDto(listingRepository.save(listing));
    }

    @Override
    @Transactional
    public AdminListingResponseDTO rejectListing(Long listingId, String note) {
        Listing listing = pendingListing(listingId);
        listing.setStatus(ListingStatus.REJECTED);
        listing.setReviewNote(note.trim());
        log.info("Listing {} rejected by an administrator", listingId);
        return toAdminDto(listingRepository.save(listing));
    }

    private Listing pendingListing(Long listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new BusinessConflictException("Only listings under review can be approved or rejected.");
        }
        return listing;
    }

    private AdminListingResponseDTO toAdminDto(Listing listing) {
        OriginalBooking booking = listing.getOriginalBooking();
        User seller = listing.getSeller();
        String voucherFileName = bookingVoucherRepository.findInfoByOriginalBookingId(booking.getId())
                .map(BookingVoucherRepository.VoucherInfo::getFileName)
                .orElse(null);
        return new AdminListingResponseDTO(toDto(listing, resalePurchaseRepository.findByListingId(listing.getId())),
                seller.getFirstName() + " " + seller.getLastName(), seller.getEmail(),
                booking.getPmsConfirmationCode(), booking.getTotalAmountPaid(), voucherFileName);
    }

    private Map<Long, List<ResalePurchase>> purchasesByListing(List<Listing> listings) {
        if (listings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = listings.stream().map(Listing::getId).toList();
        return resalePurchaseRepository.findByListingIdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getListing().getId()));
    }

    /** En un listing con Split Booking, si el comprador pidió fechas, esas noches no pueden estar vendidas. */
    private static boolean requestedNightsAreFree(Listing listing, ListingSearchCriteria criteria, List<ResalePurchase> purchases) {
        if (criteria.getCheckIn() == null || criteria.getCheckOut() == null) {
            return true;
        }
        return purchases.stream().noneMatch(p ->
                StayDates.overlaps(p.getCheckIn(), p.getCheckOut(), criteria.getCheckIn(), criteria.getCheckOut()));
    }

    private ListingResponseDTO toDto(Listing listing, List<ResalePurchase> purchases) {
        ListingResponseDTO dto = listingMapper.toDto(listing);
        dto.setSoldRanges(purchases.stream()
                .sorted(Comparator.comparing(ResalePurchase::getCheckIn))
                .map(p -> new DateRangeDTO(p.getCheckIn(), p.getCheckOut()))
                .toList());
        return dto;
    }
}
