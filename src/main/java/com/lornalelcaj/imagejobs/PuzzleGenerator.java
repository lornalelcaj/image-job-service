package com.lornalelcaj.imagejobs;

import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

@Component
public class PuzzleGenerator {

    // Tab height relative to the smaller side of a piece
    private static final double TAB_SIZE = 0.22;

    // Shape of one tab: (fraction along the edge, fraction of tab height)
    private static final double[][] TAB_POINTS = {
            {0.40, 0}, {0.44, 0.45}, {0.28, 1}, {0.50, 1}, {0.72, 1}, {0.56, 0.45}, {0.60, 0}
    };

    public Puzzle generate(BufferedImage source, int rows, int cols, long seed) {
        if (rows < 2 || cols < 2 || rows > 20 || cols > 20) {
            throw new IllegalArgumentException("Rows and columns must be between 2 and 20");
        }
        int cellWidth = source.getWidth() / cols;
        int cellHeight = source.getHeight() / rows;
        if (cellWidth < 20 || cellHeight < 20) {
            throw new IllegalArgumentException("Image is too small for this many pieces");
        }

        double tabSize = TAB_SIZE * Math.min(cellWidth, cellHeight);
        int margin = (int) Math.ceil(tabSize) + 2;

        // For every inner edge, randomly decide which of the two pieces gets the tab
        Random random = new Random(seed);
        int[][] horizontalEdges = randomEdges(rows - 1, cols, random); // between row r and r+1
        int[][] verticalEdges = randomEdges(rows, cols - 1, random);   // between column c and c+1

        // Each piece is independent, so all pieces are cut in parallel
        List<PuzzlePiece> pieces = IntStream.range(0, rows * cols)
                .parallel()
                .mapToObj(i -> createPiece(source, i / cols, i % cols, rows, cols,
                        cellWidth, cellHeight, margin, tabSize, horizontalEdges, verticalEdges))
                .toList();

        return new Puzzle(rows, cols, cellWidth, cellHeight, margin, pieces);
    }

    public BufferedImage preview(Puzzle puzzle, int gap) {
        int stepX = puzzle.cellWidth() + gap;
        int stepY = puzzle.cellHeight() + gap;
        int width = puzzle.cols() * stepX + 2 * puzzle.margin() + gap;
        int height = puzzle.rows() * stepY + 2 * puzzle.margin() + gap;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(235, 235, 235));
        g.fillRect(0, 0, width, height);
        for (PuzzlePiece piece : puzzle.pieces()) {
            g.drawImage(piece.image(), gap + piece.col() * stepX, gap + piece.row() * stepY, null);
        }
        g.dispose();
        return canvas;
    }

    private int[][] randomEdges(int rows, int cols, Random random) {
        int[][] edges = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                edges[r][c] = random.nextBoolean() ? 1 : -1;
            }
        }
        return edges;
    }

    private PuzzlePiece createPiece(BufferedImage source, int row, int col, int rows, int cols,
                                    int cellWidth, int cellHeight, int margin, double tabSize,
                                    int[][] horizontalEdges, int[][] verticalEdges) {
        // +1 = tab pointing outwards, -1 = hole pointing inwards, 0 = straight border
        int top = row == 0 ? 0 : -horizontalEdges[row - 1][col];
        int bottom = row == rows - 1 ? 0 : horizontalEdges[row][col];
        int left = col == 0 ? 0 : -verticalEdges[row][col - 1];
        int right = col == cols - 1 ? 0 : verticalEdges[row][col];

        double x0 = margin, y0 = margin;
        double x1 = margin + cellWidth, y1 = margin + cellHeight;

        // Trace the outline clockwise: top, right, bottom, left
        Path2D.Double shape = new Path2D.Double();
        shape.moveTo(x0, y0);
        addEdge(shape, x0, y0, x1, y0, top, tabSize);
        addEdge(shape, x1, y0, x1, y1, right, tabSize);
        addEdge(shape, x1, y1, x0, y1, bottom, tabSize);
        addEdge(shape, x0, y1, x0, y0, left, tabSize);
        shape.closePath();

        int cellX = col * cellWidth;
        int cellY = row * cellHeight;

        BufferedImage image = new BufferedImage(cellWidth + 2 * margin, cellHeight + 2 * margin,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(shape);
        g.drawImage(source, margin - cellX, margin - cellY, null);
        g.setClip(null);
        g.setColor(new Color(0, 0, 0, 100));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(shape);
        g.dispose();

        return new PuzzlePiece(row, col, cellX - margin, cellY - margin, image);
    }

    private void addEdge(Path2D.Double path, double ax, double ay, double bx, double by,
                         int direction, double tabSize) {
        if (direction == 0) {
            path.lineTo(bx, by);
            return;
        }
        double length = Math.hypot(bx - ax, by - ay);
        double tx = (bx - ax) / length; // unit vector along the edge
        double ty = (by - ay) / length;
        double nx = ty;                 // unit vector pointing out of the piece
        double ny = -tx;
        double height = direction * tabSize;

        double[] xs = new double[TAB_POINTS.length];
        double[] ys = new double[TAB_POINTS.length];
        for (int i = 0; i < TAB_POINTS.length; i++) {
            double along = TAB_POINTS[i][0] * length;
            double out = TAB_POINTS[i][1] * height;
            xs[i] = ax + along * tx + out * nx;
            ys[i] = ay + along * ty + out * ny;
        }

        path.lineTo(xs[0], ys[0]);
        path.curveTo(xs[1], ys[1], xs[2], ys[2], xs[3], ys[3]);
        path.curveTo(xs[4], ys[4], xs[5], ys[5], xs[6], ys[6]);
        path.lineTo(bx, by);
    }
}