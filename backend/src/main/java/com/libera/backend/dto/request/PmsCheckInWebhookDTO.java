package com.libera.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PmsCheckInWebhookDTO {

    @NotNull(message = "Resale Purchase ID is required")
    private Long resalePurchaseId;

    @NotBlank(message = "PMS Confirmation code is required")
    private String pmsConfirmationCode;
}
