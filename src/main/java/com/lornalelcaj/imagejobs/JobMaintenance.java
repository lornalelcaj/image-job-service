package com.lornalelcaj.imagejobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class JobMaintenance {

    private static final Logger log = LoggerFactory.getLogger(JobMaintenance.class);

    private final ImageJobRepository repository;
    private final Path storageDir;
    private final Duration retention;
    private final Instant startedAt = Instant.now();

    public JobMaintenance(ImageJobRepository repository,
                          @Value("${imagejobs.storage-dir:storage}") String storageDir,
                          @Value("${imagejobs.retention:PT24H}") Duration retention) {
        this.repository = repository;
        this.storageDir = Path.of(storageDir).toAbsolutePath();
        this.retention = retention;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedJobs() {
        List<ImageJob> interrupted = repository
                .findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING))
                .stream()
                .filter(job -> job.getCreatedAt().isBefore(startedAt))
                .toList();

        for (ImageJob job : interrupted) {
            job.markFailed("Interrupted by a server restart");
        }
        repository.saveAll(interrupted);

        if (!interrupted.isEmpty()) {
            log.info("Marked {} interrupted jobs as failed", interrupted.size());
        }
    }

    @Scheduled(fixedDelayString = "${imagejobs.cleanup-interval:PT1H}", initialDelayString = "PT1M")
    public void deleteOldJobs() {
        Instant cutoff = Instant.now().minus(retention);
        List<ImageJob> oldJobs = repository.findByCreatedAtBefore(cutoff);

        int deleted = 0;
        for (ImageJob job : oldJobs) {
            if (!job.isFinished()) {
                continue;
            }
            try {
                repository.delete(job);
                deleteFiles(job);
                deleted++;
            } catch (Exception e) {
                log.warn("Could not delete job {}: {}", job.getId(), e.getMessage());
            }
        }

        if (deleted > 0) {
            log.info("Deleted {} jobs older than {}", deleted, retention);
        }
    }

    private void deleteFiles(ImageJob job) throws IOException {
        deleteIfPresent(job.getInputPath());
        deleteIfPresent(job.getOutputPath());

        Path piecesDir = storageDir.resolve(job.getId() + "-pieces");
        if (Files.exists(piecesDir)) {
            try (Stream<Path> paths = Files.walk(piecesDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private void deleteIfPresent(String path) throws IOException {
        if (path != null) {
            Files.deleteIfExists(Path.of(path));
        }
    }
}