package pl.sapplayer.engine;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPInfo;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger; // Potrzebne do bytesWritten w seek, jeśli jest używane

public class SAPPlayerEngine {

    private final ASAP asap;
    private final ASAPInfo info;
    private SourceDataLine line;
    private Thread playbackThread;
    private volatile boolean isPaused = false;
    private volatile boolean playing = false;
    private int currentSong = 0;
    private float currentVolume = 0.7f; // Domyślna głośność
    private boolean loopEnabled = false; // Dodana funkcja powtarzania

    public interface PlaybackListener {
        void onTimeUpdate(int secondsPlayed, int totalDuration); // Uaktualniona, aby przekazywać całkowity czas
        void onSongEnd();
        void onPlaybackError(String message);
        void onPlaybackStarted();
    }

    private PlaybackListener listener;

    public interface AudioDataListener {
        void onAudioData(byte[] buffer);
    }
    private AudioDataListener audioDataListener;

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
        stop(); // Upewnij się, że poprzednie odtwarzanie jest zatrzymane
System.out.println("Wywołano metodę play()");
        currentSong = song;
        playing = true;
        isPaused = false;

        System.out.println("▶️ Odtwarzam utwór " + song);
        System.out.println("🔊 Liczba kanałów audio: " + info.getChannels());

        int durationMs = info.getDuration(song);
        System.out.println("DEBUG: Długość utworu " + song + ": " + durationMs + " ms");

        // Oryginalne wywołanie asap.playSong, które działało
        try {
            asap.playSong(song, durationMs);
            System.out.println("DEBUG: Po asap.playSong() - Wywołanie zakończone.");
        } catch (Exception e) {
            System.err.println("DEBUG: Wyjątek podczas asap.playSong(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        int audioChannels = info.getChannels();
        AudioFormat format = new AudioFormat(44100, 16, audioChannels, true, false);
        System.out.println("🎧 AudioFormat: " + audioChannels + " kanał(y), 44100 Hz, 16-bit");

        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();

            setVolume(currentVolume); // Ustaw głośność po otwarciu linii

            if (listener != null) {
                listener.onPlaybackStarted();
            }

            playbackThread = new Thread(() -> {
                byte[] buffer = new byte[2048]; // Rozmiar bufora z Twojego starego kodu
                long startTime = System.currentTimeMillis();
                long totalBytesGenerated = 0; // Do dokładniejszego obliczania czasu

                try {
                    System.out.println("DEBUG: Wątek odtwarzania - start pętli.");
                    while (playing) {
                        if (!isPaused) {
                            int frameSize = 2 * info.getChannels(); // 2 bajty na próbkę (16-bit) * liczba kanałów
                            int framesToGenerate = buffer.length / frameSize; // Liczba ramek, które zmieszczą się w buforze

                            // *** KLUCZOWA POPRAWKA: PRZYWRÓCENIE ORYGINALNEGO WYWOŁANIA asap.generate() ***
                            int bytesGenerated = asap.generate(buffer, framesToGenerate, 1);
                            System.out.println("DEBUG: asap.generate() zwróciło " + bytesGenerated + " bajtów.");

                            if (bytesGenerated > 0) {
                                line.write(buffer, 0, bytesGenerated);
                                totalBytesGenerated += bytesGenerated;

                                if (audioDataListener != null) {
                                    audioDataListener.onAudioData(Arrays.copyOf(buffer, bytesGenerated));
                                }

                                if (listener != null) {
                                    // Obliczenie czasu na podstawie wygenerowanych bajtów
                                    int currentSeconds = (int) (totalBytesGenerated / (format.getFrameRate() * format.getFrameSize()));
                                    int totalDurationSeconds = info.getDuration(currentSong) / 1000;
                                    listener.onTimeUpdate(currentSeconds, totalDurationSeconds);
                                }
                            } else {
                                System.out.println("DEBUG: asap.generate() zwróciło <= 0. Koniec utworu lub brak danych.");
                                if (listener != null) listener.onSongEnd();
                                if (loopEnabled) {
                                    System.out.println("🔄 Powtarzam utwór...");
                                    asap.playSong(currentSong, info.getDuration(currentSong)); // Resetuj utwór
                                    totalBytesGenerated = 0; // Resetuj licznik bajtów
                                    startTime = System.currentTimeMillis(); // Resetuj czas startu
                                } else {
                                    playing = false; // Zakończ odtwarzanie
                                }
                            }
                        } else {
                            try {
                                Thread.sleep(100); // Sprawdź, czy 100ms jest OK, oryginalnie było 10ms
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                playing = false;
                            }
                        }
                    }
                    System.out.println("DEBUG: Wątek odtwarzania - pętla zakończona.");
                } catch (LineUnavailableException lue) {
                    System.err.println("❗ Błąd w wątku odtwarzania (LineUnavailableException): " + lue.getMessage());
                    if (listener != null) listener.onPlaybackError("Błąd odtwarzania (Line): " + lue.getMessage());
                    playing = false;
                } catch (IllegalArgumentException iae) {
                    System.err.println("❗ Błąd w wątku odtwarzania (IllegalArgumentException, np. z line.write): " + iae.getMessage());
                    iae.printStackTrace();
                    if (listener != null) listener.onPlaybackError("Błąd odtwarzania (Arg): " + iae.getMessage());
                    playing = false;
                } catch (Exception e) {
                    System.err.println("❗ Nieoczekiwany błąd w wątku odtwarzania: " + e.getMessage());
                    e.printStackTrace();
                    if (listener != null) listener.onPlaybackError("Nieoczekiwany błąd odtwarzania: " + e.getMessage());
                    playing = false;
                } finally {
                    System.out.println("DEBUG: Wątek odtwarzania - blok finally.");
                    cleanup();
                }
            }, "PlaybackThread");
            playbackThread.start();

        } catch (LineUnavailableException lue) {
            System.err.println("❗ Błąd podczas otwierania linii audio: " + lue.getMessage());
            if (listener != null) listener.onPlaybackError("Błąd inicjalizacji audio: " + lue.getMessage());
            playing = false;
            cleanup();
        } catch (Exception e) {
            System.err.println("❗ Nieoczekiwany błąd w play(): " + e.getMessage());
            e.printStackTrace();
            if (listener != null) listener.onPlaybackError("Nieoczekiwany błąd play(): " + e.getMessage());
            playing = false;
            cleanup();
        }
    }

