package com.libera.backend.repository;

import com.libera.backend.domain.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    @Query("""
            select h from Hotel h
            where (:q is null
                   or lower(h.name) like lower(concat('%', :q, '%'))
                   or lower(h.city) like lower(concat('%', :q, '%'))
                   or lower(h.country) like lower(concat('%', :q, '%')))
              and (:city is null or lower(h.city) = lower(:city))
            order by h.name
            """)
    List<Hotel> search(@Param("q") String q, @Param("city") String city);
}
