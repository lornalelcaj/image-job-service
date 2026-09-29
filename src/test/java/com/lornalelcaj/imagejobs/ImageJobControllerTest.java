package com.lornalelcaj.imagejobs;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageJobControllerTest {

    private final ImageJobRepository repository = mock(ImageJobRepository.class);
    private final ImageJobService service = mock(ImageJobService.class);
    private final ImageJobController controller = new ImageJobController(repository, service);

    @Test
    void returns404WhenJobDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void refusesResultWhenJobIsNotFinished() {
        ImageJob pendingJob = new ImageJob("photo.jpg", "/tmp/input");
        when(repository.findById(1L)).thenReturn(Optional.of(pendingJob));

        assertThatThrownBy(() -> controller.result(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}