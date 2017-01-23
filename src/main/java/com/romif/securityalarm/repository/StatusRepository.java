package com.romif.securityalarm.repository;

import com.romif.securityalarm.domain.Status;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Spring Data JPA repository for the Status entity.
 */
@SuppressWarnings("unused")
public interface StatusRepository extends JpaRepository<Status,Long> {

    Page<Status> findByCreatedDateAfterAndCreatedDateBefore(ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);

    Optional<Status> findFirstByCreatedBy(String createdBy);
}
