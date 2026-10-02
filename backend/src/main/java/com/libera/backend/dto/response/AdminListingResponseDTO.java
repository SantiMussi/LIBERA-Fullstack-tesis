package com.libera.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Publicación vista desde el panel de administración: incluye los datos que se necesitan para revisarla. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminListingResponseDTO {
    private ListingResponseDTO listing;
    private String sellerName;
    private String sellerEmail;
    private String pmsConfirmationCode;
    private BigDecimal amountPaid;
    /** Nombre del comprobante subido por el vendedor, o null si no subió ninguno. */
    private String voucherFileName;
}
