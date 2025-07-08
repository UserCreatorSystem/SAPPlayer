package pl.sapplayer.visuals;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

public class AudioVisualizerPanel extends JPanel {
    private final AtomicReference<short[]> audioSamples = new AtomicReference<>();
    private double gain = 2.0;
    private Color waveformColor = new Color(0, 255, 100);
    private Color backgroundColor = Color.BLACK;
    private VisualizationMode mode = VisualizationMode.WAVEFORM;

    public enum VisualizationMode {
        WAVEFORM, SPECTRUM, VU_METER
    }

    public AudioVisualizerPanel() {
        setBackground(backgroundColor);
        setDoubleBuffered(true);
        setPreferredSize(new Dimension(400, 100));
    }

    public void updateAudioData(byte[] rawData) {
        if (rawData == null || rawData.length < 4) {
            audioSamples.set(null);
            repaint();
            return;
        }

        short[] samples = new short[rawData.length / 2];
        for (int i = 0, j = 0; i < rawData.length - 1; i += 2, j++) {
            samples[j] = (short) ((rawData[i + 1] << 8) | (rawData[i] & 0xFF));
        }

        audioSamples.set(samples);
        repaint();
    }

    public void updateAudioSamples(short[] samples) {
        audioSamples.set(samples);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        short[] samples = audioSamples.get();
        if (samples == null || samples.length == 0) {
            drawNoSignalMessage(g2, width, height);
            return;
        }

        switch (mode) {
            case WAVEFORM:
                drawWaveform(g2, samples, width, height);
                break;
            case SPECTRUM:
                drawSimpleSpectrum(g2, samples, width, height);
                break;
            case VU_METER:
                drawVUMeter(g2, samples, width, height);
                break;
        }
    }

    private void drawWaveform(Graphics2D g2, short[] samples, int width, int height) {
        int centerY = height / 2;

        // Linia środkowa
        g2.setColor(new Color(50, 50, 50));
        g2.drawLine(0, centerY, width, centerY);

        // Fala
        g2.setColor(waveformColor);
        g2.setStroke(new BasicStroke(1.0f));

        for (int x = 0; x < width; x++) {
            int sampleIndex = (x * samples.length) / width;
            if (sampleIndex >= samples.length) break;

            int sample = samples[sampleIndex];
            int y = centerY - (int) ((sample / 32768.0) * gain * (height / 2 - 10));
            y = Math.max(5, Math.min(height - 5, y));

            g2.drawLine(x, centerY, x, y);
        }
    }

    private void drawSimpleSpectrum(Graphics2D g2, short[] samples, int width, int height) {
        int bands = Math.min(width / 4, 64);
        float[] spectrum = new float[bands];

        // Proste grupowanie próbek w pasma
        int samplesPerBand = samples.length / bands;
        for (int band = 0; band < bands; band++) {
            float sum = 0;
            int start = band * samplesPerBand;
            int end = Math.min(start + samplesPerBand, samples.length);

            for (int i = start; i < end; i++) {
                sum += Math.abs(samples[i]);
            }
            spectrum[band] = sum / samplesPerBand;
        }

        // Rysowanie słupków
        int barWidth = Math.max(1, width / bands);
        for (int i = 0; i < bands; i++) {
            int barHeight = (int) ((spectrum[i] / 32768.0) * gain * height * 0.8);
            barHeight = Math.max(0, Math.min(height - 10, barHeight));

            // Kolorowanie według wysokości
            float intensity = Math.min(1.0f, spectrum[i] / 16384.0f);
            Color barColor = new Color(intensity, 1.0f - intensity * 0.5f, 0.2f);
            g2.setColor(barColor);

            int x = i * barWidth;
            g2.fillRect(x, height - barHeight, barWidth - 1, barHeight);
        }
    }

    private void drawVUMeter(Graphics2D g2, short[] samples, int width, int height) {
        // Oblicz RMS i peak
        long sumSquares = 0;
        int peak = 0;

        for (int sample : samples) {
            int abs = Math.abs(sample);
            sumSquares += (long) sample * sample;
            peak = Math.max(peak, abs);
        }

        float rms = (float) Math.sqrt((double) sumSquares / samples.length);
        float peakLevel = peak / 32768.0f;
        float rmsLevel = rms / 32768.0f;

        // Tło
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(10, 10, width - 20, height - 20);

        // Słupek RMS
        int rmsWidth = (int) (rmsLevel * (width - 40));
        g2.setColor(Color.GREEN);
        g2.fillRect(20, height / 2 - 15, rmsWidth, 10);

        // Słupek Peak
        int peakWidth = (int) (peakLevel * (width - 40));
        g2.setColor(Color.RED);
        g2.fillRect(20, height / 2 + 5, peakWidth, 10);

        // Etykiety
        g2.setColor(Color.WHITE);
        g2.drawString("RMS", 20, height / 2 - 5);
        g2.drawString("PEAK", 20, height / 2 + 25);
    }

    private void drawNoSignalMessage(Graphics2D g2, int width, int height) {
        g2.setColor(new Color(100, 100, 100));
        String msg = "Brak sygnału audio";
        FontMetrics fm = g2.getFontMetrics();
        int x = (width - fm.stringWidth(msg)) / 2;
        int y = (height - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(msg, x, y);
    }

    // Publiczne API
    public void setVisualizationMode(VisualizationMode mode) {
        this.mode = mode;
        repaint();
    }

    public void setGain(double gain) {
        this.gain = Math.max(0.1, Math.min(10.0, gain));
        repaint();
    }

    public void setWaveformColor(Color color) {
        this.waveformColor = color;
        repaint();
    }

    public void clear() {
        audioSamples.set(null);
        repaint();
    }

    public VisualizationMode getVisualizationMode() {
        return mode;
    }
}