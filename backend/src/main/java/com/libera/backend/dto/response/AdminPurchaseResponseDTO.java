package com.libera.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Compra vista desde el panel de administración: comprador, vendedor y, si ya se liquidó, el reparto del dinero. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPurchaseResponseDTO {
    private ResalePurchaseResponseDTO purchase;
    private String buyerName;
    private String buyerEmail;
    private String sellerName;
    private String sellerEmail;
    /** Null hasta que se confirma el check-in. */
    private TransactionResponseDTO transaction;
}
