// pl.sapplayer.controller.PlayerController.java
package pl.sapplayer.controller;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPInfo;
import pl.sapplayer.engine.SAPPlayerEngine;
import pl.sapplayer.ui.MainFrame;
import pl.sapplayer.utils.ImageLoader;
import pl.sapplayer.utils.SAPFileReader;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener; // Dodaj ten import
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.awt.image.BufferedImage;

public class PlayerController {

    private final MainFrame mainFrame;
    private SAPPlayerEngine playerEngine;
    private SAPFileReader sapReader;
    private File currentSAPFile;
    private int currentSongIndex = 0;
    // Usunięto: private boolean isAdjustingProgressBarProgrammatically = false; // TA FLAGA JEST USUNIĘTA
    private ChangeListener progressBarChangeListener; // Nowa zmienna do przechowywania instancji listenera
    private int lastKnownSliderValue = 0; // Dodaj to pole do klasy PlayerController

    public PlayerController(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initListeners();
        mainFrame.getControlPanel().updateButtonStates(false, false, false);
        mainFrame.getInfoPanel().clearInfo();
        mainFrame.getImagePanel().clearImage();
        mainFrame.getImagePanel().setImage(ImageLoader.loadImageFromResources("/screens/default.png"));
    }

    private void initListeners() {
        mainFrame.getControlPanel().addOpenButtonListener(e -> openFile());
        mainFrame.getControlPanel().addPlayButtonListener(e -> playCurrentSong());
        mainFrame.getControlPanel().addPauseButtonListener(e -> pauseSong());
        mainFrame.getControlPanel().addStopButtonListener(e -> stopSong());
        mainFrame.getControlPanel().addPrevButtonListener(e -> playPreviousSong());
        mainFrame.getControlPanel().addNextButtonListener(e -> playNextSong());
        mainFrame.getControlPanel().addLoopCheckboxListener(e -> toggleLoop());
        mainFrame.getControlPanel().addVolumeSliderListener(this::changeVolume);

        // Inicjalizuj i przypisz ChangeListener do zmiennej
        progressBarChangeListener = this::seekSong;
        mainFrame.getControlPanel().addProgressBarListener(progressBarChangeListener);
    }

    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Wybierz plik SAP");
        fileChooser.setFileFilter(new FileNameExtensionFilter("SAP Files (*.sap)", "sap"));
        fileChooser.setCurrentDirectory(new File("."));

        int userSelection = fileChooser.showOpenDialog(mainFrame);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                if (playerEngine != null) {
                    playerEngine.stop();
                }

                currentSAPFile = selectedFile;
                sapReader = new SAPFileReader(currentSAPFile);
                playerEngine = new SAPPlayerEngine(sapReader.getASAP(), sapReader.getInfo());
                currentSongIndex = sapReader.getDefaultSong();

                playerEngine.setPlaybackListener(new SAPPlayerEngine.PlaybackListener() {
                    // I w metodzie onTimeUpdate, upewnij się, że aktualizujesz lastKnownSliderValue.
// (Wartość secondsPlayed z onTimeUpdate to jest to samo, co ustawiasz w ProgressBarValue)
                    @Override
                    public void onTimeUpdate(int secondsPlayed, int totalDuration) {
                        SwingUtilities.invokeLater(() -> {
                            mainFrame.getInfoPanel().setTime(secondsPlayed);
                            if (totalDuration > 0) {
                                mainFrame.getControlPanel().setProgressBarMaximum(totalDuration);
                                mainFrame.getControlPanel().setProgressBarValue(secondsPlayed);
                                lastKnownSliderValue = secondsPlayed; // KLUCZOWA ZMIANA
                            } else {
                                mainFrame.getControlPanel().setProgressBarMaximum(0);
                                mainFrame.getControlPanel().setProgressBarValue(0);
                                lastKnownSliderValue = 0; // KLUCZOWA ZMIANA
                            }
                        });
                    }

                    // Również w onSongEnd i stopSong:
                    @Override
                    public void onSongEnd() {
                        SwingUtilities.invokeLater(() -> {
                            mainFrame.getInfoPanel().setTime(0);
                            mainFrame.getControlPanel().setProgressBarValue(0);
                            lastKnownSliderValue = 0; // KLUCZOWA ZMIANA
                            // ... reszta
                        });
                    }

                    @Override
                    public void onPlaybackError(String message) {
                        showError("Błąd odtwarzania: " + message);
                        SwingUtilities.invokeLater(() -> mainFrame.getControlPanel().updateButtonStates(true, false, false));
                    }

                    @Override
                    public void onPlaybackStarted() {
                        SwingUtilities.invokeLater(() -> mainFrame.getControlPanel().updateButtonStates(true, true, false));
                    }
                });

