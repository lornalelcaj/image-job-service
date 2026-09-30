import { Component, inject, signal } from '@angular/core';
import { switchMap, takeWhile, timer } from 'rxjs';
import { JobApi } from '../job-api';
import { Job } from '../models';
import { Board } from '../board/board';

@Component({
  imports: [Board],
  selector: 'app-upload',
  styleUrl: './upload.css',
  templateUrl: './upload.html',
})
export class Upload {
  private api = inject(JobApi);

  protected file = signal<File | null>(null);
  protected rows = signal(4);
  protected cols = signal(5);
  protected job = signal<Job | null>(null);
  protected error = signal<string | null>(null);
  onFileSelected(event: Event) {
    const selected = (event.target as HTMLInputElement).files?.[0];
    if (selected) {
      this.file.set(selected);
    }
  }

  generate() {
    const file = this.file();
    if (!file) {
      return;
    }
    this.error.set(null);

    this.api.createPuzzle(file, this.rows(), this.cols()).subscribe({
      next: created => {
        this.job.set(created);
        this.waitForJob(created.id);
      },
      error: err => this.error.set(
        err.status === 429
          ? 'Too many uploads. Please wait a minute and try again.'
          : 'Upload failed. Please try again.'
      )
    });
  }

  private waitForJob(id: number) {
    timer(0, 1000).pipe(
      switchMap(() => this.api.getJob(id)),
      takeWhile(job => job.status === 'PENDING' || job.status === 'RUNNING', true)
    ).subscribe(job => this.job.set(job));
  }
}
