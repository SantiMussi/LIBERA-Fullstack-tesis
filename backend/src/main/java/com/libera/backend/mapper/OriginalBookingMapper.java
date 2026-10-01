package com.libera.backend.mapper;

import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.dto.response.OriginalBookingResponseDTO;
import com.libera.backend.util.StayDates;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = StayDates.class)
public interface OriginalBookingMapper {

    @Mapping(source = "hotel.id", target = "hotelId")
    @Mapping(source = "hotel.name", target = "hotelName")
    @Mapping(source = "originalGuest.id", target = "originalGuestId")
    @Mapping(target = "nights", expression = "java(StayDates.nights(originalBooking.getCheckIn(), originalBooking.getCheckOut()))")
    OriginalBookingResponseDTO toDto(OriginalBooking originalBooking);
}
