// pl.saplayer.utils.ImageLoader.java
package pl.sapplayer.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ImageLoader {

    public static BufferedImage loadImageFromFile(File file) {
        try {
            if (file != null && file.exists()) {
                return ImageIO.read(file);
            }
        } catch (IOException e) {
            System.err.println("Błąd wczytywania obrazu z pliku " + file.getAbsolutePath() + ": " + e.getMessage());
        }
        return null;
    }

    public static BufferedImage loadImageFromResources(String path) {
        try {
            // Użyj ClassLoader do wczytania zasobów z JARa
            InputStream is = ImageLoader.class.getResourceAsStream(path);
            if (is != null) {
                return ImageIO.read(is);
            } else {
                System.err.println("Nie znaleziono zasobu: " + path);
            }
        } catch (IOException e) {
            System.err.println("Błąd wczytywania obrazu z zasobów " + path + ": " + e.getMessage());
        }
        return null;
    }
}