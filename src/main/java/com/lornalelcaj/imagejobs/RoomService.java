package com.lornalelcaj.imagejobs;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Keeps the open multiplayer rooms in memory.
 * Rooms are not stored in the database, so a server restart ends all running games.
 */
@Service
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    /** Letters and digits that are easy to read and type (no 0/O or 1/I). */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 5;
    private static final int MAX_ROOMS = 500;

    private final ImageJobService jobService;
    private final Map<String, PuzzleRoom> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public RoomService(ImageJobService jobService) {
        this.jobService = jobService;
    }

    /** Opens a new room for a finished puzzle job and returns it. */
    public PuzzleRoom create(long jobId) {
        if (rooms.size() >= MAX_ROOMS) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Too many open rooms, please try again later");
        }
        PuzzleInfo puzzle = jobService.pieces(jobId); // checks that the job is a finished puzzle

        while (true) {
            String code = newCode();
            PuzzleRoom room = new PuzzleRoom(code, jobId, puzzle, random.nextLong(), Instant.now());
            if (rooms.putIfAbsent(code, room) == null) {
                log.info("Created room {} for job {}", code, jobId);
                return room;
            }
            // a room with this code already exists, so try another one
        }
    }

    public Optional<PuzzleRoom> find(String code) {
        return Optional.ofNullable(code).map(c -> rooms.get(c.toUpperCase()));
    }

    public Collection<PuzzleRoom> all() {
        return rooms.values();
    }

    /** Deletes rooms that nobody has been in for longer than the grace period. */
    public int removeAbandoned(Instant now, Duration grace) {
        int before = rooms.size();
        rooms.values().removeIf(room -> room.isAbandoned(now, grace));
        return before - rooms.size();
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
