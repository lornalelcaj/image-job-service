package com.lornalelcaj.imagejobs;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomControllerTest {

    private final RoomService rooms = mock(RoomService.class);
    private final RoomController controller = new RoomController(rooms);

    @Test
    void returns404ForAnUnknownRoomCode() {
        when(rooms.find("NOPE1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get("NOPE1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void requiresAJobIdToCreateARoom() {
        assertThatThrownBy(() -> controller.create(new RoomController.CreateRoomRequest(null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
