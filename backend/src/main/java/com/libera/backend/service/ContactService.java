package com.libera.backend.service;

import com.libera.backend.dto.request.ContactRequestDTO;
import com.libera.backend.dto.response.ContactRequestResponseDTO;

import java.util.List;

public interface ContactService {
    ContactRequestResponseDTO createRequest(ContactRequestDTO request);

    List<ContactRequestResponseDTO> getAllRequests();
}
