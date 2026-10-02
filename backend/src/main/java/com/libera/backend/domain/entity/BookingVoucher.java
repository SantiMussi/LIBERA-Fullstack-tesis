package com.libera.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Comprobante de la reserva original que sube el vendedor (PDF o imagen). Va en su propia tabla para que
 * el archivo no se lea cada vez que se carga una reserva: solo se trae cuando un administrador lo abre.
 */
@Entity
@Table(name = "booking_vouchers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_booking_id", nullable = false, unique = true)
    private OriginalBooking originalBooking;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** Máximo 5 MB (lo valida el servicio); el largo declarado hace que MySQL use MEDIUMBLOB y no TINYBLOB. */
    @Lob
    @Column(nullable = false, length = MAX_BYTES_COLUMN)
    @ToString.Exclude
    private byte[] data;

    public static final int MAX_BYTES_COLUMN = 16 * 1024 * 1024 - 1;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
