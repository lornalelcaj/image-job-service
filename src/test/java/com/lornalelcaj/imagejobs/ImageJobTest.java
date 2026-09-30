package com.lornalelcaj.imagejobs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageJobTest {

    @Test
    void newJobIsPending() {
        ImageJob job = new ImageJob("photo.jpg", "/tmp/input");

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getCreatedAt()).isNotNull();
    }

    @Test
    void markDoneStoresOutputPath() {
        ImageJob job = new ImageJob("photo.jpg", "/tmp/input");

        job.markDone("/tmp/output.png");

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getOutputPath()).isEqualTo("/tmp/output.png");
    }

    @Test
    void markFailedStoresErrorMessage() {
        ImageJob job = new ImageJob("photo.heic", "/tmp/input");

        job.markFailed("File is not a supported image format");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("File is not a supported image format");
    }

    @Test
    void cancelledJobIsFinished() {
        ImageJob job = new ImageJob("photo.jpg", "/tmp/input");

        job.markCancelled();

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.isFinished()).isTrue();
    }

    @Test
    void pendingJobIsNotFinished() {
        ImageJob job = new ImageJob("photo.jpg", "/tmp/input");

        assertThat(job.isFinished()).isFalse();
    }
}