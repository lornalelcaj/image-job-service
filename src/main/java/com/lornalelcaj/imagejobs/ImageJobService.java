package com.lornalelcaj.imagejobs;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ImageJobService {

    private final ImageJobRepository repository;
    private final ImageProcessor processor;
    private final PuzzleGenerator puzzleGenerator;
    private final Semaphore permits;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Path storageDir;
    private static final long MAX_PIXELS = 40_000_000L; // 40 megapixels

    public ImageJobService(ImageJobRepository repository,
                           ImageProcessor processor,
                           PuzzleGenerator puzzleGenerator,
                           @Value("${imagejobs.max-concurrent-jobs:4}") int maxConcurrentJobs,
                           @Value("${imagejobs.storage-dir:storage}") String storageDir) {
        this.repository = repository;
        this.processor = processor;
        this.puzzleGenerator = puzzleGenerator;
        this.permits = new Semaphore(maxConcurrentJobs);
        this.storageDir = Path.of(storageDir).toAbsolutePath();
    }
    public ImageJob submit(MultipartFile file, JobType type, int rows, int cols, Long seed)
            throws IOException {
        if (type == JobType.PUZZLE && (rows < 2 || rows > 20 || cols < 2 || cols > 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rows and columns must be between 2 and 20");
        }

        Files.createDirectories(storageDir);
        Path inputPath = storageDir.resolve(UUID.randomUUID().toString());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, inputPath);
        }

        ImageJob newJob = type == JobType.PUZZLE
                ? new ImageJob(file.getOriginalFilename(), inputPath.toString(), JobType.PUZZLE,
                rows, cols, seed != null ? seed : ThreadLocalRandom.current().nextLong())
                : new ImageJob(file.getOriginalFilename(), inputPath.toString());

        ImageJob job = repository.save(newJob);
        executor.submit(() -> process(job.getId()));
        return job;
    }

    public ImageJob cancel(Long jobId) {
        ImageJob job = findJob(jobId);
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

    public PuzzleInfo pieces(Long jobId) {
        ImageJob job = findJob(jobId);
        if (job.getType() != JobType.PUZZLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a puzzle job");
        }
        if (job.getStatus() != JobStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not finished yet");
        }

        int cellWidth = job.getCellWidth();
        int cellHeight = job.getCellHeight();
        int margin = job.getMargin();
        List<PuzzleInfo.PieceInfo> pieces = new ArrayList<>();
        for (int row = 0; row < job.getPuzzleRows(); row++) {
            for (int col = 0; col < job.getPuzzleCols(); col++) {
                pieces.add(new PuzzleInfo.PieceInfo(
                        row, col,
                        col * cellWidth - margin,
                        row * cellHeight - margin,
                        cellWidth + 2 * margin,
                        cellHeight + 2 * margin,
                        "/jobs/" + jobId + "/pieces/" + row + "/" + col));
            }
        }
        return new PuzzleInfo(job.getPuzzleRows(), job.getPuzzleCols(),
                cellWidth, cellHeight, margin, pieces);
    }

    public byte[] pieceImage(Long jobId, int row, int col) throws IOException {
        Path file = pieceFile(jobId, row, col);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return Files.readAllBytes(file);
    }

    private ImageJob findJob(Long jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
            Path outputPath = storageDir.resolve(jobId + "-result.png");

            if (job.getType() == JobType.PUZZLE) {
                Puzzle puzzle = puzzleGenerator.generate(input,
                        job.getPuzzleRows(), job.getPuzzleCols(), job.getSeed());
                savePieces(jobId, puzzle);
                ImageIO.write(puzzleGenerator.preview(puzzle, 12), "png", outputPath.toFile());
                job.markPuzzleDone(outputPath.toString(),
                        puzzle.cellWidth(), puzzle.cellHeight(), puzzle.margin());
            } else {
                BufferedImage output = processor.process(input);
                ImageIO.write(output, "png", outputPath.toFile());
                job.markDone(outputPath.toString());
            }
        } catch (Exception e) {
            job.markFailed(e.getMessage());
        }

        try {
            repository.save(job);
        } catch (ObjectOptimisticLockingFailureException e) {
            // the job was cancelled while running, so the cancellation wins
        }
    }

    private void savePieces(Long jobId, Puzzle puzzle) throws IOException {
        Files.createDirectories(storageDir.resolve(jobId + "-pieces"));
        for (PuzzlePiece piece : puzzle.pieces()) {
            ImageIO.write(piece.image(), "png",
                    pieceFile(jobId, piece.row(), piece.col()).toFile());
        }
    }

    private Path pieceFile(Long jobId, int row, int col) {
        return storageDir.resolve(jobId + "-pieces").resolve("piece-" + row + "-" + col + ".png");
    }
}