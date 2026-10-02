package com.libera.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContactRequestResponseDTO {
    private Long id;
    private String name;
    private String hotelName;
    private String email;
    private String phone;
    private String rooms;
    private String message;
    private LocalDateTime createdAt;
}
