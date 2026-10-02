package com.libera.backend.repository;

import com.libera.backend.domain.entity.BookingVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingVoucherRepository extends JpaRepository<BookingVoucher, Long> {

    Optional<BookingVoucher> findByOriginalBookingId(Long originalBookingId);

    @Query("select v.originalBooking.id from BookingVoucher v where v.originalBooking.id in :ids")
    List<Long> findBookingIdsWithVoucher(@Param("ids") Collection<Long> bookingIds);

    /** Solo nombre y tipo, sin traer el archivo. */
    Optional<VoucherInfo> findInfoByOriginalBookingId(Long originalBookingId);

    interface VoucherInfo {
        String getFileName();

        String getContentType();
    }
}
