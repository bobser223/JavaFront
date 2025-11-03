package ui;

import structures.NotificationInfo;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class NotificationPopup {
    private static final int AUTO_CLOSE_MILLIS = 5000;

    private NotificationPopup() {}

    public static void show(NotificationInfo info) {
        if (info == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String title = (info.getTitle() == null || info.getTitle().isBlank())
                    ? "Notification"
                    : info.getTitle();
            String payload = (info.getPayload() == null || info.getPayload().isBlank())
                    ? "No details provided."
                    : info.getPayload();

            JOptionPane pane = new JOptionPane(payload, JOptionPane.INFORMATION_MESSAGE);
            JDialog dialog = pane.createDialog(null, title);
            dialog.setModal(false);
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);

            Timer timer = new Timer(AUTO_CLOSE_MILLIS, event -> dialog.dispose());
            timer.setRepeats(false);
            timer.start();
        });
    }
}
