package com.lornalelcaj.imagejobs;

import com.lornalelcaj.imagejobs.RoomMessages.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The live connection between the players' browsers and their room.
 *
 * <p>Browsers connect to {@code /ws/rooms?room=CODE}. Each action is applied to the room while
 * holding the room's lock, and the resulting messages are sent to everyone before the lock is
 * released, so all players receive the changes in the same order.
 */
@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomSocketHandler.class);

    private static final String ROOM_ATTRIBUTE = "roomCode";
    private static final CloseStatus ROOM_NOT_FOUND = new CloseStatus(4404, "Room not found");
    private static final CloseStatus ROOM_FULL = new CloseStatus(4429, "Room is full");
    private static final int SEND_TIME_LIMIT_MS = 2_000;
    private static final int BUFFER_LIMIT_BYTES = 512 * 1024;

    private final RoomService rooms;
    private final JsonMapper json;

    /** room code -> (session id -> session) */
    private final Map<String, Map<String, WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public RoomSocketHandler(RoomService rooms, JsonMapper json) {
        this.rooms = rooms;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String code = session.getUri() == null ? null
                : UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("room");
        Optional<PuzzleRoom> found = rooms.find(code);
        if (found.isEmpty()) {
            session.close(ROOM_NOT_FOUND);
            return;
        }
        PuzzleRoom room = found.get();

        // Several threads may send to the same session, which a plain WebSocketSession does not allow.
        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);

        synchronized (room) {
            if (!room.join(session.getId())) {
                session.close(ROOM_FULL);
                return;
            }
            session.getAttributes().put(ROOM_ATTRIBUTE, room.code());
            sessionsByRoom.computeIfAbsent(room.code(), c -> new ConcurrentHashMap<>())
                    .put(session.getId(), safeSession);

            send(safeSession, room.stateFor(session.getId()));
            broadcast(room.code(), List.of(room.playersMessage()));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Optional<PuzzleRoom> found = roomOf(session);
        if (found.isEmpty()) {
            return;
        }
        ClientMessage request;
        try {
            request = json.readValue(message.getPayload(), ClientMessage.class);
        } catch (RuntimeException e) {
            return; // ignore messages that are not valid JSON
        }
        if (request.type() == null || request.piece() == null) {
            return;
        }

        PuzzleRoom room = found.get();
        String player = session.getId();
        int piece = request.piece();
        boolean hasPosition = request.x() != null && request.y() != null;

        publish(room, r -> switch (request.type()) {
            case "GRAB" -> r.grab(player, piece, Instant.now());
            case "MOVE" -> hasPosition ? r.move(player, piece, request.x(), request.y(), Instant.now()) : List.<Object>of();
            case "DROP" -> hasPosition ? r.drop(player, piece, request.x(), request.y()) : List.<Object>of();
            default -> List.<Object>of();
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Optional<PuzzleRoom> found = roomOf(session);
        if (found.isEmpty()) {
            return;
        }
        PuzzleRoom room = found.get();
        synchronized (room) {
            Map<String, WebSocketSession> sessions = sessionsByRoom.get(room.code());
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) {
                    sessionsByRoom.remove(room.code());
                }
            }
            broadcast(room.code(), room.leave(session.getId(), Instant.now()));
        }
    }

    /** Applies an action to a room and sends the resulting messages to everyone in it, in order. */
    public void publish(PuzzleRoom room, Function<PuzzleRoom, List<Object>> action) {
        synchronized (room) {
            broadcast(room.code(), action.apply(room));
        }
    }

    private Optional<PuzzleRoom> roomOf(WebSocketSession session) {
        return rooms.find((String) session.getAttributes().get(ROOM_ATTRIBUTE));
    }

    private void broadcast(String code, List<Object> messages) {
        Map<String, WebSocketSession> sessions = sessionsByRoom.get(code);
        if (sessions == null) {
            return;
        }
        for (Object message : messages) {
            TextMessage text = new TextMessage(json.writeValueAsString(message));
            for (WebSocketSession session : sessions.values()) {
                send(session, text);
            }
        }
    }

    private void send(WebSocketSession session, Object message) {
        send(session, new TextMessage(json.writeValueAsString(message)));
    }

    private void send(WebSocketSession session, TextMessage text) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(text);
        } catch (Exception e) {
            // A slow or broken connection must not stop the others; it is cleaned up when it closes.
            log.debug("Could not send to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
