export type JobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED';

export interface Job {
  id: number;
  filename: string;
  status: JobStatus;
  type: 'GRAYSCALE' | 'PUZZLE' | null;
  puzzleRows: number | null;
  puzzleCols: number | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface PieceInfo {
  row: number;
  col: number;
  x: number;
  y: number;
  width: number;
  height: number;
  url: string;
}

export interface PuzzleInfo {
  rows: number;
  cols: number;
  cellWidth: number;
  cellHeight: number;
  margin: number;
  pieces: PieceInfo[];
}

// ----- Multiplayer rooms -----

export interface RoomInfo {
  code: string;
  jobId: number;
}

export interface PlayerView {
  id: string;
  name: string;
  color: string;
}

export interface PieceView {
  index: number;
  x: number;
  y: number;
  placed: boolean;
  lockedBy: string | null;
}

/** Messages the server sends over the room's WebSocket. */
export type ServerMessage =
  | { type: 'STATE'; you: string; players: PlayerView[]; pieces: PieceView[] }
  | { type: 'PLAYERS'; players: PlayerView[] }
  | { type: 'LOCKED'; piece: number; player: string }
  | { type: 'MOVED'; piece: number; x: number; y: number; player: string }
  | { type: 'RELEASED'; piece: number; x: number; y: number; placed: boolean };

/** Messages the browser sends to the server. */
export type ClientMessage =
  | { type: 'GRAB'; piece: number }
  | { type: 'MOVE' | 'DROP'; piece: number; x: number; y: number };
