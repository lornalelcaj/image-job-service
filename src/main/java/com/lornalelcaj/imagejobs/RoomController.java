package com.lornalelcaj.imagejobs;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    public record CreateRoomRequest(Long jobId) {
    }

    public record RoomInfo(String code, long jobId) {
    }

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomInfo create(@RequestBody CreateRoomRequest request) {
        if (request == null || request.jobId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobId is required");
        }
        PuzzleRoom room = rooms.create(request.jobId());
        return new RoomInfo(room.code(), room.jobId());
    }

    @GetMapping("/{code}")
    public RoomInfo get(@PathVariable String code) {
        PuzzleRoom room = rooms.find(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        return new RoomInfo(room.code(), room.jobId());
    }
}
