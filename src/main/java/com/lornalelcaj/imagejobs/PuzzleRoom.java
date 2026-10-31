package com.lornalelcaj.imagejobs;

import com.lornalelcaj.imagejobs.RoomMessages.Locked;
import com.lornalelcaj.imagejobs.RoomMessages.Moved;
import com.lornalelcaj.imagejobs.RoomMessages.PieceView;
import com.lornalelcaj.imagejobs.RoomMessages.PlayerView;
import com.lornalelcaj.imagejobs.RoomMessages.Players;
import com.lornalelcaj.imagejobs.RoomMessages.Released;
import com.lornalelcaj.imagejobs.RoomMessages.State;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One multiplayer game: where every piece is, which pieces are placed, and who holds which lock.
 *
 * <p>The server is the single source of truth. Browsers only ask to grab, move or drop a piece,
 * and every method here returns the messages that must be sent to all players.
 *
 * <p>All public methods are synchronized, so actions in the same room are handled one at a time:
 * when two players grab the same piece at the same moment, the first request wins.
 */
public class PuzzleRoom {

    /** A lock expires if its holder sends nothing for this long, e.g. after losing the connection. */
    public static final Duration LOCK_LEASE = Duration.ofSeconds(5);

    public static final int MAX_PLAYERS = 8;

    private static final String[] COLORS = {
            "#e53935", "#1e88e5", "#43a047", "#fb8c00",
            "#8e24aa", "#00acc1", "#d81b60", "#6d4c41"
    };

    private final String code;
    private final long jobId;
    private final List<Piece> pieces = new ArrayList<>();
    private final double snapDistance;
    private final Map<String, PlayerView> players = new LinkedHashMap<>();
    private int joinedCount = 0;
    private Instant emptySince;

    /**
     * Sets up the board with the same geometry as the single-player board in the browser:
     * the frame on the left and the pieces scattered on the right. The layout comes from a seed,
     * so the whole room starts from the same positions.
     */
    public PuzzleRoom(String code, long jobId, PuzzleInfo puzzle, long layoutSeed, Instant now) {
        this.code = code;
        this.jobId = jobId;
        this.emptySince = now;
        this.snapDistance = Math.min(puzzle.cellWidth(), puzzle.cellHeight()) * 0.2;

        int pad = puzzle.margin() * 2;
        int frameWidth = puzzle.cols() * puzzle.cellWidth();
        int frameHeight = puzzle.rows() * puzzle.cellHeight();
        int scatterLeft = frameWidth + pad * 2;

        Random random = new Random(layoutSeed);
        for (PuzzleInfo.PieceInfo info : puzzle.pieces()) {
            double x = scatterLeft + random.nextDouble() * (frameWidth - puzzle.cellWidth()) - puzzle.margin();
            double y = pad + random.nextDouble() * (frameHeight - puzzle.cellHeight()) - puzzle.margin();
            pieces.add(new Piece(pad + info.x(), pad + info.y(), x, y));
        }
    }

    public String code() {
        return code;
    }

    public long jobId() {
        return jobId;
    }

    // ----- players -----

    /** Adds a player. Returns false if the room is full. */
    public synchronized boolean join(String playerId) {
        if (players.size() >= MAX_PLAYERS) {
            return false;
        }
        String color = COLORS[joinedCount % COLORS.length];
        joinedCount++;
        players.put(playerId, new PlayerView(playerId, "Player " + joinedCount, color));
        emptySince = null;
        return true;
    }

