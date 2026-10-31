package com.lornalelcaj.imagejobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Background tasks that keep the multiplayer rooms healthy. */
@Component
public class RoomMaintenance {

    private static final Logger log = LoggerFactory.getLogger(RoomMaintenance.class);
    private static final Duration EMPTY_ROOM_GRACE = Duration.ofMinutes(10);

    private final RoomService rooms;
    private final RoomSocketHandler sockets;

    public RoomMaintenance(RoomService rooms, RoomSocketHandler sockets) {
        this.rooms = rooms;
        this.sockets = sockets;
    }

    /** Frees pieces whose holder stopped responding, so no piece stays stuck forever. */
    @Scheduled(fixedDelay = 1000)
    public void expireLocks() {
        Instant now = Instant.now();
        for (PuzzleRoom room : rooms.all()) {
            sockets.publish(room, r -> r.expireLocks(now));
        }
    }

    /** Deletes rooms that have been empty for a while. */
    @Scheduled(fixedDelay = 60_000)
    public void removeAbandonedRooms() {
        int removed = rooms.removeAbandoned(Instant.now(), EMPTY_ROOM_GRACE);
        if (removed > 0) {
            log.info("Removed {} abandoned rooms", removed);
        }
    }
}
