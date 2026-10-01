package com.libera.backend.mapper;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.enums.PartnershipModel;
import com.libera.backend.dto.response.HotelResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = PartnershipModel.class)
public interface HotelMapper {

    @Mapping(target = "liberaPartner", expression = "java(hotel.getPartnershipModel() != null && hotel.getPartnershipModel() != PartnershipModel.NONE)")
    @Mapping(target = "splitBookingAvailable", expression = "java(hotel.getPartnershipModel() == PartnershipModel.INTEGRATION)")
    HotelResponseDTO toDto(Hotel hotel);
}
