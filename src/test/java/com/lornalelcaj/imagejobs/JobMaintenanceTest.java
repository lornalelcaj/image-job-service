package com.lornalelcaj.imagejobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobMaintenanceTest {

    private final ImageJobRepository repository = mock(ImageJobRepository.class);

    @Test
    void marksJobsFromBeforeTheRestartAsFailed() throws InterruptedException {
        ImageJob pending = new ImageJob("a.jpg", "/tmp/a");
        ImageJob running = new ImageJob("b.jpg", "/tmp/b");
        running.markRunning();
        Thread.sleep(5); // make sure the app "starts" after the jobs were created

        JobMaintenance maintenance = new JobMaintenance(repository, "storage", Duration.ofHours(24));
        when(repository.findByStatusIn(any())).thenReturn(List.of(pending, running));

        maintenance.failInterruptedJobs();

        assertThat(pending.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(running.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(pending.getErrorMessage()).isEqualTo("Interrupted by a server restart");
    }

    @Test
    void leavesJobsCreatedAfterStartupAlone() throws InterruptedException {
        JobMaintenance maintenance = new JobMaintenance(repository, "storage", Duration.ofHours(24));
        Thread.sleep(5); // this upload arrives after the app started
        ImageJob newUpload = new ImageJob("c.jpg", "/tmp/c");
        when(repository.findByStatusIn(any())).thenReturn(List.of(newUpload));

        maintenance.failInterruptedJobs();

        assertThat(newUpload.getStatus()).isEqualTo(JobStatus.PENDING);
    }
}