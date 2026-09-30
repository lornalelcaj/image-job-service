package com.lornalelcaj.imagejobs;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/jobs")
public class ImageJobController {

    private final ImageJobRepository repository;
    private final ImageJobService service;

    public ImageJobController(ImageJobRepository repository, ImageJobService service) {
        this.repository = repository;
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageJob create(@RequestParam("file") MultipartFile file) throws IOException {
        return service.submit(file);
    }

    @GetMapping
    public List<ImageJob> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ImageJob get(@PathVariable Long id) {
        return findJob(id);
    }

    @GetMapping(value = "/{id}/result", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] result(@PathVariable Long id) throws IOException {
        ImageJob job = findJob(id);
        if (job.getStatus() != JobStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not finished yet");
        }
        return Files.readAllBytes(Path.of(job.getOutputPath()));
    }

    private ImageJob findJob(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @PostMapping("/{id}/cancel")
    public ImageJob cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}