package com.libera.backend.domain.entity;

import com.libera.backend.domain.enums.PartnershipModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hotels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "tax_id", nullable = false)
    private String taxId;

    private String city;

    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "partnership_model", nullable = false)
    private PartnershipModel partnershipModel;

    @Column(name = "pms_provider")
    private String pmsProvider;

    @Column(name = "markup_fee", precision = 5, scale = 2)
    private BigDecimal markupFee;
}
