import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Board } from '../board/board';
import { JobApi } from '../job-api';
import { RoomConnection } from '../room-connection';

@Component({
  selector: 'app-room',
  imports: [Board, RouterLink],
  templateUrl: './room.html',
  styleUrl: './room.css',
})
export class Room implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(JobApi);

  protected code = signal('');
  protected jobId = signal<number | null>(null);
  protected connection = signal<RoomConnection | null>(null);
  protected error = signal<string | null>(null);
  protected copied = signal(false);
  protected shareLink = computed(() => `${location.origin}/room/${this.code()}`);

  ngOnInit() {
    const code = (this.route.snapshot.paramMap.get('code') ?? '').toUpperCase();
    this.code.set(code);

    this.api.getRoom(code).subscribe({
      next: room => {
        this.jobId.set(room.jobId);
        this.connection.set(new RoomConnection(room.code));
      },
      error: () => this.error.set('This room does not exist or has already ended.'),
    });
  }

  ngOnDestroy() {
    this.connection()?.close();
  }

  copyLink(input: HTMLInputElement) {
    const done = () => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    };
    // The clipboard API only works on HTTPS pages, so fall back to selecting the text.
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(this.shareLink()).then(done);
    } else {
      input.select();
      if (document.execCommand('copy')) {
        done();
      }
    }
  }

  reconnect() {
    location.reload();
  }
}
