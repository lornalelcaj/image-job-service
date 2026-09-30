import { Component, OnInit, WritableSignal, computed, inject, input, signal } from '@angular/core';
import { JobApi } from '../job-api';
import { PieceInfo, PuzzleInfo } from '../models';

interface BoardPiece {
  info: PieceInfo;
  targetLeft: number;
  targetTop: number;
  left: WritableSignal<number>;
  top: WritableSignal<number>;
  z: WritableSignal<number>;
  placed: WritableSignal<boolean>;
}

@Component({
  selector: 'app-board',
  imports: [],
  templateUrl: './board.html',
  styleUrl: './board.css',
})
export class Board implements OnInit {
  private api = inject(JobApi);

  jobId = input.required<number>();

  protected pieces = signal<BoardPiece[]>([]);
  protected boardWidth = signal(0);
  protected boardHeight = signal(0);
  protected frame = signal({ left: 0, top: 0, width: 0, height: 0 });
  protected scale = signal(1);
  protected moves = signal(0);
  protected solvedSeconds = signal<number | null>(null);
  protected placedCount = computed(() => this.pieces().filter(p => p.placed()).length);

  private dragging: BoardPiece | null = null;
  private startX = 0;
  private startY = 0;
  private startLeft = 0;
  private startTop = 0;
  private topZ = 1;
  private startTime = 0;
  private snapDistance = 0;

  ngOnInit() {
    this.api.getPieces(this.jobId()).subscribe(puzzle => this.setUp(puzzle));
  }

  private setUp(puzzle: PuzzleInfo) {
    const pad = puzzle.margin * 2;
    const frameWidth = puzzle.cols * puzzle.cellWidth;
    const frameHeight = puzzle.rows * puzzle.cellHeight;
    const width = frameWidth * 2 + pad * 3;
    const height = frameHeight + pad * 2;

    this.boardWidth.set(width);
    this.boardHeight.set(height);
    this.frame.set({ left: pad, top: pad, width: frameWidth, height: frameHeight });
    this.scale.set(Math.min(1, (window.innerWidth - 48) / width));
    this.snapDistance = Math.min(puzzle.cellWidth, puzzle.cellHeight) * 0.2;

    const scatterLeft = frameWidth + pad * 2;
    this.pieces.set(puzzle.pieces.map(info => ({
      info,
      targetLeft: pad + info.x,
      targetTop: pad + info.y,
      left: signal(scatterLeft + Math.random() * (frameWidth - puzzle.cellWidth) - puzzle.margin),
      top: signal(pad + Math.random() * (frameHeight - puzzle.cellHeight) - puzzle.margin),
      z: signal(1),
      placed: signal(false),
    })));

    this.startTime = Date.now();
  }

  onPointerDown(event: PointerEvent, piece: BoardPiece) {
    if (piece.placed()) {
      return;
    }
    event.preventDefault();
    (event.target as HTMLElement).setPointerCapture(event.pointerId);

    this.dragging = piece;
    this.startX = event.clientX;
    this.startY = event.clientY;
    this.startLeft = piece.left();
    this.startTop = piece.top();
    piece.z.set(++this.topZ);
  }

  onPointerMove(event: PointerEvent, piece: BoardPiece) {
    if (this.dragging !== piece) {
      return;
    }
    const scale = this.scale();
    piece.left.set(this.startLeft + (event.clientX - this.startX) / scale);
    piece.top.set(this.startTop + (event.clientY - this.startY) / scale);
  }

  onPointerUp(piece: BoardPiece) {
    if (this.dragging !== piece) {
      return;
    }
    this.dragging = null;
    this.moves.update(m => m + 1);

    const distance = Math.hypot(piece.left() - piece.targetLeft, piece.top() - piece.targetTop);
    if (distance < this.snapDistance) {
      piece.left.set(piece.targetLeft);
      piece.top.set(piece.targetTop);
      piece.placed.set(true);
      piece.z.set(0);

      if (this.pieces().every(p => p.placed())) {
        this.solvedSeconds.set(Math.round((Date.now() - this.startTime) / 1000));
      }
    }
  }

  protected formatTime(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${minutes}:${rest.toString().padStart(2, '0')}`;
  }
}
