package com.libera.backend.repository;

import com.libera.backend.domain.entity.OriginalBooking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OriginalBookingRepository extends JpaRepository<OriginalBooking, Long> {

    /** Bloquea la reserva para que no se creen dos publicaciones vigentes en paralelo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from OriginalBooking b where b.id = :id")
    Optional<OriginalBooking> findByIdForUpdate(@Param("id") Long id);

    boolean existsByHotelIdAndPmsConfirmationCode(Long hotelId, String pmsConfirmationCode);

    @EntityGraph(attributePaths = {"hotel"})
    List<OriginalBooking> findByOriginalGuestIdOrderByCheckInAsc(Long originalGuestId);
}
