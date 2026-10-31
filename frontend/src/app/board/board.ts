import {
  Component, OnDestroy, OnInit, WritableSignal, computed, inject, input, signal
} from '@angular/core';
import { Subscription } from 'rxjs';
import { JobApi } from '../job-api';
import { PieceInfo, PlayerView, PuzzleInfo, ServerMessage } from '../models';
import { RoomConnection } from '../room-connection';

interface BoardPiece {
  index: number;
  info: PieceInfo;
  targetLeft: number;
  targetTop: number;
  left: WritableSignal<number>;
  top: WritableSignal<number>;
  z: WritableSignal<number>;
  placed: WritableSignal<boolean>;
  /** Multiplayer: the player currently holding this piece, if any. */
  lockedBy: WritableSignal<string | null>;
  /** Multiplayer: the last position confirmed by the server. */
  serverLeft: number;
  serverTop: number;
}

/** How often (ms) a dragged piece's position is sent to the other players. */
const MOVE_INTERVAL = 50;
/** How often (ms) a held piece tells the server it is still being held, even without moving. */
const KEEP_ALIVE_INTERVAL = 2000;

@Component({
  selector: 'app-board',
  imports: [],
  templateUrl: './board.html',
  styleUrl: './board.css',
})
export class Board implements OnInit, OnDestroy {
  private api = inject(JobApi);

  jobId = input.required<number>();
  /** Set for a multiplayer game; null for single player. */
  connection = input<RoomConnection | null>(null);

  protected pieces = signal<BoardPiece[]>([]);
  protected boardWidth = signal(0);
  protected boardHeight = signal(0);
  protected frame = signal({ left: 0, top: 0, width: 0, height: 0 });
  protected scale = signal(1);
  protected moves = signal(0);
  protected solvedSeconds = signal<number | null>(null);
  protected placedCount = computed(() => this.pieces().filter(p => p.placed()).length);

  // Multiplayer state
  protected players = signal<PlayerView[]>([]);
  protected you = signal<string | null>(null);
  protected synced = signal(true);
  private playerColors = computed(() => new Map(this.players().map(p => [p.id, p.color])));

  private dragging: BoardPiece | null = null;
  private startX = 0;
  private startY = 0;
  private startLeft = 0;
  private startTop = 0;
  private topZ = 1;
  private startTime = 0;
  private snapDistance = 0;

  private subscription?: Subscription;
  private ready = false;
  private queued: ServerMessage[] = [];
  private lastMoveSent = 0;
  private keepAlive?: ReturnType<typeof setInterval>;

  ngOnInit() {
    const connection = this.connection();
    if (connection) {
      this.synced.set(false); // wait for the room's state before showing the pieces
      // Messages can arrive before the pieces are loaded, so they wait in a queue.
      this.subscription = connection.messages.subscribe(message =>
        this.ready ? this.handle(message) : this.queued.push(message));
    }

    this.api.getPieces(this.jobId()).subscribe(puzzle => {
      this.setUp(puzzle);
      this.ready = true;
      this.queued.forEach(message => this.handle(message));
      this.queued = [];
    });
  }

  ngOnDestroy() {
    this.subscription?.unsubscribe();
    this.stopKeepAlive();
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
    this.pieces.set(puzzle.pieces.map((info, index) => {
      const left = scatterLeft + Math.random() * (frameWidth - puzzle.cellWidth) - puzzle.margin;
      const top = pad + Math.random() * (frameHeight - puzzle.cellHeight) - puzzle.margin;
      return {
        index,
        info,
        targetLeft: pad + info.x,
        targetTop: pad + info.y,
        left: signal(left),
        top: signal(top),
        z: signal(1),
        placed: signal(false),
        lockedBy: signal<string | null>(null),
        serverLeft: left,
        serverTop: top,
      };
    }));

    this.startTime = Date.now();
  }

  // ----- dragging -----

