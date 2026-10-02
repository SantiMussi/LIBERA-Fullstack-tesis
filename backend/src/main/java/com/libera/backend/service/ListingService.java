package com.libera.backend.service;

import com.libera.backend.dto.request.ListingCreateRequestDTO;
import com.libera.backend.dto.request.ListingSearchCriteria;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.dto.response.AdminListingResponseDTO;
import com.libera.backend.dto.response.ListingResponseDTO;

import java.util.List;

public interface ListingService {
    ListingResponseDTO createListing(ListingCreateRequestDTO request, Long authenticatedUserId);

    List<ListingResponseDTO> searchListings(ListingSearchCriteria criteria);

    /** Las publicaciones en revisión o rechazadas solo las ven su vendedor y los administradores. */
    ListingResponseDTO getListing(Long listingId, Long viewerId, boolean viewerIsAdmin);

    List<ListingResponseDTO> getListingsBySeller(Long sellerId);

    ListingResponseDTO cancelListing(Long listingId, Long authenticatedUserId);

    // --- Revisión (administradores) ---

    List<AdminListingResponseDTO> getListingsForReview(ListingStatus status);

    AdminListingResponseDTO approveListing(Long listingId);

    AdminListingResponseDTO rejectListing(Long listingId, String note);
}
