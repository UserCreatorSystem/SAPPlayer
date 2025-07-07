package pl.sapplayer.controller;

import net.sf.asap.ASAP;
import net.sf.asap.ASAPInfo;
import pl.sapplayer.engine.SAPPlayerEngine;
import pl.sapplayer.ui.MainFrame;
import pl.sapplayer.utils.ImageLoader;
import pl.sapplayer.utils.SAPFileReader;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerController {

    private final MainFrame mainFrame;
    private SAPPlayerEngine playerEngine;
    private SAPFileReader sapReader;
    private File currentSAPFile;
    private final AtomicInteger currentSongIndex = new AtomicInteger(0);

    // Ulepszone zarządzanie stanem paska postępu
    private final AtomicBoolean isUserDragging = new AtomicBoolean(false);
    private final AtomicInteger lastEngineTimeUpdate = new AtomicInteger(0);
    private ChangeListener progressBarChangeListener;

    // Debouncing dla wizualizacji
    private final AtomicBoolean visualizationUpdateInProgress = new AtomicBoolean(false);

    // Ulepszona obsługa wizualizacji z lepszym throttling
    //private final AtomicBoolean visualizationUpdateInProgress = new AtomicBoolean(false);
    private final AtomicLong lastVisualizationUpdate = new AtomicLong(0);
    private static final long VISUALIZATION_UPDATE_INTERVAL = 16; // ~60 FPS


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

        // Ulepszona obsługa paska postępu
        progressBarChangeListener = this::handleProgressBarChange;
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
                currentSongIndex.set(sapReader.getDefaultSong());

                // Ulepszone listener'y
                playerEngine.setPlaybackListener(new SAPPlayerEngine.PlaybackListener() {
                    @Override
                    public void onTimeUpdate(int secondsPlayed, int totalDuration) {
                        lastEngineTimeUpdate.set(secondsPlayed);

                        SwingUtilities.invokeLater(() -> {
                            // Aktualizuj tylko jeśli użytkownik nie przeciąga paska
                            if (!isUserDragging.get()) {
                                mainFrame.getInfoPanel().setTime(secondsPlayed);
                                if (totalDuration > 0) {
                                    mainFrame.getControlPanel().setProgressBarMaximum(totalDuration);
                                    mainFrame.getControlPanel().setProgressBarValue(secondsPlayed);
                                } else {
                                    mainFrame.getControlPanel().setProgressBarMaximum(0);
                                    mainFrame.getControlPanel().setProgressBarValue(0);
                                }
                            }
                        });
                    }

                    @Override
                    public void onSongEnd() {
                        SwingUtilities.invokeLater(() -> {
                            mainFrame.getInfoPanel().setTime(0);
                            if (!isUserDragging.get()) {
                                mainFrame.getControlPanel().setProgressBarValue(0);
                            }
                            // Nie zmieniamy stanu przycisków tutaj, bo może być loop
                        });
                    }

                    @Override
                    public void onPlaybackError(String message) {
                        showError("Błąd odtwarzania: " + message);
                        SwingUtilities.invokeLater(() ->
                                mainFrame.getControlPanel().updateButtonStates(true, false, false));
                    }

                    @Override
                    public void onPlaybackStarted() {
                        SwingUtilities.invokeLater(() ->
                                mainFrame.getControlPanel().updateButtonStates(true, true, false));
                    }
                });

                // Ulepszona obsługa wizualizacji z debouncing
                playerEngine.setAudioDataListener(new SAPPlayerEngine.AudioDataListener() {
                    @Override
                    public void onAudioData(byte[] buffer) {
                        // Lepsze throttling - nie aktualizuj wizualizacji zbyt często
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastVisualizationUpdate.get() >= VISUALIZATION_UPDATE_INTERVAL) {
                            if (visualizationUpdateInProgress.compareAndSet(false, true)) {
                                lastVisualizationUpdate.set(currentTime);

                                // Wykonaj aktualizację wizualizacji w EDT bez blokowania
                                SwingUtilities.invokeLater(() -> {
                                    try {
                                        updateVisualization(buffer);
                                    } finally {
                                        visualizationUpdateInProgress.set(false);
                                    }
                                });
                            }
                        }
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

    private void updateVisualization(byte[] buffer) {
        if (playerEngine != null && buffer != null && buffer.length > 0) {
            int channels = playerEngine.getAudioChannels();

            if (channels == 1) {
                // Mono - użyj tylko lewego wizualizatora
                mainFrame.getVisualizerLeft().setVisible(true);
                mainFrame.getVisualizerRight().setVisible(false);
                mainFrame.getVisualizerLeft().updateAudioData(buffer);
            } else if (channels == 2) {
                // Stereo - rozdziel kanały
                mainFrame.getVisualizerLeft().setVisible(true);
                mainFrame.getVisualizerRight().setVisible(true);

                // Sprawdź czy buffer ma wystarczającą długość
                if (buffer.length >= 4) {
                    int samplesPerChannel = buffer.length / 4; // 2 kanały * 2 bajty na próbkę
                    byte[] leftChannel = new byte[samplesPerChannel * 2];
                    byte[] rightChannel = new byte[samplesPerChannel * 2];

                    // Poprawione rozdzielanie kanałów (interleaved stereo)
                    for (int i = 0, left = 0, right = 0; i < buffer.length - 3; i += 4) {
                        // Lewy kanał (próbka 16-bit little-endian)
                        leftChannel[left++] = buffer[i];
                        leftChannel[left++] = buffer[i + 1];
                        // Prawy kanał (próbka 16-bit little-endian)
                        rightChannel[right++] = buffer[i + 2];
                        rightChannel[right++] = buffer[i + 3];
                    }

                    mainFrame.getVisualizerLeft().updateAudioData(leftChannel);
                    mainFrame.getVisualizerRight().updateAudioData(rightChannel);
                }
            }
        }
    }

    private void playCurrentSong() {
        if (playerEngine != null && !playerEngine.isPlaying()) {
            try {
                playerEngine.play(currentSongIndex.get());
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
            mainFrame.getControlPanel().setProgressBarValue(0);
            mainFrame.getVisualizerLeft().clear();
            mainFrame.getVisualizerRight().clear();
        }
    }

    private void playPreviousSong() {
        if (playerEngine != null && sapReader != null) {
            int newIndex = currentSongIndex.get() - 1;
            if (newIndex < 0) {
                newIndex = sapReader.getSongsCount() - 1;
            }
            currentSongIndex.set(newIndex);

            try {
                playerEngine.play(newIndex);
                updateFileInfo();
            } catch (Exception ex) {
                showError("Błąd odtwarzania poprzedniego utworu: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void playNextSong() {
        if (playerEngine != null && sapReader != null) {
            int newIndex = currentSongIndex.get() + 1;
            if (newIndex >= sapReader.getSongsCount()) {
                newIndex = 0;
            }
            currentSongIndex.set(newIndex);

            try {
                playerEngine.play(newIndex);
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

    /**
     * Ulepszona obsługa paska postępu
     */
    private void handleProgressBarChange(ChangeEvent e) {
        JSlider source = (JSlider) e.getSource();

        if (source.getValueIsAdjusting()) {
            // Użytkownik przeciąga suwak
            isUserDragging.set(true);

            // Opcjonalnie: pokaż preview czasu podczas przeciągania
            int previewTime = source.getValue();
            mainFrame.getInfoPanel().setTime(previewTime);

        } else {
            // Użytkownik puścił suwak
            isUserDragging.set(false);

            int targetSeconds = source.getValue();
            int lastKnownTime = lastEngineTimeUpdate.get();

            // Sprawdź czy to rzeczywiście zmiana zainicjowana przez użytkownika
            if (Math.abs(targetSeconds - lastKnownTime) > 1) { // Tolerancja 1 sekunda
                if (playerEngine != null) {
                    System.out.println("Użytkownik przewinął do: " + targetSeconds + "s");
                    playerEngine.seek(targetSeconds);
                }
            }
        }
    }

    private void updateFileInfo() {
        if (sapReader != null) {
            int songIndex = currentSongIndex.get();
            mainFrame.getInfoPanel().setFilePath(currentSAPFile.getName());
            mainFrame.getInfoPanel().setTitle(sapReader.getTitle());
            mainFrame.getInfoPanel().setAuthor(sapReader.getAuthor());
            mainFrame.getInfoPanel().setDate(sapReader.getDate());
            mainFrame.getInfoPanel().setSongInfo(songIndex, sapReader.getSongsCount());
            mainFrame.getInfoPanel().setFormat(String.format("%d kanał(y), %d Hz, %d-bit",
                    playerEngine.getAudioChannels(),
                    44100,
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