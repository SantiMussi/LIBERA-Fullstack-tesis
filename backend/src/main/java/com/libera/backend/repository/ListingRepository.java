package com.libera.backend.repository;

import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.enums.ListingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    /** Bloquea la fila hasta el fin de la transacción: evita que dos compradores compren las mismas noches a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOriginalBookingIdAndStatusNot(Long originalBookingId, ListingStatus status);

    @EntityGraph(attributePaths = {"originalBooking.hotel", "seller"})
    List<Listing> findBySellerIdOrderByIdDesc(Long sellerId);

    @Override
    @EntityGraph(attributePaths = {"originalBooking.hotel", "seller"})
    List<Listing> findAll(Specification<Listing> spec, Sort sort);
}
