package com.lornalelcaj.imagejobs;

import com.lornalelcaj.imagejobs.RoomMessages.Locked;
import com.lornalelcaj.imagejobs.RoomMessages.PieceView;
import com.lornalelcaj.imagejobs.RoomMessages.Released;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PuzzleRoomTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private PuzzleRoom room;

    /** A 2 x 2 puzzle with 100 px cells and a 20 px margin. Piece 0 belongs at (20, 20). */
    @BeforeEach
    void setUp() {
        List<PuzzleInfo.PieceInfo> pieces = new ArrayList<>();
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                pieces.add(new PuzzleInfo.PieceInfo(row, col, col * 100 - 20, row * 100 - 20, 140, 140, "url"));
            }
        }
        room = new PuzzleRoom("ABCDE", 1, new PuzzleInfo(2, 2, 100, 100, 20, pieces), 42, NOW);
        room.join("alice");
        room.join("bob");
    }

    @Test
    void firstPlayerToGrabAPieceGetsTheLock() {
        List<Object> aliceGrab = room.grab("alice", 0, NOW);
        List<Object> bobGrab = room.grab("bob", 0, NOW);

        assertThat(aliceGrab).containsExactly(new Locked(0, "alice"));
        assertThat(bobGrab).isEmpty();
        assertThat(piece(0).lockedBy()).isEqualTo("alice");
    }

    @Test
    void onlyTheLockHolderCanMoveAPiece() {
        room.grab("alice", 0, NOW);

        assertThat(room.move("bob", 0, 300, 300, NOW)).isEmpty();
        assertThat(room.move("alice", 0, 300, 300, NOW)).hasSize(1);
        assertThat(piece(0).x()).isEqualTo(300);
    }

    @Test
    void droppingNearTheTargetPlacesThePieceAndReleasesTheLock() {
        room.grab("alice", 0, NOW);

        List<Object> messages = room.drop("alice", 0, 25, 18);

        assertThat(messages).containsExactly(new Released(0, 20, 20, true));
        assertThat(piece(0).lockedBy()).isNull();
    }

    @Test
    void droppingFarAwayReleasesTheLockWithoutPlacing() {
        room.grab("alice", 0, NOW);

        List<Object> messages = room.drop("alice", 0, 400, 400);

        assertThat(messages).containsExactly(new Released(0, 400, 400, false));
    }

    @Test
    void placedPiecesCannotBeGrabbedAgain() {
        room.grab("alice", 0, NOW);
        room.drop("alice", 0, 20, 20);

        assertThat(room.grab("bob", 0, NOW)).isEmpty();
    }

    @Test
    void anExpiredLockCanBeTakenByAnotherPlayer() {
        room.grab("alice", 0, NOW);
        Instant later = NOW.plus(PuzzleRoom.LOCK_LEASE).plusSeconds(1);

        assertThat(room.grab("bob", 0, later)).containsExactly(new Locked(0, "bob"));
    }

    @Test
    void movingKeepsTheLockAlive() {
        room.grab("alice", 0, NOW);
        room.move("alice", 0, 300, 300, NOW.plusSeconds(4));

        assertThat(room.expireLocks(NOW.plusSeconds(6))).isEmpty();
        assertThat(room.expireLocks(NOW.plusSeconds(10))).hasSize(1);
    }

    @Test
    void leavingReleasesThePlayersLocks() {
        room.grab("alice", 0, NOW);

        List<Object> messages = room.leave("alice", NOW);

        assertThat(messages).first().isInstanceOf(Released.class);
        assertThat(piece(0).lockedBy()).isNull();
        assertThat(room.grab("bob", 0, NOW)).hasSize(1);
    }

    @Test
    void grabbingASecondPieceReleasesTheFirst() {
        room.grab("alice", 0, NOW);

        List<Object> messages = room.grab("alice", 1, NOW);

        assertThat(messages).hasSize(2);
        assertThat(piece(0).lockedBy()).isNull();
        assertThat(piece(1).lockedBy()).isEqualTo("alice");
    }

    @Test
    void theRoomIsSolvedWhenEveryPieceIsPlaced() {
        int[][] targets = {{20, 20}, {120, 20}, {20, 120}, {120, 120}};
        for (int i = 0; i < 4; i++) {
            room.grab("alice", i, NOW);
            room.drop("alice", i, targets[i][0], targets[i][1]);
        }

        assertThat(room.isSolved()).isTrue();
    }

    @Test
    void anEmptyRoomIsAbandonedAfterTheGracePeriod() {
        room.leave("alice", NOW);
        room.leave("bob", NOW);

        assertThat(room.isAbandoned(NOW.plusSeconds(60), Duration.ofMinutes(10))).isFalse();
        assertThat(room.isAbandoned(NOW.plusSeconds(601), Duration.ofMinutes(10))).isTrue();
    }

    private PieceView piece(int index) {
        return room.stateFor("alice").pieces().get(index);
    }
}
