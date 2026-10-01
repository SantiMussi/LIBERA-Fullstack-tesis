package com.libera.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
    private String accessToken;
    private String tokenType;
    /** Segundos hasta que vence el token. */
    private long expiresIn;
    private UserResponseDTO user;
}
