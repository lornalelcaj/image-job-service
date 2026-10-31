package com.lornalelcaj.imagejobs;

import java.util.List;

/**
 * Messages sent over the WebSocket connection of a puzzle room.
 * Every server message has a "type" field so the browser knows how to handle it.
 */
public final class RoomMessages {

    private RoomMessages() {
    }

    // ----- browser -> server -----

    /** GRAB, MOVE or DROP a piece. x and y are only used for MOVE and DROP. */
    public record ClientMessage(String type, Integer piece, Double x, Double y) {
    }

    // ----- server -> browser -----

    public record PlayerView(String id, String name, String color) {
    }

    public record PieceView(int index, double x, double y, boolean placed, String lockedBy) {
    }

    /** Sent once to a player who just joined: who they are and where every piece is. */
    public record State(String type, String you, List<PlayerView> players, List<PieceView> pieces) {
        public State(String you, List<PlayerView> players, List<PieceView> pieces) {
            this("STATE", you, players, pieces);
        }
    }

    /** The list of players changed (someone joined or left). */
    public record Players(String type, List<PlayerView> players) {
        public Players(List<PlayerView> players) {
            this("PLAYERS", players);
        }
    }

    /** A player now holds the lock on a piece. */
    public record Locked(String type, int piece, String player) {
        public Locked(int piece, String player) {
            this("LOCKED", piece, player);
        }
    }

    /** The lock holder is dragging a piece. */
    public record Moved(String type, int piece, double x, double y, String player) {
        public Moved(int piece, double x, double y, String player) {
            this("MOVED", piece, x, y, player);
        }
    }

    /** A lock was released: the piece was dropped, the lock expired, or the player left. */
    public record Released(String type, int piece, double x, double y, boolean placed) {
        public Released(int piece, double x, double y, boolean placed) {
            this("RELEASED", piece, x, y, placed);
        }
    }
}
