package com.lornalelcaj.imagejobs;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Adds the multiplayer rooms to /actuator/health. */
@Component
public class RoomsHealthIndicator implements HealthIndicator {

    private final RoomService rooms;

    public RoomsHealthIndicator(RoomService rooms) {
        this.rooms = rooms;
    }

    @Override
    public Health health() {
        int players = rooms.all().stream().mapToInt(PuzzleRoom::playerCount).sum();
        return Health.up()
                .withDetail("openRooms", rooms.all().size())
                .withDetail("connectedPlayers", players)
                .build();
    }
}