package com.lornalelcaj.imagejobs;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PuzzleGeneratorTest {

    private final PuzzleGenerator generator = new PuzzleGenerator();

    @Test
    void createsOnePiecePerCell() {
        Puzzle puzzle = generator.generate(gradient(400, 300), 3, 4, 42);

        assertThat(puzzle.pieces()).hasSize(12);
        assertThat(puzzle.pieces())
                .extracting(p -> p.row() * 4 + p.col())
                .doesNotHaveDuplicates();
    }

    @Test
    void pieceCenterMatchesSourceImage() {
        BufferedImage source = gradient(400, 300);

        Puzzle puzzle = generator.generate(source, 3, 4, 42);

        int cx = puzzle.margin() + puzzle.cellWidth() / 2;
        int cy = puzzle.margin() + puzzle.cellHeight() / 2;
        for (PuzzlePiece piece : puzzle.pieces()) {
            int expected = source.getRGB(piece.x() + cx, piece.y() + cy);
            assertThat(piece.image().getRGB(cx, cy)).isEqualTo(expected);
        }
    }

    @Test
    void cornerOutsideTheShapeIsTransparent() {
        Puzzle puzzle = generator.generate(gradient(400, 300), 3, 4, 42);

        for (PuzzlePiece piece : puzzle.pieces()) {
            int alpha = piece.image().getRGB(0, 0) >>> 24;
            assertThat(alpha).isZero();
        }
    }

    @Test
    void rejectsTooFewRows() {
        assertThatThrownBy(() -> generator.generate(gradient(400, 300), 1, 4, 42))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesPreviewForVisualCheck() throws IOException {
        Puzzle puzzle = generator.generate(gradient(800, 600), 4, 5, 7);

        BufferedImage preview = generator.preview(puzzle, 12);

        ImageIO.write(preview, "png", new File("target/puzzle-preview.png"));
        assertThat(preview.getWidth()).isGreaterThan(800);
    }

    private BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = x * 255 / width;
                int g = y * 255 / height;
                image.setRGB(x, y, (r << 16) | (g << 8) | 128);
            }
        }
        return image;
    }
}