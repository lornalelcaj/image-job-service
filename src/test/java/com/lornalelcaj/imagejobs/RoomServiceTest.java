package com.lornalelcaj.imagejobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomServiceTest {

    private final ImageJobService jobService = mock(ImageJobService.class);
    private final RoomService rooms = new RoomService(jobService);

    @Test
    void createsARoomWithAShortReadableCode() {
        when(jobService.pieces(7L)).thenReturn(onePiecePuzzle());

        PuzzleRoom room = rooms.create(7L);

        assertThat(room.code()).matches("[A-HJ-NP-Z2-9]{5}");
        assertThat(room.jobId()).isEqualTo(7L);
        assertThat(rooms.find(room.code())).contains(room);
        assertThat(rooms.find(room.code().toLowerCase())).contains(room);
    }

    @Test
    void removesRoomsThatStayEmpty() {
        when(jobService.pieces(7L)).thenReturn(onePiecePuzzle());
        PuzzleRoom room = rooms.create(7L);

        int removed = rooms.removeAbandoned(Instant.now().plus(Duration.ofMinutes(11)), Duration.ofMinutes(10));

        assertThat(removed).isEqualTo(1);
        assertThat(rooms.find(room.code())).isEmpty();
    }

    private PuzzleInfo onePiecePuzzle() {
        return new PuzzleInfo(1, 1, 100, 100, 20,
                List.of(new PuzzleInfo.PieceInfo(0, 0, -20, -20, 140, 140, "url")));
    }
}
