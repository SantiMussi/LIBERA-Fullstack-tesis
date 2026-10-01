package com.libera.backend.service;

import com.libera.backend.dto.request.OriginalBookingCreateRequestDTO;
import com.libera.backend.dto.response.OriginalBookingResponseDTO;

import java.util.List;

public interface OriginalBookingService {
    OriginalBookingResponseDTO registerBooking(OriginalBookingCreateRequestDTO request, Long authenticatedUserId);

    List<OriginalBookingResponseDTO> getBookingsByGuest(Long guestId);
}
