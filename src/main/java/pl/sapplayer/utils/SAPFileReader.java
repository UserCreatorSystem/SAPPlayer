package pl.sapplayer.utils;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPInfo;

import java.io.File;
import java.io.FileInputStream;

public class SAPFileReader {

    private final File file;
    private ASAP asap;
    private ASAPInfo info;

    public SAPFileReader(File file) throws Exception {
        this.file = file;
        load();
    }

    private void load() throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }

        asap = new ASAP();
        asap.load(file.getName(), data, data.length);
        info = asap.getInfo();
    }

    public String getTitle() {
        return info.getTitle();
    }

    public String getAuthor() {
        return info.getAuthor();
    }

    public String getDate() {
        return info.getDate();
    }

    public int getSongsCount() {
        return info.getSongs();
    }

    public int getDefaultSong() {
        return info.getDefaultSong();
    }

    public int getDuration(int song) {
        return info.getDuration(song);
    }

    public ASAP getASAP() {
        return asap;
    }

    public ASAPInfo getInfo() {
        return info;
    }

    public File getFile() {
        return file;
    }

    public int getAudioChannels() {
        // POPRAWKA: ZWRACAMY RZECZYWISTĄ LICZBĘ KANAŁÓW Z BIBLIOTEKI ASAP
        System.out.println("SAPFileReadergetAudioChannels():" + info.getChannels());
        return info.getChannels();
    }

    public String getAudioMode() {
        System.out.println("SAPFileReadergetAudioMode():" + info.getChannels());
        return getAudioChannels() == 2 ? "Stereo" : "Mono";
    }

}