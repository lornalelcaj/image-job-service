package com.lornalelcaj.imagejobs;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String inputPath;
    private String outputPath;
    private String errorMessage;

    protected ImageJob() {} // required by JPA

    public ImageJob(String filename, String inputPath) {
        this.filename = filename;
        this.inputPath = inputPath;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markRunning() {
        this.status = JobStatus.RUNNING;
    }

    public void markDone(String outputPath) {
        this.outputPath = outputPath;
        this.status = JobStatus.DONE;
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = JobStatus.FAILED;
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public JobStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getErrorMessage() { return errorMessage; }

    @JsonIgnore
    public String getInputPath() { return inputPath; }

    @JsonIgnore
    public String getOutputPath() { return outputPath; }
}