// pl.saplayer.visuals.AudioVisualizerPanel.java
package pl.sapplayer.visuals;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class AudioVisualizerPanel extends JPanel {
    private byte[] audioData; // Surowe dane audio (interleaved stereo lub mono)
    private double gain = 1.0; // Wzmocnienie wizualizacji (domyślnie 1.0)
    private Color waveformColor = new Color(0, 255, 100); // Kolor fali (zielony)
    private Color backgroundColor = Color.BLACK; // Kolor tła
    private boolean showCenterLine = true;

    private BufferedImage bufferImage; // Buforowany obraz do rysowania
    private Graphics2D bufferGraphics; // Kontekst graficzny dla buforowanego obrazu

    public AudioVisualizerPanel() {
        setBackground(backgroundColor);
        // Nie ma już wewnętrznego timera! Repaint będzie wywoływany z zewnątrz.
    }

    /**
     * Ustawia wzmocnienie (gain) dla wizualizacji.
     * @param gain wartość wzmocnienia (np. 1.0 dla domyślnej, >1.0 dla zwiększenia)
     */
    public void setGain(double gain) {
        this.gain = Math.max(0.1, Math.min(5.0, gain)); // Ograniczenie wartości gain
        SwingUtilities.invokeLater(this::repaint); // Odśwież widok po zmianie
    }

    /**
     * Aktualizuje dane audio do wizualizacji.
     * Ta metoda powinna być wywoływana z wątku odtwarzania,
     * ale repaint() musi być w EDT.
     * @param data tablica bajtów zawierająca dane audio (16-bit, Little-Endian)
     */
    public void updateAudioData(byte[] data) {
        // Skopiuj dane, aby uniknąć problemów z modyfikacją oryginalnego bufora
        this.audioData = (data != null) ? Arrays.copyOf(data, data.length) : null;
        SwingUtilities.invokeLater(this::repaint); // Zawsze wywołuj repaint w EDT
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // Rysuje tło panelu (aktualnie ustawione na backgroundColor)

        int width = getWidth();
        int height = getHeight();

        // Sprawdź, czy bufor obrazu musi zostać utworzony/zmieniony
        if (bufferImage == null || bufferImage.getWidth() != width || bufferImage.getHeight() != height) {
            bufferImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            bufferGraphics = bufferImage.createGraphics();
        }

        // Wyczyść buforowany obraz
        bufferGraphics.setColor(backgroundColor);
        bufferGraphics.fillRect(0, 0, width, height);

        // Sprawdź, czy są dane audio do rysowania
        if (audioData == null || audioData.length < 2) {
            drawNoSignalMessage(bufferGraphics);
        } else {
            // Ustawienia dla rysowania
            bufferGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Rysuj linię środkową
            if (showCenterLine) {
                int centerY = height / 2;
                bufferGraphics.setColor(new Color(50, 50, 50)); // Ciemniejszy szary
                bufferGraphics.drawLine(0, centerY, width, centerY);
            }

            // Rysuj falę audio
            bufferGraphics.setColor(waveformColor);
            drawWaveform(bufferGraphics);
        }

        // Rysuj buforowany obraz na panelu
        g.drawImage(bufferImage, 0, 0, null);
    }

    private void drawWaveform(Graphics2D g2) {
        int width = getWidth();
        int height = getHeight();
        int centerY = height / 2;

        // Określ, ile próbek będziemy rysować na szerokość panelu.
        // Chcemy, aby fala była widoczna, więc nie rysujemy każdej próbki,
        // jeśli bufor jest bardzo duży, a panel wąski.
        // 16-bitowe próbki, więc 2 bajty na próbkę.
        int numSamples = audioData.length / 2; // Całkowita liczba 16-bitowych próbek
        int pointsToDraw = Math.min(width, numSamples); // Rysujemy co najwyżej tyle punktów, ile pikseli szerokości

        if (pointsToDraw == 0) return;

        // Oblicz współczynnik skalowania, aby dopasować dane audio do szerokości panelu
        double sampleStep = (double) numSamples / pointsToDraw;

        int prevX = 0;
        int prevY = centerY;

        for (int i = 0; i < pointsToDraw; i++) {
            // Indeks próbki w oryginalnym buforze (float, bo może być ułamkowy)
            int sampleIndex = (int) (i * sampleStep);
            int byteIndex = sampleIndex * 2; // Bajtowy indeks dla 16-bitowych próbek

            if (byteIndex + 1 >= audioData.length) break; // Zabezpieczenie przed wyjściem poza zakres

            // Odczytanie 16-bitowej próbki (Little-Endian)
            // (MSB << 8) | (LSB & 0xFF)
            int sample = (audioData[byteIndex + 1] << 8) | (audioData[byteIndex] & 0xFF);

            // Skalowanie próbki do wysokości panelu
            // Wartość 32768.0 to maksymalna wartość dla 16-bit signed int (-32768 do 32767)
            // Wynik skalowania powinien być w zakresie od -centerY do +centerY, a następnie przesunięty
            double scaledSample = (double) sample * gain / 32768.0; // -1.0 do 1.0 (z gainem)
            int y = centerY - (int)(scaledSample * centerY); // Odwrócony Y: wyższe wartości idą w górę

            int x = i; // Rysujemy każdy punkt na osobnym pikselu X

            if (i > 0) {
                g2.drawLine(prevX, prevY, x, y);
            }

            prevX = x;
            prevY = y;
        }
    }

    private void drawNoSignalMessage(Graphics2D g2) {
        g2.setColor(new Color(100, 100, 100)); // Szary
        String msg = "Brak sygnału audio";
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(msg)) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(msg, x, y);
    }

    // Dodatkowe settery dla kolorów i linii, jeśli chcesz je konfigurować
    public void setWaveformColor(Color color) {
        this.waveformColor = color;
        SwingUtilities.invokeLater(this::repaint);
    }

    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
        setBackground(color); // Zaktualizuj tło JPanelu
        SwingUtilities.invokeLater(this::repaint);
    }

    public void setShowCenterLine(boolean show) {
        this.showCenterLine = show;
        SwingUtilities.invokeLater(this::repaint);
    }

    /**
     * Czyści wizualizator, usuwając wszelkie dane i rysując puste tło.
     */
    public void clear() {
        this.audioData = null;
        SwingUtilities.invokeLater(this::repaint);
    }
}