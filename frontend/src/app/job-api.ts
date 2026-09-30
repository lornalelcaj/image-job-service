import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Job, PuzzleInfo } from './models';

@Injectable({ providedIn: 'root' })
export class JobApi {
  private http = inject(HttpClient);

  listJobs(): Observable<Job[]> {
    return this.http.get<Job[]>('/jobs');
  }

  getJob(id: number): Observable<Job> {
    return this.http.get<Job>(`/jobs/${id}`);
  }

  createPuzzle(file: File, rows: number, cols: number, seed?: number): Observable<Job> {
    const form = new FormData();
    form.append('file', file);
    form.append('type', 'PUZZLE');
    form.append('rows', String(rows));
    form.append('cols', String(cols));
    if (seed !== undefined) {
      form.append('seed', String(seed));
    }
    return this.http.post<Job>('/jobs', form);
  }

  getPieces(id: number): Observable<PuzzleInfo> {
    return this.http.get<PuzzleInfo>(`/jobs/${id}/pieces`);
  }
}
