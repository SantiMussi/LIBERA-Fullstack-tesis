package com.libera.backend.service;

import com.libera.backend.dto.request.LoginRequestDTO;
import com.libera.backend.dto.request.RegisterRequestDTO;
import com.libera.backend.dto.response.AuthResponseDTO;
import com.libera.backend.dto.response.UserResponseDTO;

public interface AuthService {
    AuthResponseDTO register(RegisterRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);

    UserResponseDTO getCurrentUser(Long authenticatedUserId);
}
