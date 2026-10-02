package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.ContactRequest;
import com.libera.backend.dto.request.ContactRequestDTO;
import com.libera.backend.dto.response.ContactRequestResponseDTO;
import com.libera.backend.repository.ContactRequestRepository;
import com.libera.backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactRequestRepository contactRequestRepository;

    @Override
    @Transactional
    public ContactRequestResponseDTO createRequest(ContactRequestDTO request) {
        ContactRequest saved = contactRequestRepository.save(ContactRequest.builder()
                .name(request.getName().trim())
                .hotelName(request.getHotel().trim())
                .email(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .phone(blankToNull(request.getPhone()))
                .rooms(blankToNull(request.getRooms()))
                .message(blankToNull(request.getMessage()))
                .createdAt(LocalDateTime.now())
                .build());
        log.info("New demo request {} from hotel {}", saved.getId(), saved.getHotelName());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactRequestResponseDTO> getAllRequests() {
        return contactRequestRepository.findAllByOrderByCreatedAtDesc().stream().map(ContactServiceImpl::toDto).toList();
    }

    private static ContactRequestResponseDTO toDto(ContactRequest c) {
        ContactRequestResponseDTO dto = new ContactRequestResponseDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setHotelName(c.getHotelName());
        dto.setEmail(c.getEmail());
        dto.setPhone(c.getPhone());
        dto.setRooms(c.getRooms());
        dto.setMessage(c.getMessage());
        dto.setCreatedAt(c.getCreatedAt());
        return dto;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
