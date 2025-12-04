package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import structures.NotificationInfo;

public final class NotificationPopup {
  private static final int AUTO_CLOSE_MILLIS = 5000;
  private static final int WIDTH = 360;
  private static final int HEIGHT = 180;
  private static final int SCREEN_MARGIN = 20;

  private NotificationPopup() {}

  public static void show(NotificationInfo info) {
    if (info == null) {
      return;
    }

    SwingUtilities.invokeLater(
        () -> {
          String title =
              (info.getTitle() == null || info.getTitle().isBlank())
                  ? "Notification"
                  : info.getTitle();
          String payload =
              (info.getPayload() == null || info.getPayload().isBlank())
                  ? "No details provided."
                  : info.getPayload();

          JDialog dialog = new JDialog((JDialog) null, false);
          dialog.setTitle(title);
          dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
          dialog.setAlwaysOnTop(true);
          dialog.setResizable(false);

          JPanel root = new JPanel(new BorderLayout(10, 10));
          root.setBorder(
              BorderFactory.createCompoundBorder(
                  BorderFactory.createLineBorder(new Color(70, 105, 170)),
                  new EmptyBorder(12, 14, 12, 14)));

          JLabel titleLabel = new JLabel(title);
          titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
          titleLabel.setForeground(new Color(40, 60, 110));
          root.add(titleLabel, BorderLayout.NORTH);

          JTextArea body = new JTextArea(payload);
          body.setEditable(false);
          body.setLineWrap(true);
          body.setWrapStyleWord(true);
          body.setFont(body.getFont().deriveFont(14f));
          body.setBorder(BorderFactory.createEmptyBorder());
          body.setBackground(new Color(248, 249, 252));
          root.add(body, BorderLayout.CENTER);

          JPanel actions = new JPanel(new BorderLayout());
          JLabel hint = new JLabel("Auto closes in " + (AUTO_CLOSE_MILLIS / 1000) + "s");
          hint.setHorizontalAlignment(SwingConstants.LEFT);
          JButton close = new JButton("Dismiss");
          close.addActionListener(e -> dialog.dispose());
          actions.add(hint, BorderLayout.WEST);
          actions.add(close, BorderLayout.EAST);
          root.add(actions, BorderLayout.SOUTH);

          dialog.setContentPane(root);
          dialog.setSize(new Dimension(WIDTH, HEIGHT));
          dialog.pack();

          positionDialog(dialog);
          dialog.setVisible(true);

          Timer timer = new Timer(AUTO_CLOSE_MILLIS, event -> dialog.dispose());
          timer.setRepeats(false);
          timer.start();
        });
  }

  private static void positionDialog(JDialog dialog) {
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    int x = (int) (screen.getWidth() - dialog.getWidth() - SCREEN_MARGIN);
    int y = (int) (screen.getHeight() - dialog.getHeight() - SCREEN_MARGIN);
    dialog.setLocation(x, y);
  }
}
