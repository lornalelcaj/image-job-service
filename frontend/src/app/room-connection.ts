import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { ClientMessage, ServerMessage } from './models';

export type ConnectionStatus = 'connecting' | 'open' | 'closed';

/** The live connection to one multiplayer room. */
export class RoomConnection {
  readonly messages = new Subject<ServerMessage>();
  readonly status = signal<ConnectionStatus>('connecting');
  readonly closeReason = signal<string | null>(null);

  private socket: WebSocket;
  private closedByUs = false;

  constructor(code: string) {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    this.socket = new WebSocket(`${protocol}://${location.host}/ws/rooms?room=${encodeURIComponent(code)}`);

    this.socket.onopen = () => this.status.set('open');
    this.socket.onmessage = event => {
      try {
        this.messages.next(JSON.parse(event.data) as ServerMessage);
      } catch {
        // ignore anything that is not valid JSON
      }
    };
    this.socket.onclose = event => {
      this.status.set('closed');
      if (!this.closedByUs) {
        this.closeReason.set(reasonFor(event.code));
      }
    };
  }

  send(message: ClientMessage) {
    if (this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    }
  }

  close() {
    this.closedByUs = true;
    this.socket.close();
    this.messages.complete();
  }
}

function reasonFor(code: number): string {
  switch (code) {
    case 4404:
      return 'This room no longer exists.';
    case 4429:
      return 'This room is full.';
    default:
      return 'The connection to the room was lost.';
  }
}
