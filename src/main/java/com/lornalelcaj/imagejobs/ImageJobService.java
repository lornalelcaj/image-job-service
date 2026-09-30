package com.lornalelcaj.imagejobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
public class ImageJobService {

    private final ImageJobRepository repository;
    private final ImageProcessor processor;
    private final Semaphore permits;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Path storageDir = Path.of("storage").toAbsolutePath();

    public ImageJobService(ImageJobRepository repository,
                           ImageProcessor processor,
                           @Value("${imagejobs.max-concurrent-jobs:4}") int maxConcurrentJobs) {
        this.repository = repository;
        this.processor = processor;
        this.permits = new Semaphore(maxConcurrentJobs);
    }

    public ImageJob submit(MultipartFile file) throws IOException {
        Files.createDirectories(storageDir);
        Path inputPath = storageDir.resolve(UUID.randomUUID().toString());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, inputPath);
        }

        ImageJob job = repository.save(new ImageJob(file.getOriginalFilename(), inputPath.toString()));
        executor.submit(() -> process(job.getId()));
        return job;
    }

    public ImageJob cancel(Long jobId) {
        ImageJob job = repository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (job.isFinished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is already finished");
        }
        job.markCancelled();
        try {
            return repository.save(job);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Job was updated at the same time, please try again");
        }
    }

    private void process(Long jobId) {
        try {
            permits.acquire(); // waits here while the maximum number of jobs is running
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            runJob(jobId);
        } finally {
            permits.release();
        }
    }

    private void runJob(Long jobId) {
        ImageJob job = repository.findById(jobId).orElseThrow();
        if (job.getStatus() == JobStatus.CANCELLED) {
            return;
        }

        try {
            job.markRunning();
            job = repository.save(job);
        } catch (ObjectOptimisticLockingFailureException e) {
            return; // cancelled just before it started
        }

        try {
            BufferedImage input = ImageIO.read(Path.of(job.getInputPath()).toFile());
            if (input == null) {
                throw new IOException("File is not a supported image format");
            }
            BufferedImage output = processor.process(input);
            Path outputPath = storageDir.resolve(jobId + "-result.png");
            ImageIO.write(output, "png", outputPath.toFile());
            job.markDone(outputPath.toString());
        } catch (Exception e) {
            job.markFailed(e.getMessage());
        }

        try {
            repository.save(job);
        } catch (ObjectOptimisticLockingFailureException e) {
            // the job was cancelled while running, so the cancellation wins
        }
    }
}