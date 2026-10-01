package com.libera.backend.service.port;

public interface PmsIntegrationPort {
    /**
     * Changes the guest name in the hotel's PMS.
     *
     * @param pmsConfirmationCode the original confirmation code
     * @param newGuestFirstName the new guest's first name
     * @param newGuestLastName the new guest's last name
     * @param newGuestDocument the new guest's document number
     */
    void changeGuestName(String pmsConfirmationCode, String newGuestFirstName, String newGuestLastName, String newGuestDocument);

    /**
     * Checks if this adapter supports the given PMS provider.
     *
     * @param pmsProvider the name of the PMS provider (e.g., "WINPAX", "ROIBACK")
     * @return true if supported
     */
    boolean supports(String pmsProvider);
}
