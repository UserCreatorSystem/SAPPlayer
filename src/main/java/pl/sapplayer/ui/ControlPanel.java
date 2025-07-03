package pl.sapplayer.ui;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;

public class ControlPanel extends JPanel {

    private JButton openButton;
    private JButton playButton;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton prevButton;
    private JButton nextButton;
    private JCheckBox loopCheckbox; // Przełącznik powtarzania
    private JSlider volumeSlider;   // Suwak głośności
    private JSlider progressBar;    // Pasek postępu
    private volatile boolean isAdjustingProgressBarProgrammatically = false; // TA FLAGA

    public ControlPanel() {
        setLayout(new GridBagLayout()); // Użyj GridBagLayout dla elastycznego układu
        setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Odstępy między komponentami

        // --- Row 0: Playback buttons ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel playbackButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        openButton = new JButton("Otwórz");
        prevButton = new JButton("⏪ Poprzedni");
        playButton = new JButton("▶ Start");
        pauseButton = new JButton("⏸ Pauza");
        stopButton = new JButton("🛑 Stop");
        nextButton = new JButton("⏩ Następny");

        playbackButtonsPanel.add(openButton);
        playbackButtonsPanel.add(prevButton);
        playbackButtonsPanel.add(playButton);
        playbackButtonsPanel.add(pauseButton);
        playbackButtonsPanel.add(stopButton);
        playbackButtonsPanel.add(nextButton);
        add(playbackButtonsPanel, gbc);

        // --- Row 1: Progress Bar ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; // Rozciągnij pasek postępu
        progressBar = new JSlider(0, 100, 0); // Min, Max, Current value (0-100% na początek)
        progressBar.setMajorTickSpacing(25);
        progressBar.setPaintTicks(true);
        progressBar.setPaintLabels(false); // Etykiety czasu będą obok
        add(progressBar, gbc);

        // --- Row 2: Volume, Loop, etc. ---
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE; // Nie rozciągaj
        JPanel additionalControlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0)); // Większe odstępy

        // Volume Slider
        volumeSlider = new JSlider(0, 100, 70); // 0-100%, start na 70%
        volumeSlider.setOrientation(JSlider.HORIZONTAL);
        volumeSlider.setPreferredSize(new Dimension(120, 30)); // Mniejszy rozmiar
        volumeSlider.setToolTipText("Głośność");
        // Dodaj etykietę dla głośności
        JLabel volumeLabel = new JLabel(new ImageIcon(getClass().getResource("/icons/volume_up.png"))); // Ikona głośności (założenie: folder icons w resources)
        if (volumeLabel.getIcon() == null) volumeLabel.setText("Głośność:"); // Fallback jeśli ikona nie znaleziona
        additionalControlsPanel.add(volumeLabel);
        additionalControlsPanel.add(volumeSlider);


        // Loop Checkbox
        loopCheckbox = new JCheckBox("Powtarzaj");
        additionalControlsPanel.add(loopCheckbox);

        add(additionalControlsPanel, gbc);

        updateButtonStates(false, false, false); // Początkowy stan
    }

    public void updateButtonStates(boolean fileLoaded, boolean isPlaying, boolean isPaused) {
        openButton.setEnabled(true);
        playButton.setEnabled(fileLoaded && !isPlaying);
        pauseButton.setEnabled(fileLoaded && isPlaying && !isPaused);
        stopButton.setEnabled(fileLoaded && isPlaying);
        prevButton.setEnabled(fileLoaded);
        nextButton.setEnabled(fileLoaded);
        progressBar.setEnabled(fileLoaded); // Pasek postępu aktywny tylko gdy plik załadowany
        volumeSlider.setEnabled(fileLoaded); // Suwak głośności aktywny tylko gdy plik załadowany
        loopCheckbox.setEnabled(fileLoaded);
    }

    public void setProgressBarValue(int value) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(value));
    }

    public void setProgressBarMaximum(int max) {
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(max));
    }

    public void setLoopChecked(boolean checked) {
        SwingUtilities.invokeLater(() -> loopCheckbox.setSelected(checked));
    }
    public boolean isLoopSelected() {
        return loopCheckbox.isSelected();
    }

    // Nowe metody: Getter/Setter dla flagi i Getter dla JSlider
    public boolean isAdjustingProgressBarProgrammatically() {
        return isAdjustingProgressBarProgrammatically;
    }

    public void setAdjustingProgressBarProgrammatically(boolean adjustingProgressBarProgrammatically) {
        this.isAdjustingProgressBarProgrammatically = adjustingProgressBarProgrammatically;
    }

    public JSlider getProgressBar() { // DODANO GETTER DLA JSLIDER
        return progressBar;
    }
    public void removeProgressBarListener(ChangeListener listener) {
        progressBar.removeChangeListener(listener);
    }
    // Metody do dodawania ActionListenerów i ChangeListenerów
    public void addOpenButtonListener(ActionListener listener) { openButton.addActionListener(listener); }
    public void addPlayButtonListener(ActionListener listener) { playButton.addActionListener(listener); }
    public void addPauseButtonListener(ActionListener listener) { pauseButton.addActionListener(listener); }
    public void addStopButtonListener(ActionListener listener) { stopButton.addActionListener(listener); }
    public void addPrevButtonListener(ActionListener listener) { prevButton.addActionListener(listener); }
    public void addNextButtonListener(ActionListener listener) { nextButton.addActionListener(listener); }
    public void addLoopCheckboxListener(ActionListener listener) { loopCheckbox.addActionListener(listener); }
    public void addVolumeSliderListener(ChangeListener listener) { volumeSlider.addChangeListener(listener); }
    public void addProgressBarListener(ChangeListener listener) { progressBar.addChangeListener(listener); } // Do przewijania

}