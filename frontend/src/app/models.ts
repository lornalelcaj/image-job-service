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
