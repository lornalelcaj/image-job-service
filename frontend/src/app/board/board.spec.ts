import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ClientMessage, PuzzleInfo, ServerMessage } from '../models';
import { RoomConnection } from '../room-connection';
import { Board } from './board';

const PUZZLE: PuzzleInfo = {
  rows: 1, cols: 2, cellWidth: 100, cellHeight: 100, margin: 20,
  pieces: [
    { row: 0, col: 0, x: -20, y: -20, width: 140, height: 140, url: '/jobs/1/pieces/0/0' },
    { row: 0, col: 1, x: 80, y: -20, width: 140, height: 140, url: '/jobs/1/pieces/0/1' },
  ],
};

/** A stand-in for the real WebSocket connection, so the test controls what the "server" says. */
class FakeConnection {
  readonly messages = new Subject<ServerMessage>();
  readonly sent: ClientMessage[] = [];
  send(message: ClientMessage) {
    this.sent.push(message);
  }
}

function pointer(x: number, y: number): PointerEvent {
  return {
    clientX: x, clientY: y, pointerId: 1,
    preventDefault: () => {},
    target: { setPointerCapture: () => {} },
  } as unknown as PointerEvent;
}

describe('Board', () => {
  let fixture: ComponentFixture<Board>;
  let board: any; // gives the test access to the board's protected state
  let http: HttpTestingController;

  async function create(connection: FakeConnection | null) {
    await TestBed.configureTestingModule({
      imports: [Board],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Board);
    fixture.componentRef.setInput('jobId', 1);
    fixture.componentRef.setInput('connection', connection as unknown as RoomConnection);
    board = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne('/jobs/1/pieces').flush(PUZZLE);
  }

  it('creates a single-player board with all pieces', async () => {
    await create(null);
    expect(board.pieces().length).toBe(2);
  });

  describe('multiplayer', () => {
    let connection: FakeConnection;

    beforeEach(async () => {
      connection = new FakeConnection();
      await create(connection);
      connection.messages.next({
        type: 'STATE', you: 'me',
        players: [{ id: 'me', name: 'Player 1', color: '#e53935' }, { id: 'other', name: 'Player 2', color: '#1e88e5' }],
        pieces: [
          { index: 0, x: 300, y: 50, placed: false, lockedBy: null },
          { index: 1, x: 350, y: 60, placed: false, lockedBy: null },
        ],
      });
    });

    it('uses the positions sent by the server', () => {
      expect(board.pieces()[0].left()).toBe(300);
      expect(board.synced()).toBe(true);
    });

    it('asks the server for the lock when a piece is grabbed', () => {
      board.onPointerDown(pointer(0, 0), board.pieces()[0]);
      expect(connection.sent[0]).toEqual({ type: 'GRAB', piece: 0 });
    });

    it('stops dragging and goes back when another player got the lock first', () => {
      const piece = board.pieces()[0];
      board.onPointerDown(pointer(0, 0), piece);
      board.onPointerMove(pointer(40, 40), piece);

      connection.messages.next({ type: 'LOCKED', piece: 0, player: 'other' });
      board.onPointerMove(pointer(80, 80), piece);

      expect(piece.lockedBy()).toBe('other');
      expect(piece.left()).toBe(300); // back at the last position the server confirmed
    });

    it('cannot grab a piece another player is holding', () => {
      connection.messages.next({ type: 'LOCKED', piece: 1, player: 'other' });
      board.onPointerDown(pointer(0, 0), board.pieces()[1]);
      expect(connection.sent).toEqual([]);
    });

    it('places a piece when the server says it snapped into place', () => {
      connection.messages.next({ type: 'RELEASED', piece: 0, x: 40, y: 40, placed: true });
      expect(board.pieces()[0].placed()).toBe(true);
      expect(board.placedCount()).toBe(1);
    });
  });
});
