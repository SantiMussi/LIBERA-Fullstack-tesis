package com.libera.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "original_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OriginalBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_guest_id", nullable = false)
    private User originalGuest;

    @Column(name = "pms_confirmation_code", nullable = false)
    private String pmsConfirmationCode;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "room_type", nullable = false)
    private String roomType;

    @Column(name = "total_amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmountPaid;

    @Column(name = "is_libera_rate", nullable = false)
    private Boolean isLiberaRate;
}