                playerEngine.setAudioDataListener(new SAPPlayerEngine.AudioDataListener() {
                    @Override
                    public void onAudioData(byte[] buffer) {
                        SwingUtilities.invokeLater(() -> {
                            if (playerEngine.getAudioChannels() == 1) {
                                mainFrame.getVisualizerLeft().setVisible(true);
                                mainFrame.getVisualizerRight().setVisible(false);
                                mainFrame.getVisualizerLeft().updateAudioData(buffer);
                            } else { // Stereo
                                mainFrame.getVisualizerLeft().setVisible(true);
                                mainFrame.getVisualizerRight().setVisible(true);
                                byte[] left = new byte[buffer.length / 2];
                                byte[] right = new byte[buffer.length / 2];
                                for (int i = 0, j = 0; i < buffer.length; i += 4, j += 2) {
                                    left[j] = buffer[i];
                                    left[j + 1] = buffer[i + 1];
                                    right[j] = buffer[i + 2];
                                    right[j + 1] = buffer[i + 3];
                                }
                                mainFrame.getVisualizerLeft().updateAudioData(left);
                                mainFrame.getVisualizerRight().updateAudioData(right);
                            }
                        });
                    }
                });

                updateFileInfo();
                loadScreenshotForSAP(currentSAPFile);

