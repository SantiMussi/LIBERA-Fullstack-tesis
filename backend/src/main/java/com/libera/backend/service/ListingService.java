package com.libera.backend.service;

import com.libera.backend.dto.request.ListingCreateRequestDTO;
import com.libera.backend.dto.request.ListingSearchCriteria;
import com.libera.backend.dto.response.ListingResponseDTO;

import java.util.List;

public interface ListingService {
    ListingResponseDTO createListing(ListingCreateRequestDTO request, Long authenticatedUserId);

    List<ListingResponseDTO> searchListings(ListingSearchCriteria criteria);

    ListingResponseDTO getListing(Long listingId);

    List<ListingResponseDTO> getListingsBySeller(Long sellerId);

    ListingResponseDTO cancelListing(Long listingId, Long authenticatedUserId);
}
