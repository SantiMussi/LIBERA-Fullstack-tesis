package com.libera.backend.mapper;

import com.libera.backend.domain.entity.Listing;
import com.libera.backend.dto.response.ListingResponseDTO;
import com.libera.backend.util.StayDates;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = StayDates.class)
public interface ListingMapper {

    @Mapping(source = "originalBooking.id", target = "originalBookingId")
    @Mapping(source = "seller.id", target = "sellerId")
    @Mapping(source = "originalBooking.hotel.id", target = "hotelId")
    @Mapping(source = "originalBooking.hotel.slug", target = "hotelSlug")
    @Mapping(source = "originalBooking.hotel.name", target = "hotelName")
    @Mapping(source = "originalBooking.hotel.city", target = "hotelCity")
    @Mapping(source = "originalBooking.hotel.country", target = "hotelCountry")
    @Mapping(source = "originalBooking.roomType", target = "roomType")
    @Mapping(source = "originalBooking.checkIn", target = "checkIn")
    @Mapping(source = "originalBooking.checkOut", target = "checkOut")
    @Mapping(source = "originalBooking.totalAmountPaid", target = "originalPrice")
    @Mapping(target = "nights", expression = "java(StayDates.nights(listing.getOriginalBooking().getCheckIn(), listing.getOriginalBooking().getCheckOut()))")
    @Mapping(target = "soldRanges", ignore = true)
    ListingResponseDTO toDto(Listing listing);
}
