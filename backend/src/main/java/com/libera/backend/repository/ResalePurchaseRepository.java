package com.libera.backend.repository;

import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResalePurchaseRepository extends JpaRepository<ResalePurchase, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ResalePurchase p where p.id = :id")
    Optional<ResalePurchase> findByIdForUpdate(@Param("id") Long id);

    List<ResalePurchase> findByListingId(Long listingId);

    List<ResalePurchase> findByListingIdIn(Collection<Long> listingIds);

    @EntityGraph(attributePaths = {"listing.originalBooking.hotel", "buyer"})
    List<ResalePurchase> findByBuyerIdOrderByIdDesc(Long buyerId);

    @EntityGraph(attributePaths = {"listing.originalBooking.hotel", "buyer"})
    List<ResalePurchase> findByListingSellerIdOrderByIdDesc(Long sellerId);

    @EntityGraph(attributePaths = {"listing.originalBooking.hotel", "buyer"})
    List<ResalePurchase> findByStatusOrderByIdDesc(ResalePurchaseStatus status);

    @EntityGraph(attributePaths = {"listing.originalBooking.hotel", "buyer"})
    List<ResalePurchase> findAllByOrderByIdDesc();
}
