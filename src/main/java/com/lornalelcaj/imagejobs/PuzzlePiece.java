package com.lornalelcaj.imagejobs;

import java.awt.image.BufferedImage;

public record PuzzlePiece(int row, int col, int x, int y, BufferedImage image) {
}