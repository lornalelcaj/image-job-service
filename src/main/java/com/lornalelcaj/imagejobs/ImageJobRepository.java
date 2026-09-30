package com.lornalelcaj.imagejobs;

import org.springframework.data.jpa.repository.JpaRepository;


import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ImageJobRepository extends JpaRepository<ImageJob, Long> {

    List<ImageJob> findByStatusIn(Collection<JobStatus> statuses);

    List<ImageJob> findByCreatedAtBefore(Instant cutoff);
}