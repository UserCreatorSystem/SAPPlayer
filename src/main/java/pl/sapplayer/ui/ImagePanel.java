// pl.saplayer.ui.ImagePanel.java
package pl.sapplayer.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImagePanel extends JPanel {

    private JLabel imageLabel;
    private static final int IMAGE_WIDTH = 320;
    private static final int IMAGE_HEIGHT = 192;

    public ImagePanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(IMAGE_WIDTH + 20, IMAGE_HEIGHT + 20)); // Dodatkowy padding
        setBorder(BorderFactory.createTitledBorder("Grafika / Screenshot"));

        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        // Ustawienie domyślnego pustego obrazka na start
        setImage(new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB));

        add(imageLabel, BorderLayout.CENTER);
    }

    public void setImage(BufferedImage image) {
        if (image != null) {
            // Skalowanie obrazu do docelowego rozmiaru
            Image scaledImage = image.getScaledInstance(IMAGE_WIDTH, IMAGE_HEIGHT, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaledImage));
        } else {
            // Wyczyść obraz, jeśli jest null
            imageLabel.setIcon(null);
        }
    }

    public void clearImage() {
        setImage(new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB)); // Pusty czarny obraz
    }
}