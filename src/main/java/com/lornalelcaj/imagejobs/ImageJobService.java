package com.lornalelcaj.imagejobs;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ImageJobService {

    private final ImageJobRepository repository;
    private final ImageProcessor processor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Path storageDir = Path.of("storage").toAbsolutePath();

    public ImageJobService(ImageJobRepository repository, ImageProcessor processor) {
        this.repository = repository;
        this.processor = processor;
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

    private void process(Long jobId) {
        ImageJob job = repository.findById(jobId).orElseThrow();
        try {
            job.markRunning();
            repository.save(job);

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
        repository.save(job);
    }
}