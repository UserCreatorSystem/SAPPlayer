// pl.saplayer.visuals.AudioVisualizerPanel.java
package pl.sapplayer.visuals;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AudioVisualizerPanel extends JPanel {
    private volatile byte[] audioData; // Surowe dane audio (interleaved stereo lub mono)
    private double gain = 2.0; // Zwiększone wzmocnienie wizualizacji
    private Color waveformColor = new Color(0, 255, 100); // Kolor fali (zielony)
    private Color backgroundColor = Color.BLACK; // Kolor tła
    private boolean showCenterLine = true;

    private BufferedImage bufferImage; // Buforowany obraz do rysowania
    private Graphics2D bufferGraphics; // Kontekst graficzny dla buforowanego obrazu

    // Throttling dla lepszej wydajności
    private final AtomicBoolean updatePending = new AtomicBoolean(false);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    private static final long MIN_UPDATE_INTERVAL = 33; // ~30 FPS (33ms)

    public AudioVisualizerPanel() {
        setBackground(backgroundColor);
        setDoubleBuffered(true); // Włącz podwójne buforowanie
        setPreferredSize(new Dimension(400, 100)); // Domyślny rozmiar
    }

    /**
     * Ustawia wzmocnienie (gain) dla wizualizacji.
     * @param gain wartość wzmocnienia (np. 1.0 dla domyślnej, >1.0 dla zwiększenia)
     */
    public void setGain(double gain) {
        this.gain = Math.max(0.1, Math.min(10.0, gain)); // Zwiększony zakres
        requestRepaint();
    }

    /**
     * Aktualizuje dane audio do wizualizacji z throttling.
     * @param data tablica bajtów zawierająca dane audio (16-bit, Little-Endian)
     */
    public void updateAudioData(byte[] data) {
        if (data == null || data.length < 4) {
            this.audioData = null;
            requestRepaint();
            return;
        }

        // Skopiuj dane, aby uniknąć problemów z modyfikacją oryginalnego bufora
        this.audioData = Arrays.copyOf(data, data.length);

        // Throttled repaint dla lepszej wydajności
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime.get() >= MIN_UPDATE_INTERVAL) {
            if (updatePending.compareAndSet(false, true)) {
                lastUpdateTime.set(currentTime);
                SwingUtilities.invokeLater(() -> {
                    updatePending.set(false);
                    repaint();
                });
            }
        }
    }

    private void requestRepaint() {
        if (updatePending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                updatePending.set(false);
                repaint();
            });
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        // Sprawdź, czy bufor obrazu musi zostać utworzony/zmieniony
        if (bufferImage == null || bufferImage.getWidth() != width || bufferImage.getHeight() != height) {
            if (bufferGraphics != null) {
                bufferGraphics.dispose();
            }
            bufferImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            bufferGraphics = bufferImage.createGraphics();
            bufferGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            bufferGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }

        // Wyczyść buforowany obraz
        bufferGraphics.setColor(backgroundColor);
        bufferGraphics.fillRect(0, 0, width, height);

        // Sprawdź, czy są dane audio do rysowania
        if (audioData == null || audioData.length < 4) {
            drawNoSignalMessage(bufferGraphics, width, height);
        } else {
            // Rysuj linię środkową
            if (showCenterLine) {
                int centerY = height / 2;
                bufferGraphics.setColor(new Color(50, 50, 50));
                bufferGraphics.drawLine(0, centerY, width, centerY);
            }

            // Rysuj falę audio
            bufferGraphics.setColor(waveformColor);
            drawWaveform(bufferGraphics, width, height);
        }

        // Rysuj buforowany obraz na panelu
        g.drawImage(bufferImage, 0, 0, null);
    }

    private void drawWaveform(Graphics2D g2, int width, int height) {
        int centerY = height / 2;

        // Poprawiona konwersja danych audio
        int numSamples = audioData.length / 2; // 16-bit próbki
        if (numSamples == 0) return;

        // Określ ile próbek na piksel
        double samplesPerPixel = (double) numSamples / width;

        // Użyj Stroke dla lepszego renderowania
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int[] xPoints = new int[width];
        int[] yPoints = new int[width];

        // Generuj punkty dla całej szerokości
        for (int x = 0; x < width; x++) {
            double samplePos = x * samplesPerPixel;
            int sampleIndex = (int) samplePos;

            // Zabezpieczenie przed wyjściem poza zakres
            if (sampleIndex * 2 + 1 >= audioData.length) {
                sampleIndex = (audioData.length / 2) - 1;
            }

            int byteIndex = sampleIndex * 2;

            // Poprawiona konwersja Little-Endian 16-bit signed
            int sample = (audioData[byteIndex + 1] << 8) | (audioData[byteIndex] & 0xFF);

            // Konwersja do signed int (jeśli potrzebne)
            if (sample > 32767) {
                sample -= 65536;
            }

            // Skalowanie z wzmocnieniem
            double normalizedSample = (double) sample / 32768.0 * gain;

            // Ograniczenie zakresu
            normalizedSample = Math.max(-1.0, Math.min(1.0, normalizedSample));

            // Konwersja do współrzędnych ekranu
            int y = centerY - (int) (normalizedSample * (height / 2 - 2));

            xPoints[x] = x;
            yPoints[x] = y;
        }

        // Rysuj linię łamaną przez wszystkie punkty
        if (width > 1) {
            g2.drawPolyline(xPoints, yPoints, width);
        }

        // Dodatkowe podświetlenie dla wyższych amplitud
        g2.setColor(new Color(waveformColor.getRed(), waveformColor.getGreen(), waveformColor.getBlue(), 100));
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Rysuj tylko punkty o wysokiej amplitudzie
        for (int x = 0; x < width; x++) {
            double samplePos = x * samplesPerPixel;
            int sampleIndex = (int) samplePos;

            if (sampleIndex * 2 + 1 >= audioData.length) continue;

            int byteIndex = sampleIndex * 2;
            int sample = (audioData[byteIndex + 1] << 8) | (audioData[byteIndex] & 0xFF);

            if (sample > 32767) sample -= 65536;

            double normalizedSample = Math.abs((double) sample / 32768.0 * gain);

            // Rysuj dodatkowe podświetlenie dla amplitud > 50%
            if (normalizedSample > 0.5) {
                int y = yPoints[x];
                g2.drawLine(x, y - 1, x, y + 1);
            }
        }
    }

    private void drawNoSignalMessage(Graphics2D g2, int width, int height) {
        g2.setColor(new Color(100, 100, 100));
        String msg = "Brak sygnału audio";
        FontMetrics fm = g2.getFontMetrics();
        int x = (width - fm.stringWidth(msg)) / 2;
        int y = (height - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(msg, x, y);
    }

    // Dodatkowe settery dla kolorów i linii
    public void setWaveformColor(Color color) {
        this.waveformColor = color;
        requestRepaint();
    }

    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
        setBackground(color);
        requestRepaint();
    }

    public void setShowCenterLine(boolean show) {
        this.showCenterLine = show;
        requestRepaint();
    }

    public double getGain() {
        return gain;
    }

    /**
     * Czyści wizualizator, usuwając wszelkie dane i rysując puste tło.
     */
    public void clear() {
        this.audioData = null;
        requestRepaint();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        // Cleanup przy usuwaniu komponentu
        if (bufferGraphics != null) {
            bufferGraphics.dispose();
            bufferGraphics = null;
        }
        if (bufferImage != null) {
            bufferImage.flush();
            bufferImage = null;
        }
    }
}