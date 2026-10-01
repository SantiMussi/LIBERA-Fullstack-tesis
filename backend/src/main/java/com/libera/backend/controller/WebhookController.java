package com.libera.backend.controller;

import com.libera.backend.dto.request.PmsCheckInWebhookDTO;
import com.libera.backend.exception.UnauthorizedActionException;
import com.libera.backend.service.ResalePurchaseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final ResalePurchaseService resalePurchaseService;

    // B2B API Key shared with the PMS providers (configured in application.properties / env var)
    private final byte[] expectedApiKey;

    public WebhookController(ResalePurchaseService resalePurchaseService,
                             @Value("${libera.webhook.api-key}") String expectedApiKey) {
        this.resalePurchaseService = resalePurchaseService;
        this.expectedApiKey = expectedApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/pms/checkin")
    public ResponseEntity<Void> processPmsCheckIn(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @Valid @RequestBody PmsCheckInWebhookDTO request) {

        // Protect the webhook endpoint with API Key validation (constant-time comparison)
        if (apiKey == null || !MessageDigest.isEqual(apiKey.getBytes(StandardCharsets.UTF_8), expectedApiKey)) {
            throw new UnauthorizedActionException("Invalid or missing API Key for B2B Webhook");
        }

        resalePurchaseService.processCheckInWebhook(request);

        return ResponseEntity.ok().build();
    }
}
