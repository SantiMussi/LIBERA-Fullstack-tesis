package com.libera.backend.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Helpers para rangos de estadía [checkIn, checkOut): la noche del checkOut no se cuenta. */
public final class StayDates {

    private StayDates() {
    }

    public static Long nights(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public static boolean overlaps(LocalDate aIn, LocalDate aOut, LocalDate bIn, LocalDate bOut) {
        return aIn.isBefore(bOut) && bIn.isBefore(aOut);
    }
}
