package com.libera.backend.service;

import com.libera.backend.dto.response.HotelResponseDTO;

import java.util.List;

public interface HotelService {
    List<HotelResponseDTO> searchHotels(String query, String city);

    HotelResponseDTO getHotel(Long hotelId);
}
