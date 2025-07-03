import net.sf.asap.ASAP;
import net.sf.asap.ASAPInfo;
import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;

public class SimpleSAPPlayer {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Użycie: java SimpleSAPPlayer <ścieżka_do_pliku.sap>");
            return;
        }

        try {
            // Wczytaj plik SAP
            File file = new File(args[0]);
            byte[] data = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(data);
            }

            // Inicjalizuj ASAP
            ASAP asap = new ASAP();
            asap.load(file.getName(), data, data.length);
            ASAPInfo info = asap.getInfo();

            // Pobierz domyślny utwór
            int song = info.getDefaultSong();
            asap.playSong(song, info.getDuration(song));
            System.out.println("Odtwarzanie: " + info.getTitle() + " (" + info.getAuthor() + ")");

            // Konfiguracja audio
            int channels = info.getChannels();
            AudioFormat format = new AudioFormat(44100, 16, channels, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();

            // Wątek odtwarzania
            Thread playbackThread = new Thread(() -> {
                byte[] buffer = new byte[2048]; // Zmniejszony rozmiar bufora
// System.out.println("rozmiar bufora buffer: " + buffer.length + " bajtów");
                while (true) {
                    int frames = buffer.length / (2 * channels);
// System.out.println("rozmiar frames: " + frames + " bajtów");
                    int bytesGenerated = asap.generate(buffer, frames,1);
// System.out.println("Wygenerowano: " + bytesGenerated + " bajtów");

                    if (bytesGenerated <= 0) break;
                    line.write(buffer, 0, bytesGenerated);
                }

                line.drain();
                line.stop();
                line.close();
                System.out.println("Koniec odtwarzania");
            });

            playbackThread.start();
            playbackThread.join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}