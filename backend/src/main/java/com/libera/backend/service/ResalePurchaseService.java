package com.libera.backend.service;

import com.libera.backend.domain.enums.ResalePurchaseStatus;
import com.libera.backend.dto.request.PmsCheckInWebhookDTO;
import com.libera.backend.dto.request.ResalePurchaseRequestDTO;
import com.libera.backend.dto.response.AdminPurchaseResponseDTO;
import com.libera.backend.dto.response.ResalePurchaseResponseDTO;

import java.util.List;

public interface ResalePurchaseService {
    ResalePurchaseResponseDTO createPurchase(ResalePurchaseRequestDTO request, Long authenticatedUserId);

    void processCheckInWebhook(PmsCheckInWebhookDTO webhookDTO);

    /** Confirmación manual del check-in (hoteles sin PMS integrado). Solo administradores. */
    AdminPurchaseResponseDTO confirmCheckInManually(Long purchaseId);

    ResalePurchaseResponseDTO openDispute(Long purchaseId, Long authenticatedUserId);

    ResalePurchaseResponseDTO getPurchase(Long purchaseId, Long authenticatedUserId, boolean isAdmin);

    List<ResalePurchaseResponseDTO> getPurchasesByBuyer(Long buyerId);

    List<ResalePurchaseResponseDTO> getSalesBySeller(Long sellerId);

    List<AdminPurchaseResponseDTO> getAllPurchases(ResalePurchaseStatus status);
}