    /** Removes a player and releases every lock they still hold. */
    public synchronized List<Object> leave(String playerId, Instant now) {
        List<Object> messages = new ArrayList<>();
        if (players.remove(playerId) == null) {
            return messages;
        }
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            if (playerId.equals(piece.lockedBy)) {
                messages.add(release(i, piece));
            }
        }
        messages.add(playersMessage());
        if (players.isEmpty()) {
            emptySince = now;
        }
        return messages;
    }

    public synchronized Players playersMessage() {
        return new Players(List.copyOf(players.values()));
    }

    public synchronized State stateFor(String playerId) {
        List<PieceView> views = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            views.add(new PieceView(i, p.x, p.y, p.placed, p.lockedBy));
        }
        return new State(playerId, List.copyOf(players.values()), views);
    }

    /** True if nobody has been in the room for longer than the grace period. */
    public synchronized boolean isAbandoned(Instant now, Duration grace) {
        return emptySince != null && emptySince.plus(grace).isBefore(now);
    }

    public synchronized boolean isSolved() {
        return pieces.stream().allMatch(p -> p.placed);
    }

    // ----- piece actions -----

    /**
     * A player wants to start dragging a piece. The lock is granted if the piece is free,
     * if its old lock has expired, or if the player already holds it.
     */
    public synchronized List<Object> grab(String playerId, int index, Instant now) {
        List<Object> messages = new ArrayList<>();
        if (!players.containsKey(playerId) || !isValidIndex(index)) {
            return messages;
        }
        Piece piece = pieces.get(index);
        if (piece.placed || isHeldByOther(piece, playerId, now)) {
            return messages; // the request lost; the player already sees who holds the piece
        }

        // A player drags one piece at a time, so a lock on another piece is given up.
        for (int i = 0; i < pieces.size(); i++) {
            Piece other = pieces.get(i);
            if (i != index && playerId.equals(other.lockedBy)) {
                messages.add(release(i, other));
            }
        }

        piece.lockedBy = playerId;
        piece.lockExpiresAt = now.plus(LOCK_LEASE);
        messages.add(new Locked(index, playerId));
        return messages;
    }

    /** The lock holder moved the piece. Moves from anyone else are ignored. */
    public synchronized List<Object> move(String playerId, int index, double x, double y, Instant now) {
        if (!holdsLock(playerId, index) || !isFinite(x, y)) {
            return List.of();
        }
        Piece piece = pieces.get(index);
        piece.x = x;
        piece.y = y;
        piece.lockExpiresAt = now.plus(LOCK_LEASE); // moving keeps the lock alive
        return List.of(new Moved(index, x, y, playerId));
    }

    /**
     * The lock holder dropped the piece. If it is close to its correct position,
     * it snaps into place and stays there. Either way, the lock is released.
     */
    public synchronized List<Object> drop(String playerId, int index, double x, double y) {
        if (!holdsLock(playerId, index) || !isFinite(x, y)) {
            return List.of();
        }
        Piece piece = pieces.get(index);
        piece.x = x;
        piece.y = y;
        if (Math.hypot(x - piece.targetX, y - piece.targetY) < snapDistance) {
            piece.x = piece.targetX;
            piece.y = piece.targetY;
            piece.placed = true;
        }
        return List.of(release(index, piece));
    }

    /** Releases locks whose holder has not sent anything for longer than the lease. */
    public synchronized List<Object> expireLocks(Instant now) {
        List<Object> messages = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            if (piece.lockedBy != null && !piece.lockExpiresAt.isAfter(now)) {
                messages.add(release(i, piece));
            }
        }
        return messages;
    }

    // ----- helpers -----

    private Released release(int index, Piece piece) {
        piece.lockedBy = null;
        piece.lockExpiresAt = null;
        return new Released(index, piece.x, piece.y, piece.placed);
    }

    private boolean holdsLock(String playerId, int index) {
        return isValidIndex(index) && playerId.equals(pieces.get(index).lockedBy);
    }

    private boolean isHeldByOther(Piece piece, String playerId, Instant now) {
        return piece.lockedBy != null
                && !piece.lockedBy.equals(playerId)
                && piece.lockExpiresAt.isAfter(now);
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < pieces.size();
    }

    private static boolean isFinite(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y);
    }

    private static final class Piece {
        final double targetX;
        final double targetY;
        double x;
        double y;
        boolean placed;
        String lockedBy;
        Instant lockExpiresAt;

        Piece(double targetX, double targetY, double x, double y) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.x = x;
            this.y = y;
        }
    }
}
