package com.lornalelcaj.imagejobs;

import java.util.List;

public record Puzzle(int rows, int cols, int cellWidth, int cellHeight, int margin,
                     List<PuzzlePiece> pieces) {
}