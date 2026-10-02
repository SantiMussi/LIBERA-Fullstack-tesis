package com.libera.backend.service;

import com.libera.backend.domain.entity.BookingVoucher;
import com.libera.backend.dto.request.OriginalBookingCreateRequestDTO;
import com.libera.backend.dto.response.OriginalBookingResponseDTO;

import java.util.List;

public interface OriginalBookingService {
    OriginalBookingResponseDTO registerBooking(OriginalBookingCreateRequestDTO request, Long authenticatedUserId);

    List<OriginalBookingResponseDTO> getBookingsByGuest(Long guestId);

    /** El titular sube (o reemplaza) el comprobante de su reserva: PDF, JPG, PNG o WEBP de hasta 5 MB. */
    void uploadVoucher(Long bookingId, Long authenticatedUserId, String fileName, String contentType, byte[] data);

    /** Solo administradores (lo restringe SecurityConfig). */
    BookingVoucher getVoucher(Long bookingId);
}
