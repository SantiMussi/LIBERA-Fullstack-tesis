package com.libera.backend.service.impl;

import com.libera.backend.dto.response.HotelResponseDTO;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.mapper.HotelMapper;
import com.libera.backend.repository.HotelRepository;
import com.libera.backend.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    @Override
    @Transactional(readOnly = true)
    public List<HotelResponseDTO> searchHotels(String query, String city) {
        return hotelRepository.search(blankToNull(query), blankToNull(city)).stream()
                .map(hotelMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HotelResponseDTO getHotel(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .map(hotelMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