                mainFrame.getControlPanel().updateButtonStates(true, false, false);
            } catch (Exception ex) {
                showError("Błąd podczas wczytywania pliku: " + ex.getMessage());
                ex.printStackTrace();
                mainFrame.getControlPanel().updateButtonStates(false, false, false);
            }
        }
    }

    private void playCurrentSong() {
        if (playerEngine != null && !playerEngine.isPlaying()) {
            try {
                playerEngine.play(currentSongIndex);
                mainFrame.getControlPanel().updateButtonStates(true, true, false);
            } catch (Exception ex) {
                showError("Błąd odtwarzania: " + ex.getMessage());
                ex.printStackTrace();
            }
        } else if (playerEngine != null && playerEngine.isPaused()) {
            playerEngine.resume();
            mainFrame.getControlPanel().updateButtonStates(true, true, false);
        }
    }

    private void pauseSong() {
        if (playerEngine != null && playerEngine.isPlaying() && !playerEngine.isPaused()) {
            playerEngine.pause();
            mainFrame.getControlPanel().updateButtonStates(true, true, true);
        }
    }

    private void stopSong() {
        if (playerEngine != null) {
            playerEngine.stop();
            mainFrame.getControlPanel().updateButtonStates(true, false, false);
            mainFrame.getInfoPanel().setTime(0);
            // Również tutaj reset paska musi być obsługiwany z usunięciem/dodaniem listenera
            mainFrame.getControlPanel().removeProgressBarListener(progressBarChangeListener);
            mainFrame.getControlPanel().setProgressBarValue(0);
            mainFrame.getControlPanel().addProgressBarListener(progressBarChangeListener);
            mainFrame.getVisualizerLeft().clear();
            mainFrame.getVisualizerRight().clear();
        }
    }

    private void playPreviousSong() {
        if (playerEngine != null && sapReader != null) {
            currentSongIndex--;
            if (currentSongIndex < 0) {
                currentSongIndex = sapReader.getSongsCount() - 1;
            }
            try {
                playerEngine.play(currentSongIndex);
                updateFileInfo();
            } catch (Exception ex) {
                showError("Błąd odtwarzania poprzedniego utworu: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void playNextSong() {
        if (playerEngine != null && sapReader != null) {
            currentSongIndex++;
            if (currentSongIndex >= sapReader.getSongsCount()) {
                currentSongIndex = 0;
            }
            try {
                playerEngine.play(currentSongIndex);
                updateFileInfo();
            } catch (Exception ex) {
                showError("Błąd odtwarzania następnego utworu: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void toggleLoop() {
        if (playerEngine != null) {
            boolean isLooping = mainFrame.getControlPanel().isLoopSelected();
            playerEngine.setLoopEnabled(isLooping);
        }
    }

    private void changeVolume(ChangeEvent e) {
        JSlider source = (JSlider) e.getSource();
        if (!source.getValueIsAdjusting()) {
            float volume = source.getValue() / 100.0f;
            if (playerEngine != null) {
                playerEngine.setVolume(volume);
            }
        }
    }

    // Zmieniona metoda seekSong (ChangeEvent e)
    private void seekSong(ChangeEvent e) {
        JSlider source = (JSlider) e.getSource();

        // Sprawdzamy, czy zmiana wartości jest spowodowana aktywnym przeciąganiem przez użytkownika
        if (source.getValueIsAdjusting()) {
            // Użytkownik przeciąga suwak, nie robimy nic jeszcze, tylko aktualizujemy lastKnownSliderValue
            // (opcjonalnie, jeśli chcesz wyświetlać czas podczas przewijania)
            lastKnownSliderValue = source.getValue();
            return;
        }

        // Jeśli doszliśmy tutaj, to użytkownik puścił suwak LUB wartość została zmieniona programowo.
        // Musimy odróżnić te dwa przypadki.
        // Jeżeli wartość suwaka JEST RÓŻNA od ostatniej znanej nam programowej wartości,
        // to oznacza, że użytkownik ją zmienił.
        if (source.getValue() != lastKnownSliderValue) {
            // To jest zmiana zainicjowana przez użytkownika (po puszczeniu suwaka)
            int seconds = source.getValue();
            if (playerEngine != null) {
                System.out.println("Użytkownik przewinął do: " + seconds + "s");
                playerEngine.seek(seconds);
                mainFrame.getInfoPanel().setTime(seconds);
            }
            lastKnownSliderValue = seconds; // Aktualizujemy ostatnią znaną wartość
        } else {
            // Jeśli wartość jest taka sama jak lastKnownSliderValue, to znaczy, że to była zmiana programowa
            // i powinniśmy ją zignorować w kontekście wywoływania seek().
            // Możemy tu dodać logowanie, jeśli chcemy to potwierdzić.
            // System.out.println("DEBUG: Zmiana paska postępu zignorowana (programowa lub bez faktycznej zmiany wartości).");
        }
    }
    private void updateFileInfo() {
        if (sapReader != null) {
            mainFrame.getInfoPanel().setFilePath(currentSAPFile.getName());
            mainFrame.getInfoPanel().setTitle(sapReader.getTitle());
            mainFrame.getInfoPanel().setAuthor(sapReader.getAuthor());
            mainFrame.getInfoPanel().setDate(sapReader.getDate());
            mainFrame.getInfoPanel().setSongInfo(currentSongIndex, sapReader.getSongsCount());
            mainFrame.getInfoPanel().setFormat(String.format("%d kanał(y), %d Hz, %d-bit",
                    playerEngine.getAudioChannels(),
                    (int) 44100,
                    16
            ));
        } else {
            mainFrame.getInfoPanel().clearInfo();
        }
    }

    private void loadScreenshotForSAP(File sapFile) {
        String baseName = sapFile.getName().replaceFirst("[.][^.]+$", "");
        File imageFile = new File("screens", baseName + ".png");

        System.out.println("Szukam obrazka: " + imageFile.getAbsolutePath());

        BufferedImage image = null;
        if (imageFile.exists()) {
            image = ImageLoader.loadImageFromFile(imageFile);
            System.out.println("Znaleziono grafikę: " + imageFile.getName());
        }

        if (image == null) {
            System.out.println("Brak grafiki, używam domyślnej.");
            image = ImageLoader.loadImageFromResources("/screens/default.png");
        }
        mainFrame.getImagePanel().setImage(image);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(mainFrame, message, "Błąd", JOptionPane.ERROR_MESSAGE);
    }
}