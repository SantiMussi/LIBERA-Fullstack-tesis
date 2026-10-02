package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.BookingVoucher;
import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.PartnershipModel;
import com.libera.backend.dto.request.OriginalBookingCreateRequestDTO;
import com.libera.backend.dto.response.OriginalBookingResponseDTO;
import com.libera.backend.exception.BusinessConflictException;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.mapper.OriginalBookingMapper;
import com.libera.backend.repository.BookingVoucherRepository;
import com.libera.backend.repository.HotelRepository;
import com.libera.backend.repository.ListingRepository;
import com.libera.backend.repository.OriginalBookingRepository;
import com.libera.backend.repository.UserRepository;
import com.libera.backend.service.OriginalBookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OriginalBookingServiceImpl implements OriginalBookingService {

    private final OriginalBookingRepository originalBookingRepository;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final OriginalBookingMapper originalBookingMapper;
    private final ListingRepository listingRepository;
    private final BookingVoucherRepository bookingVoucherRepository;

    private static final long MAX_VOUCHER_BYTES = 5L * 1024 * 1024;
    private static final Set<String> VOUCHER_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

    @Override
    @Transactional
    public OriginalBookingResponseDTO registerBooking(OriginalBookingCreateRequestDTO request, Long authenticatedUserId) {
        if (!request.getCheckOut().isAfter(request.getCheckIn())) {
            throw new IllegalArgumentException("Check-out must be after check-in.");
        }

        Hotel hotel = hotelRepository.findById(request.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));

        String confirmationCode = request.getPmsConfirmationCode().trim();
        if (originalBookingRepository.existsByHotelIdAndPmsConfirmationCode(hotel.getId(), confirmationCode)) {
            log.warn("User {} tried to register an already registered confirmation code for hotel {}", authenticatedUserId, hotel.getId());
            throw new BusinessConflictException("This booking has already been registered.");
        }

        User guest = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OriginalBooking booking = OriginalBooking.builder()
                .hotel(hotel)
                .originalGuest(guest)
                .pmsConfirmationCode(confirmationCode)
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .roomType(request.getRoomType().trim())
                .totalAmountPaid(request.getTotalAmountPaid())
                // En el Modelo Integración la tarifa no reembolsable del hotel es la Tarifa Revendible LIBERA
                .isLiberaRate(hotel.getPartnershipModel() == PartnershipModel.INTEGRATION)
                .build();

        booking = originalBookingRepository.save(booking);
        log.info("User {} registered original booking {} at hotel {}", authenticatedUserId, booking.getId(), hotel.getId());
        return originalBookingMapper.toDto(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OriginalBookingResponseDTO> getBookingsByGuest(Long guestId) {
        List<OriginalBooking> bookings = originalBookingRepository.findByOriginalGuestIdOrderByCheckInAsc(guestId);
        if (bookings.isEmpty()) {
            return List.of();
        }
        Set<Long> withVoucher = new HashSet<>(bookingVoucherRepository.findBookingIdsWithVoucher(
                bookings.stream().map(OriginalBooking::getId).toList()));
        // Publicación vigente de cada reserva: la más reciente que no esté cancelada
        Map<Long, ListingStatus> listingStatusByBooking = listingRepository.findBySellerIdOrderByIdDesc(guestId).stream()
                .filter(l -> l.getStatus() != ListingStatus.CANCELLED)
                .sorted(Comparator.comparing(Listing::getId).reversed())
                .collect(Collectors.toMap(l -> l.getOriginalBooking().getId(), Listing::getStatus, (newer, older) -> newer));
        return bookings.stream()
                .map(b -> {
                    OriginalBookingResponseDTO dto = originalBookingMapper.toDto(b);
                    dto.setHasVoucher(withVoucher.contains(b.getId()));
                    ListingStatus status = listingStatusByBooking.get(b.getId());
                    dto.setListingStatus(status == null ? null : status.name());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional
    public void uploadVoucher(Long bookingId, Long authenticatedUserId, String fileName, String contentType, byte[] data) {
        OriginalBooking booking = originalBookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Original booking not found"));
        if (!booking.getOriginalGuest().getId().equals(authenticatedUserId)) {
            throw new SecurityException("You can only upload the voucher of your own booking.");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("The voucher file is empty.");
        }
        if (data.length > MAX_VOUCHER_BYTES) {
            throw new IllegalArgumentException("The voucher file must be 5 MB or smaller.");
        }
        if (contentType == null || !VOUCHER_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("The voucher must be a PDF, JPG, PNG or WEBP file.");
        }

        BookingVoucher voucher = bookingVoucherRepository.findByOriginalBookingId(bookingId)
                .orElseGet(() -> BookingVoucher.builder().originalBooking(booking).build());
        String safeName = fileName == null || fileName.isBlank() ? "comprobante" : fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        voucher.setFileName(safeName.length() > 200 ? safeName.substring(safeName.length() - 200) : safeName);
        voucher.setContentType(contentType);
        voucher.setData(data);
        voucher.setUploadedAt(LocalDateTime.now());
        bookingVoucherRepository.save(voucher);
        log.info("User {} uploaded a voucher for booking {}", authenticatedUserId, bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingVoucher getVoucher(Long bookingId) {
        return bookingVoucherRepository.findByOriginalBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("This booking has no voucher"));
    }
}
