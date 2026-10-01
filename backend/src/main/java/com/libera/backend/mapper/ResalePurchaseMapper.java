package com.libera.backend.mapper;

import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.dto.response.ResalePurchaseResponseDTO;
import com.libera.backend.util.StayDates;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = StayDates.class)
public interface ResalePurchaseMapper {

    @Mapping(source = "listing.id", target = "listingId")
    @Mapping(source = "buyer.id", target = "buyerId")
    @Mapping(source = "listing.originalBooking.hotel.name", target = "hotelName")
    @Mapping(source = "listing.originalBooking.roomType", target = "roomType")
    @Mapping(target = "nights", expression = "java(StayDates.nights(resalePurchase.getCheckIn(), resalePurchase.getCheckOut()))")
    ResalePurchaseResponseDTO toDto(ResalePurchase resalePurchase);
}
