package com.libera.backend.mapper;

import com.libera.backend.domain.entity.User;
import com.libera.backend.dto.response.UserResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    UserResponseDTO toDto(User user);
}
