package com.lornalelcaj.imagejobs;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class ImageJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private Instant createdAt;

    protected ImageJob() {} // required by JPA

    public ImageJob(String filename) {
        this.filename = filename;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public JobStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}