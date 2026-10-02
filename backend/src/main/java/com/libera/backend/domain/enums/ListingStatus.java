package com.libera.backend.domain.enums;

public enum ListingStatus {
    /** Recién publicada: un administrador revisa la reserva y el comprobante antes de que aparezca en el catálogo. */
    PENDING_REVIEW,
    ACTIVE,
    PARTIALLY_SOLD,
    SOLD_OUT,
    CANCELLED,
    /** El administrador no pudo validar la reserva; no se puede volver a publicar. */
    REJECTED
}
