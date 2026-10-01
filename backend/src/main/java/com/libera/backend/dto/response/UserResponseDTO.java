package com.libera.backend.dto.response;

import com.libera.backend.domain.enums.UserRole;
import lombok.Data;

@Data
public class UserResponseDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String documentNumber;
    private Boolean identityVerified;
    private UserRole role;
}
