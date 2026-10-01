package com.libera.backend.domain.entity;

import com.libera.backend.domain.enums.ListingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "listings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ManyToOne: una reserva cancelada se puede volver a publicar (solo puede haber una publicación vigente a la vez)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_booking_id", nullable = false)
    private OriginalBooking originalBooking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "listed_total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal listedTotalPrice;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "allows_split_booking", nullable = false)
    private Boolean allowsSplitBooking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;
}
