package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.PartnershipModel;
import com.libera.backend.dto.request.OriginalBookingCreateRequestDTO;
import com.libera.backend.dto.response.OriginalBookingResponseDTO;
import com.libera.backend.exception.BusinessConflictException;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.mapper.OriginalBookingMapper;
import com.libera.backend.repository.HotelRepository;
import com.libera.backend.repository.OriginalBookingRepository;
import com.libera.backend.repository.UserRepository;
import com.libera.backend.service.OriginalBookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OriginalBookingServiceImpl implements OriginalBookingService {

    private final OriginalBookingRepository originalBookingRepository;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final OriginalBookingMapper originalBookingMapper;

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
        return originalBookingRepository.findByOriginalGuestIdOrderByCheckInAsc(guestId).stream()
                .map(originalBookingMapper::toDto)
                .toList();
    }
}
