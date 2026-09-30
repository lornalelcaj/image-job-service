package com.lornalelcaj.imagejobs;

import java.util.List;

public record PuzzleInfo(int rows, int cols, int cellWidth, int cellHeight, int margin,
                         List<PieceInfo> pieces) {

    public record PieceInfo(int row, int col, int x, int y, int width, int height, String url) {
    }
}