  onPointerDown(event: PointerEvent, piece: BoardPiece) {
    if (piece.placed()) {
      return;
    }
    const connection = this.connection();
    if (connection) {
      const holder = piece.lockedBy();
      if (holder !== null && holder !== this.you()) {
        return; // another player is holding this piece
      }
      // Ask the server for the lock and start dragging right away.
      // If another player's request arrived first, the server tells us and the drag stops.
      connection.send({ type: 'GRAB', piece: piece.index });
      piece.lockedBy.set(this.you());
      this.startKeepAlive(piece);
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

    const connection = this.connection();
    const now = Date.now();
    if (connection && now - this.lastMoveSent >= MOVE_INTERVAL) {
      this.lastMoveSent = now;
      connection.send({ type: 'MOVE', piece: piece.index, x: piece.left(), y: piece.top() });
    }
  }

  onPointerUp(piece: BoardPiece) {
    if (this.dragging !== piece) {
      return;
    }
    this.dragging = null;
    this.stopKeepAlive();
    this.moves.update(m => m + 1);

    const connection = this.connection();
    if (connection) {
      // The server decides whether the piece snaps into place and tells everyone.
      connection.send({ type: 'DROP', piece: piece.index, x: piece.left(), y: piece.top() });
      return;
    }

    const distance = Math.hypot(piece.left() - piece.targetLeft, piece.top() - piece.targetTop);
    if (distance < this.snapDistance) {
      piece.left.set(piece.targetLeft);
      piece.top.set(piece.targetTop);
      piece.placed.set(true);
      piece.z.set(0);
      this.checkSolved();
    }
  }

  // ----- multiplayer -----

  private handle(message: ServerMessage) {
    if (message.type === 'STATE') {
      this.you.set(message.you);
      this.players.set(message.players);
      for (const view of message.pieces) {
        const piece = this.pieces()[view.index];
        if (piece) {
          this.applyPosition(piece, view.x, view.y);
          piece.placed.set(view.placed);
          piece.lockedBy.set(view.lockedBy);
          if (view.placed) {
            piece.z.set(0);
          }
        }
      }
      this.startTime = Date.now();
      this.synced.set(true);
      this.checkSolved();
      return;
    }

    if (message.type === 'PLAYERS') {
      this.players.set(message.players);
      return;
    }

    const piece = this.pieces()[message.piece];
    if (!piece) {
      return;
    }

    switch (message.type) {
      case 'LOCKED':
        piece.lockedBy.set(message.player);
        if (message.player !== this.you()) {
          piece.z.set(++this.topZ);
          if (this.dragging === piece) {
            // Another player's request reached the server first: stop and go back.
            this.dragging = null;
            this.stopKeepAlive();
            this.applyPosition(piece, piece.serverLeft, piece.serverTop);
          }
        }
        break;

      case 'MOVED':
        piece.serverLeft = message.x;
        piece.serverTop = message.y;
        if (message.player !== this.you()) {
          piece.left.set(message.x);
          piece.top.set(message.y);
        }
        break;

      case 'RELEASED':
        if (this.dragging === piece) {
          this.dragging = null; // our lock expired while we were holding the piece
          this.stopKeepAlive();
        }
        piece.lockedBy.set(null);
        this.applyPosition(piece, message.x, message.y);
        piece.placed.set(message.placed);
        if (message.placed) {
          piece.z.set(0);
        }
        this.checkSolved();
        break;
    }
  }

  private applyPosition(piece: BoardPiece, x: number, y: number) {
    piece.serverLeft = x;
    piece.serverTop = y;
    piece.left.set(x);
    piece.top.set(y);
  }

  /** While a piece is held still, keep telling the server so the lock does not expire. */
  private startKeepAlive(piece: BoardPiece) {
    this.stopKeepAlive();
    this.keepAlive = setInterval(() => {
      this.connection()?.send({ type: 'MOVE', piece: piece.index, x: piece.left(), y: piece.top() });
    }, KEEP_ALIVE_INTERVAL);
  }

  private stopKeepAlive() {
    if (this.keepAlive !== undefined) {
      clearInterval(this.keepAlive);
      this.keepAlive = undefined;
    }
  }

  private checkSolved() {
    if (this.solvedSeconds() === null && this.pieces().length > 0 && this.pieces().every(p => p.placed())) {
      this.solvedSeconds.set(Math.round((Date.now() - this.startTime) / 1000));
    }
  }

  // ----- display helpers -----

  protected isLockedByOther(piece: BoardPiece): boolean {
    const holder = piece.lockedBy();
    return holder !== null && holder !== this.you();
  }

  protected lockGlow(piece: BoardPiece): string | null {
    const holder = piece.lockedBy();
    const color = holder ? this.playerColors().get(holder) : undefined;
    return color ? `drop-shadow(0 0 2px ${color}) drop-shadow(0 0 6px ${color})` : null;
  }

  protected formatTime(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${minutes}:${rest.toString().padStart(2, '0')}`;
  }
}
