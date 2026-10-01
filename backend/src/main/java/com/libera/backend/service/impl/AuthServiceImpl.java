package com.libera.backend.service.impl;

import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.UserRole;
import com.libera.backend.dto.request.LoginRequestDTO;
import com.libera.backend.dto.request.RegisterRequestDTO;
import com.libera.backend.dto.response.AuthResponseDTO;
import com.libera.backend.dto.response.UserResponseDTO;
import com.libera.backend.exception.BusinessConflictException;
import com.libera.backend.exception.ResourceNotFoundException;
import com.libera.backend.exception.UnauthorizedActionException;
import com.libera.backend.mapper.UserMapper;
import com.libera.backend.repository.UserRepository;
import com.libera.backend.security.JwtTokenService;
import com.libera.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessConflictException("An account with this email already exists.");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .documentNumber(request.getDocumentNumber().trim())
                .identityVerified(false)
                .role(UserRole.USER)
                .build();

        user = userRepository.save(user);
        log.info("User {} registered", user.getId());
        return authResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO request) {
        // Mismo mensaje para email inexistente y contraseña incorrecta: no revela qué emails están registrados
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedActionException("Invalid email or password."));
        return authResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getCurrentUser(Long authenticatedUserId) {
        return userRepository.findById(authenticatedUserId)
                .map(userMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private AuthResponseDTO authResponse(User user) {
        return new AuthResponseDTO(jwtTokenService.issueToken(user), "Bearer",
                jwtTokenService.getExpirationSeconds(), userMapper.toDto(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
