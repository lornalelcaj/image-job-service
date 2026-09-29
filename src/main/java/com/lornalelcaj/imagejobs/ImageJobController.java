package com.lornalelcaj.imagejobs;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/jobs")
public class ImageJobController {

    private final ImageJobRepository repository;

    public ImageJobController(ImageJobRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ImageJob create(@RequestParam String filename) {
        return repository.save(new ImageJob(filename));
    }

    @GetMapping
    public List<ImageJob> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ImageJob get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}