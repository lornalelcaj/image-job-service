import { Routes } from '@angular/router';
import { Room } from './room/room';
import { Upload } from './upload/upload';

export const routes: Routes = [
  { path: '', component: Upload },
  { path: 'room/:code', component: Room },
  { path: '**', redirectTo: '' },
];