    public void pause() {
        if (playing) {
            isPaused = true;
            System.out.println("⏸️ Pauza");
        }
    }

    public void resume() {
        if (playing && isPaused) {
            isPaused = false;
            System.out.println("▶️ Wznawiam");
        }
    }

    public void stop() {
        boolean wasPlaying = playing;
        playing = false;
        isPaused = false;

        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.join(500);
                System.out.println("DEBUG: Oczekiwanie na wątek odtwarzania zakończone.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("DEBUG: Oczekiwanie na wątek odtwarzania przerwane.");
            }
        }
        if (wasPlaying && line != null) { // Cleanup tylko jeśli faktycznie coś grało i linia nie jest jeszcze zamknięta
            cleanup();
        } else if (line != null) { // Jeśli linia jest, ale nie było aktywnego odtwarzania, też ją zamknij
            cleanup();
        } else {
            System.out.println("DEBUG: Cleanup pominięte w stop() - linia już zamknięta lub nieaktywna.");
        }
        System.out.println("🛑 Zatrzymuję odtwarzanie...");
    }

    private void cleanup() {
        if (line != null) {
            try {
                line.drain();
                line.stop();
                line.close();
            } catch (Exception e) {
                System.err.println("❗ Błąd przy zamykaniu linii audio: " + e.getMessage());
            } finally {
                line = null;
                System.out.println("✅ Linia audio zamknięta");
            }
        }
        playbackThread = null;
    }

    public void setVolume(float volume) {
        if (line != null && line.isOpen() && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log10(volume) * 20.0);
            dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            gainControl.setValue(dB);
            currentVolume = volume;
            System.out.println("Głośność ustawiona na: " + (int)(volume * 100) + "% (dB: " + dB + ")");
        } else {
            currentVolume = volume;
        }
    }

    /**
     * Przewija utwór do danej pozycji w sekundach.
     * Implementacja symuluje przewijanie przez ponowne uruchomienie utworu i generowanie danych do danej pozycji.
     * @param seconds Nowa pozycja w sekundach.
     */
    public void seek(int seconds) {
        System.out.println("Wywołano metodę seek()");
        if (asap != null) {
            boolean wasPlayingBeforeSeek = playing;
            stop(); // Zatrzymuje bieżące odtwarzanie

            try {
                asap.playSong(currentSong, info.getDuration(currentSong)); // Ponownie inicjuje utwór

                int sampleRate = 44100;
                int sampleSizeInBytes = 16 / 8; // 16-bit to 2 bajty
                int channels = info.getChannels();
                int frameSize = sampleSizeInBytes * channels;

                long bytesToSeek = (long) seconds * sampleRate * frameSize;

                byte[] tempBuffer = new byte[8192]; // Bufor tymczasowy do przewijania
                long bytesGenerated = 0;

                System.out.println("Przewijam... do " + seconds + "s (" + bytesToSeek + " bajtów)");

                // Generuj dane audio, aby "przewinąć" do żądanej pozycji
                while (bytesGenerated < bytesToSeek) {
                    int framesToGenerate = tempBuffer.length / frameSize;
                    int read = asap.generate(tempBuffer, framesToGenerate,1); // Poprawne wywołanie generate
                    if (read <= 0) {
                        System.out.println("DEBUG: Przewijanie napotkało koniec utworu. Odczytano: " + read + " bajtów.");
                        break;
                    }
                    bytesGenerated += read;
                }
                System.out.println("Przewinięto " + bytesGenerated + " bajtów.");

                if (wasPlayingBeforeSeek) {
                    play(currentSong); // Wznów odtwarzanie od nowej pozycji
                } else {
                    if (listener != null) {
                        listener.onTimeUpdate(seconds, info.getDuration(currentSong) / 1000); // Zaktualizuj UI
                    }
                }
                System.out.println("Przewinięto do: " + seconds + "s");

            } catch (Exception e) {
                System.err.println("Błąd podczas przewijania: " + e.getMessage());
                e.printStackTrace();
                if (listener != null) listener.onPlaybackError("Błąd przewijania: " + e.getMessage());
            }
        }
    }

    public void setLoopEnabled(boolean enabled) {
        this.loopEnabled = enabled;
        System.out.println("Powtarzanie: " + (enabled ? "Włączone" : "Wyłączone"));
    }

    public int getCurrentSong() {
        return currentSong;
    }

    public int getTotalSongs() {
        return info.getSongs();
    }

    public boolean isPaused() {
        return isPaused;
    }

    public int getAudioChannels() {
        return info.getChannels();
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getTotalDuration(int song) {
        return info.getDuration(song);
    }
}