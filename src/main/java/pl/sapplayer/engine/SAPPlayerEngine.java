package pl.sapplayer.engine;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPInfo;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class SAPPlayerEngine {

    private final ASAP asap;
    private final ASAPInfo info;
    private SourceDataLine line;
    private Thread playbackThread;
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicInteger currentSong = new AtomicInteger(0);
    private volatile float currentVolume = 0.7f;
    private volatile boolean loopEnabled = false;

    // Nowe pola dla lepszego zarządzania czasem i pozycją
    private final AtomicInteger currentTimeSeconds = new AtomicInteger(0);
    private final AtomicBoolean seekRequested = new AtomicBoolean(false);
    private final AtomicInteger seekTargetSeconds = new AtomicInteger(0);
    private final ReentrantLock seekLock = new ReentrantLock();

    // Bufor dla wizualizacji
    private volatile byte[] visualizationBuffer;
    private final Object visualizationLock = new Object();

    // Parametry audio
    private static final int SAMPLE_RATE = 44100;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int BUFFER_SIZE = 2048;

    public interface PlaybackListener {
        void onTimeUpdate(int secondsPlayed, int totalDuration);
        void onSongEnd();
        void onPlaybackError(String message);
        void onPlaybackStarted();
    }

    private volatile PlaybackListener listener;

    public interface AudioDataListener {
        void onAudioData(byte[] buffer);
    }

    private volatile AudioDataListener audioDataListener;

    public SAPPlayerEngine(ASAP asap, ASAPInfo info) {
        this.asap = asap;
        this.info = info;
    }

    public void setPlaybackListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public void setAudioDataListener(AudioDataListener listener) {
        this.audioDataListener = listener;
    }

    public void play(int song) throws Exception {
        stop(); // Zatrzymaj poprzednie odtwarzanie

        System.out.println("▶️ Odtwarzam utwór " + song);

        currentSong.set(song);
        playing.set(true);
        isPaused.set(false);
        currentTimeSeconds.set(0);
        seekRequested.set(false);

        int durationMs = info.getDuration(song);
        System.out.println("Długość utworu: " + durationMs + " ms");

        try {
            asap.playSong(song, durationMs);
        } catch (Exception e) {
            System.err.println("Błąd podczas asap.playSong(): " + e.getMessage());
            throw e;
        }

        int audioChannels = info.getChannels();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, BITS_PER_SAMPLE, audioChannels, true, false);

        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();
            setVolume(currentVolume);

            if (listener != null) {
                listener.onPlaybackStarted();
            }

            playbackThread = new Thread(this::playbackLoop, "SAPPlaybackThread");
            playbackThread.start();

        } catch (LineUnavailableException e) {
            System.err.println("Błąd podczas otwierania linii audio: " + e.getMessage());
            if (listener != null) listener.onPlaybackError("Błąd inicjalizacji audio: " + e.getMessage());
            playing.set(false);
            cleanup();
            throw e;
        }
    }

    private void playbackLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalBytesGenerated = 0;
        int frameSize = (BITS_PER_SAMPLE / 8) * info.getChannels();

        try {
            while (playing.get()) {
                // Sprawdź czy jest żądanie przewijania
                if (seekRequested.get()) {
                    handleSeek();
                    totalBytesGenerated = (long) currentTimeSeconds.get() * SAMPLE_RATE * frameSize;
                    continue;
                }

                if (!isPaused.get()) {
                    int framesToGenerate = buffer.length / frameSize;
                    int bytesGenerated = asap.generate(buffer, framesToGenerate, 1);

                    if (bytesGenerated > 0) {
                        line.write(buffer, 0, bytesGenerated);
                        totalBytesGenerated += bytesGenerated;

                        // Aktualizuj wizualizację
                        updateVisualization(buffer, bytesGenerated);

                        // Oblicz aktualny czas
                        int newTimeSeconds = (int) (totalBytesGenerated / (SAMPLE_RATE * frameSize));
                        if (newTimeSeconds != currentTimeSeconds.get()) {
                            currentTimeSeconds.set(newTimeSeconds);
                            if (listener != null) {
                                int totalDurationSeconds = info.getDuration(currentSong.get()) / 1000;
                                listener.onTimeUpdate(newTimeSeconds, totalDurationSeconds);
                            }
                        }
                    } else {
                        // Koniec utworu
                        if (listener != null) listener.onSongEnd();
                        if (loopEnabled) {
                            System.out.println("🔄 Powtarzam utwór...");
                            asap.playSong(currentSong.get(), info.getDuration(currentSong.get()));
                            totalBytesGenerated = 0;
                            currentTimeSeconds.set(0);
                        } else {
                            playing.set(false);
                        }
                    }
                } else {
                    // Tryb pauzy
                    Thread.sleep(50);
                }
            }
        } catch (Exception e) {
            System.err.println("Błąd w wątku odtwarzania: " + e.getMessage());
            if (listener != null) listener.onPlaybackError("Błąd odtwarzania: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleSeek() {
        seekLock.lock();
        try {
            if (!seekRequested.get()) return;

            int targetSeconds = seekTargetSeconds.get();
            System.out.println("Przewijam do: " + targetSeconds + "s");

            // Zrestartuj utwór
            try {
                asap.playSong(currentSong.get(), info.getDuration(currentSong.get()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Szybkie przewijanie przez generowanie danych bez odtwarzania
            if (targetSeconds > 0) {
                byte[] tempBuffer = new byte[BUFFER_SIZE];
                int frameSize = (BITS_PER_SAMPLE / 8) * info.getChannels();
                long targetBytes = (long) targetSeconds * SAMPLE_RATE * frameSize;
                long bytesGenerated = 0;

                while (bytesGenerated < targetBytes) {
                    int framesToGenerate = Math.min(tempBuffer.length / frameSize,
                            (int)((targetBytes - bytesGenerated) / frameSize));
                    int generated = asap.generate(tempBuffer, framesToGenerate, 1);
                    if (generated <= 0) break;
                    bytesGenerated += generated;
                }
            }

            currentTimeSeconds.set(targetSeconds);
            seekRequested.set(false);

        } finally {
            seekLock.unlock();
        }
    }

    private void updateVisualization(byte[] buffer, int bytesGenerated) {
        if (audioDataListener != null) {
            // Skopiuj dane dla wizualizacji w thread-safe sposób
            synchronized (visualizationLock) {
                visualizationBuffer = Arrays.copyOf(buffer, bytesGenerated);
            }

            // Wyślij dane do wizualizacji (może być wywołane z innego wątku)
            audioDataListener.onAudioData(visualizationBuffer);
        }
    }

    public void pause() {
        if (playing.get()) {
            isPaused.set(true);
            System.out.println("⏸️ Pauza");
        }
    }

    public void resume() {
        if (playing.get() && isPaused.get()) {
            isPaused.set(false);
            System.out.println("▶️ Wznawiam");
        }
    }

    public void stop() {
        boolean wasPlaying = playing.getAndSet(false);
        isPaused.set(false);
        seekRequested.set(false);

        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                playbackThread.interrupt();
            }
        }

        if (wasPlaying) {
            cleanup();
        }

        currentTimeSeconds.set(0);
        System.out.println("🛑 Zatrzymano odtwarzanie");
    }

    private void cleanup() {
        if (line != null) {
            try {
                line.drain();
                line.stop();
                line.close();
            } catch (Exception e) {
                System.err.println("Błąd przy zamykaniu linii audio: " + e.getMessage());
            } finally {
                line = null;
            }
        }
        playbackThread = null;
    }

    public void setVolume(float volume) {
        currentVolume = Math.max(0.0f, Math.min(1.0f, volume));

        if (line != null && line.isOpen() && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log10(currentVolume) * 20.0);
            dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            gainControl.setValue(dB);
        }
    }

    /**
     * Ulepszona metoda seek - nie zatrzymuje odtwarzania, tylko ustawia flagę
     */
    public void seek(int seconds) {
        if (asap != null && seconds >= 0) {
            seekLock.lock();
            try {
                seekTargetSeconds.set(seconds);
                seekRequested.set(true);
                System.out.println("Żądanie przewijania do: " + seconds + "s");
            } finally {
                seekLock.unlock();
            }
        }
    }

    public void setLoopEnabled(boolean enabled) {
        this.loopEnabled = enabled;
        System.out.println("Powtarzanie: " + (enabled ? "Włączone" : "Wyłączone"));
    }

    // Gettery
    public int getCurrentSong() { return currentSong.get(); }
    public int getTotalSongs() { return info.getSongs(); }
    public boolean isPaused() { return isPaused.get(); }
    public boolean isPlaying() { return playing.get(); }
    public int getAudioChannels() { return info.getChannels(); }
    public int getTotalDuration(int song) { return info.getDuration(song); }
    public int getCurrentTimeSeconds() { return currentTimeSeconds.get(); }
}