package com.lornalelcaj.imagejobs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class ImageJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    private String filename;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    private JobType type;

    private Instant createdAt;
    private String inputPath;
    private String outputPath;
    private String errorMessage;

    // Puzzle settings (only used for puzzle jobs)
    private Integer puzzleRows;
    private Integer puzzleCols;
    private Long seed;

    // Puzzle result geometry (filled in when the puzzle is done)
    private Integer cellWidth;
    private Integer cellHeight;
    private Integer margin;

    protected ImageJob() {} // required by JPA

    public ImageJob(String filename, String inputPath) {
        this(filename, inputPath, JobType.GRAYSCALE, null, null, null);
    }

    public ImageJob(String filename, String inputPath, JobType type,
                    Integer puzzleRows, Integer puzzleCols, Long seed) {
        this.filename = filename;
        this.inputPath = inputPath;
        this.type = type;
        this.puzzleRows = puzzleRows;
        this.puzzleCols = puzzleCols;
        this.seed = seed;
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

    public void markPuzzleDone(String previewPath, int cellWidth, int cellHeight, int margin) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.margin = margin;
        markDone(previewPath);
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = JobStatus.FAILED;
    }

    public void markCancelled() {
        this.status = JobStatus.CANCELLED;
    }

    @JsonIgnore
    public boolean isFinished() {
        return status == JobStatus.DONE
                || status == JobStatus.FAILED
                || status == JobStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public JobStatus getStatus() { return status; }
    public JobType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getPuzzleRows() { return puzzleRows; }
    public Integer getPuzzleCols() { return puzzleCols; }
    public Long getSeed() { return seed; }

    @JsonIgnore
    public String getInputPath() { return inputPath; }

    @JsonIgnore
    public String getOutputPath() { return outputPath; }

    @JsonIgnore
    public Integer getCellWidth() { return cellWidth; }

    @JsonIgnore
    public Integer getCellHeight() { return cellHeight; }

    @JsonIgnore
    public Integer getMargin() { return margin; }
}