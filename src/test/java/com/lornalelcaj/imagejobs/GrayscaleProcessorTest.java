package com.lornalelcaj.imagejobs;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class GrayscaleProcessorTest {

    private final GrayscaleProcessor processor = new GrayscaleProcessor();

    @Test
    void keepsImageDimensions() {
        BufferedImage input = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);

        BufferedImage output = processor.process(input);

        assertThat(output.getWidth()).isEqualTo(40);
        assertThat(output.getHeight()).isEqualTo(30);
    }

    @Test
    void convertsPureRedToExpectedGray() {
        BufferedImage input = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        input.setRGB(0, 0, 0xFF0000);

        int pixel = processor.process(input).getRGB(0, 0);

        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        assertThat(r).isEqualTo(76); // 0.299 * 255
        assertThat(g).isEqualTo(76);
        assertThat(b).isEqualTo(76);
    }

    @Test
    void keepsBlackBlack() {
        BufferedImage input = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        int pixel = processor.process(input).getRGB(0, 0);

        assertThat(pixel & 0xFFFFFF).isEqualTo(0);
    }
}