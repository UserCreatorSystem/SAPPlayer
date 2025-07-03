// pl.saplayer.PlayerApp.java
package pl.sapplayer;

import pl.sapplayer.controller.PlayerController; // Nowa klasa kontrolera
import pl.sapplayer.ui.MainFrame;
import javax.swing.*;

public class PlayerApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("Nie udało się ustawić systemowego wyglądu: " + e.getMessage());
            }

            MainFrame frame = new MainFrame();
            PlayerController controller = new PlayerController(frame); // Tworzymy kontroler
            frame.setVisible(true);
        });
    }
}