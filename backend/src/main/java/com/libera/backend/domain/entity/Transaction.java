package com.libera.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resale_purchase_id", nullable = false)
    private ResalePurchase resalePurchase;

    @Column(name = "total_paid_by_buyer", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPaidByBuyer;

    @Column(name = "buyer_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal buyerFeeAmount;

    @Column(name = "seller_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal sellerFeeAmount;

    @Column(name = "seller_payout_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal sellerPayoutAmount;

    @Column(name = "hotel_revenue_share_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal hotelRevenueShareAmount;

    @Column(name = "libera_net_revenue", nullable = false, precision = 10, scale = 2)
    private BigDecimal liberaNetRevenue;
}
