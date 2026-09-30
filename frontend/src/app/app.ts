import { Component } from '@angular/core';
import { Upload } from './upload/upload';

@Component({
  selector: 'app-root',
  imports: [Upload],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {}
