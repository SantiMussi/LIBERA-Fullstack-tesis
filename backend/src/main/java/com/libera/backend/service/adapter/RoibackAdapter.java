package com.libera.backend.service.adapter;

import com.libera.backend.service.port.PmsIntegrationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RoibackAdapter implements PmsIntegrationPort {

    @Override
    public void changeGuestName(String pmsConfirmationCode, String newGuestFirstName, String newGuestLastName, String newGuestDocument) {
        log.info("ROIBACK API Call: Changing guest name for booking {} to {} {} (Doc: {})", 
                pmsConfirmationCode, newGuestFirstName, newGuestLastName, newGuestDocument);
        // Simulate HTTP call to Roiback API
    }

    @Override
    public boolean supports(String pmsProvider) {
        return "ROIBACK".equalsIgnoreCase(pmsProvider);
    }
}
