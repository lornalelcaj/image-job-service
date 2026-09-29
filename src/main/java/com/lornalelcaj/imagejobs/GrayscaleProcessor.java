package com.lornalelcaj.imagejobs;

import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;

@Component
public class GrayscaleProcessor implements ImageProcessor {

    @Override
    public BufferedImage process(BufferedImage input) {
        int width = input.getWidth();
        int height = input.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = input.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                output.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        return output;
    }
}