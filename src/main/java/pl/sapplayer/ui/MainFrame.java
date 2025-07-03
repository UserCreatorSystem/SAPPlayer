// pl.saplayer.ui.MainFrame.java
package pl.sapplayer.ui;

import pl.sapplayer.visuals.AudioVisualizerPanel; // Ważna zmiana pakietu
import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private AudioVisualizerPanel visualizerLeft;
    private AudioVisualizerPanel visualizerRight;
    private InfoPanel infoPanel;
    private ImagePanel imagePanel;
    private ControlPanel controlPanel;

    public MainFrame() {
        setTitle("Nowy SAP Player");
        // Zwiększony rozmiar, aby pomieścić nowe kontrolki
        setSize(850, 750); // Dostosuj w miarę potrzeby
        setMinimumSize(new Dimension(700, 650));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10)); // Dodajemy odstępy

        // --- GÓRNY PANEL: Wizualizatory ---
        visualizerLeft = new AudioVisualizerPanel();
        visualizerRight = new AudioVisualizerPanel();
        visualizerLeft.setPreferredSize(new Dimension(0, 100)); // Wysokość 100px
        visualizerRight.setPreferredSize(new Dimension(0, 100)); // Wysokość 100px

        JPanel visualizersContainer = new JPanel(new GridLayout(2, 1, 5, 5));
        visualizersContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        visualizersContainer.add(visualizerLeft);
        visualizersContainer.add(visualizerRight);
        add(visualizersContainer, BorderLayout.NORTH);

        // --- CENTRALNY PANEL: Informacje i Grafika ---
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        infoPanel = new InfoPanel();
        imagePanel = new ImagePanel();

        centerPanel.add(imagePanel, BorderLayout.CENTER);
        centerPanel.add(infoPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        // --- DOLNY PANEL: Przyciski sterujące i suwaki ---
        controlPanel = new ControlPanel();
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        add(controlPanel, BorderLayout.SOUTH);

        visualizerRight.setVisible(false); // Domyślnie ukryj prawy wizualizator (dla mono)
    }

    // --- Metody dostępu ---
    public AudioVisualizerPanel getVisualizerLeft() { return visualizerLeft; }
    public AudioVisualizerPanel getVisualizerRight() { return visualizerRight; }
    public InfoPanel getInfoPanel() { return infoPanel; }
    public ImagePanel getImagePanel() { return imagePanel; }
    public ControlPanel getControlPanel() { return controlPanel; }
}