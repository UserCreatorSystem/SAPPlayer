// pl.saplayer.ui.InfoPanel.java
package pl.sapplayer.ui;

import javax.swing.*;
import java.awt.*;

public class InfoPanel extends JPanel {

    private JLabel filePathLabel;
    private JLabel titleLabel;
    private JLabel authorLabel;
    private JLabel dateLabel;
    private JLabel songLabel;
    private JLabel timeLabel;
    private JLabel formatLabel;

    public InfoPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); // Układ pionowy

        filePathLabel = new JLabel("Plik: -");
        titleLabel = new JLabel("Tytuł: -");
        authorLabel = new JLabel("Autor: -");
        dateLabel = new JLabel("Data: -");
        songLabel = new JLabel("Utwór: - / -");
        timeLabel = new JLabel("Czas: 00:00");
        formatLabel = new JLabel("Format: -");

      //  Font infoFont = new Font("Monospaced", Font.PLAIN, 12);

        // Dodajemy etykiety do panelu
        for (JLabel label : new JLabel[]{filePathLabel, titleLabel, authorLabel, dateLabel, songLabel, timeLabel, formatLabel}) {
        //    label.setFont(infoFont);
            label.setAlignmentX(Component.LEFT_ALIGNMENT); // Wyrównanie do lewej
            add(label);
        }

        setBorder(BorderFactory.createTitledBorder("Informacje o utworze"));
    }

    // Metody do aktualizacji informacji
    public void setFilePath(String path) {
        filePathLabel.setText("Plik: " + path);
    }

    public void setTitle(String title) {
        titleLabel.setText("Tytuł: " + title);
    }

    public void setAuthor(String author) {
        authorLabel.setText("Autor: " + author);
    }

    public void setDate(String date) {
        dateLabel.setText("Data: " + date);
    }

    public void setSongInfo(int currentSong, int totalSongs) {
        songLabel.setText("Utwór: " + (currentSong + 1) + " / " + totalSongs);
    }

    public void setTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        timeLabel.setText(String.format("Czas: %02d:%02d", min, sec));
    }

    public void setFormat(String format) {
        formatLabel.setText("Format: " + format);
    }

    public void clearInfo() {
        filePathLabel.setText("Plik: -");
        titleLabel.setText("Tytuł: -");
        authorLabel.setText("Autor: -");
        dateLabel.setText("Data: -");
        songLabel.setText("Utwór: - / -");
        timeLabel.setText("Czas: 00:00");
        formatLabel.setText("Format: -");
    }
}