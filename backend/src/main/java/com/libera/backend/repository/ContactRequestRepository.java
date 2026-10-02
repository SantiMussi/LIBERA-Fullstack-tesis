package com.libera.backend.repository;

import com.libera.backend.domain.entity.ContactRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {

    List<ContactRequest> findAllByOrderByCreatedAtDesc();
}
