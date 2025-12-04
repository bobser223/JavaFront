import db.DataBaseWrapper;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import logger.Logger;
import structures.NotificationInfo;
import web.Client;

public class AppWindow extends JFrame {
  private final DataBaseWrapper db;
  private final Clock clock;
  private Thread clockThread;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();

  private final JTextField hostField = new JTextField("127.0.0.1", 12);
  private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(1488, 1, 65535, 1));
  private final JTextField usernameField = new JTextField(12);
  private final JPasswordField passwordField = new JPasswordField(12);
  private final JCheckBox registerCheckBox = new JCheckBox("Register new user");
  private final JLabel authStatusLabel = new JLabel("Not authenticated");
  private final JLabel clockStatusLabel = new JLabel("Clock stopped");
  private final JLabel adminStatusLabel = new JLabel("Admin: unknown");

  private final JTextField titleField = new JTextField(18);
  private final JTextField payloadField = new JTextField(18);
  private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(30, 0, 86_400, 5));
  private final JCheckBox sendToWebCheckBox = new JCheckBox("Send to server", true);
  private final JCheckBox adminDeleteCheckBox = new JCheckBox("Use admin endpoint");

  private final JTextField adminUserField = new JTextField(12);
  private final JPasswordField adminUserPasswordField = new JPasswordField(12);
  private final JCheckBox adminGrantCheckBox = new JCheckBox("Grant admin rights");
  private final JTextField deleteUsersField = new JTextField(18);

  private final NotificationTableModel tableModel = new NotificationTableModel();
  private final JTable notificationsTable = new JTable(tableModel);
  private final JTextArea logArea = new JTextArea(5, 20);

  private static final DateTimeFormatter FIRE_AT_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  public AppWindow(DataBaseWrapper db, Clock clock) {
    super("Notification Client");
    this.db = db;
    this.clock = clock;

    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(980, 680));

    JPanel root = new JPanel(new BorderLayout(12, 12));
    root.setBorder(new EmptyBorder(10, 10, 10, 10));
    setContentPane(root);

    root.add(buildAuthPanel(), BorderLayout.NORTH);
    root.add(buildMainContent(), BorderLayout.CENTER);
    root.add(buildLogPanel(), BorderLayout.SOUTH);

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            shutdown();
          }

          @Override
          public void windowClosed(WindowEvent e) {
            shutdown();
          }
        });

    pack();
    setLocationRelativeTo(null);
  }

  private JPanel buildAuthPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Connection and login"));

    JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
    row1.add(new JLabel("Host:"));
    row1.add(hostField);
    row1.add(new JLabel("Port:"));
    portSpinner.setPreferredSize(new Dimension(90, 26));
    row1.add(portSpinner);
    JButton applyEndpoint = new JButton("Apply endpoint");
    applyEndpoint.addActionListener(e -> applyEndpoint());
    row1.add(applyEndpoint);
    row1.add(Box.createHorizontalStrut(12));
    row1.add(clockStatusLabel);

    JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
    row2.add(new JLabel("Username:"));
    row2.add(usernameField);
    row2.add(new JLabel("Password:"));
    passwordField.setPreferredSize(new Dimension(120, 26));
    row2.add(passwordField);
    row2.add(registerCheckBox);
    JButton loginButton = new JButton("Login");
    loginButton.addActionListener(e -> handleAuth());
    row2.add(loginButton);
    JButton adminCheckButton = new JButton("Check admin");
    adminCheckButton.addActionListener(e -> handleAdminCheck());
    row2.add(adminCheckButton);
    row2.add(Box.createHorizontalStrut(10));
    row2.add(authStatusLabel);
    row2.add(Box.createHorizontalStrut(10));
    row2.add(adminStatusLabel);

    panel.add(row1);
    panel.add(row2);
    return panel;
  }

  private JSplitPane buildMainContent() {
    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    splitPane.setTopComponent(buildNotificationsPane());
    splitPane.setBottomComponent(buildAdminPane());
    splitPane.setResizeWeight(0.65);
    return splitPane;
  }

  private JPanel buildNotificationsPane() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.setBorder(BorderFactory.createTitledBorder("Notifications"));

    JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
    form.add(new JLabel("Title:"));
    titleField.setPreferredSize(new Dimension(180, 26));
    form.add(titleField);
    form.add(new JLabel("Payload:"));
    payloadField.setPreferredSize(new Dimension(220, 26));
    form.add(payloadField);
    form.add(new JLabel("Delay (sec):"));
    delaySpinner.setPreferredSize(new Dimension(90, 26));
    form.add(delaySpinner);
    form.add(sendToWebCheckBox);
    JButton addButton = new JButton("Add notification");
    addButton.addActionListener(e -> handleAddNotification());
    form.add(addButton);

    JPanel tableWrapper = new JPanel(new BorderLayout());
    notificationsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    notificationsTable.setRowHeight(24);
    notificationsTable.getTableHeader().setReorderingAllowed(false);
    tableWrapper.add(new JScrollPane(notificationsTable), BorderLayout.CENTER);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JButton refreshButton = new JButton("Refresh remote");
    refreshButton.addActionListener(e -> refreshRemoteNotifications());
    JButton deleteButton = new JButton("Delete selected");
    deleteButton.addActionListener(e -> handleDeleteSelected());
    actions.add(refreshButton);
    actions.add(deleteButton);
    actions.add(adminDeleteCheckBox);

    panel.add(form, BorderLayout.NORTH);
    panel.add(tableWrapper, BorderLayout.CENTER);
    panel.add(actions, BorderLayout.SOUTH);
    return panel;
  }

  private JPanel buildAdminPane() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Admin actions"));

    JPanel addUserRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    addUserRow.add(new JLabel("Username:"));
    adminUserField.setPreferredSize(new Dimension(140, 26));
    addUserRow.add(adminUserField);
    addUserRow.add(new JLabel("Password:"));
    adminUserPasswordField.setPreferredSize(new Dimension(140, 26));
    addUserRow.add(adminUserPasswordField);
    addUserRow.add(adminGrantCheckBox);
    JButton createUserButton = new JButton("Save user");
    createUserButton.addActionListener(e -> handleCreateUser());
    addUserRow.add(createUserButton);

    JPanel deleteRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    deleteRow.add(new JLabel("Delete users (comma):"));
    deleteUsersField.setPreferredSize(new Dimension(260, 26));
    deleteRow.add(deleteUsersField);
    JButton deleteUsersButton = new JButton("Delete");
    deleteUsersButton.addActionListener(e -> handleDeleteUsers());
    deleteRow.add(deleteUsersButton);

    panel.add(addUserRow);
    panel.add(deleteRow);
    return panel;
  }

  private JPanel buildLogPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Status"));

    logArea.setEditable(false);
    logArea.setLineWrap(true);
    logArea.setWrapStyleWord(true);

    panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
    return panel;
  }

  private void applyEndpoint() {
    String host = hostField.getText().trim();
    int port = ((Number) portSpinner.getValue()).intValue();
    Client.configureEndpoint(host, port);
    appendLog("Endpoint set to " + host + ":" + port);
  }

  private void handleAuth() {
    String host = hostField.getText().trim();
    int port = ((Number) portSpinner.getValue()).intValue();
    String username = usernameField.getText().trim();
    String password = new String(passwordField.getPassword());

    if (username.isBlank() || password.isBlank()) {
      showError("Username and password cannot be empty.");
      return;
    }

    runAsync(
        "auth",
        () -> {
          try {
            Client.configureEndpoint(host, port);
            Client.setCredentials(username, password);
            boolean ok =
                registerCheckBox.isSelected()
                    ? Client.sendAuth(new String[] {username, password}) == 1
                    : Client.validateCredentials();
            if (ok) {
              logInfo("Authenticated as " + username);
              updateAuthState("Authenticated", true);
              startClockIfNeeded();
              refreshRemoteNotifications();
              handleAdminCheck();
            } else {
              updateAuthState("Auth failed", false);
              logInfo("Authentication failed");
            }
          } catch (Exception ex) {
            logInfo("Authentication error: " + ex.getMessage());
            updateAuthState("Auth error", false);
          }
        });
  }

  private void handleAddNotification() {
    String title = titleField.getText().trim();
    String payload = payloadField.getText().trim();
    long delaySeconds = ((Number) delaySpinner.getValue()).longValue();
    boolean sendToWeb = sendToWebCheckBox.isSelected();

    if (title.isBlank()) {
      showError("Title is required to create a notification.");
      return;
    }

    runAsync(
        "add notification",
        () -> {
          long fireAt = (System.currentTimeMillis() / 1000L) + delaySeconds;
          NotificationInfo info =
              new NotificationInfo(0, 0, title, payload.isBlank() ? null : payload, fireAt);
          if (sendToWeb) {
            List<String> statuses = Client.sendNotification(info);
            if (statuses.isEmpty()) {
              showError("Failed to upload notification to server.");
              return;
            }
            logInfo("Uploaded to server: " + statuses);
          }
          db.addNotification(info);
          logInfo(
              "Saved notification locally. Fires at "
                  + FIRE_AT_FORMATTER.format(Instant.ofEpochMilli(toMillis(info.getFireAt()))));
          titleField.setText("");
          payloadField.setText("");
          delaySpinner.setValue(30);
        });
  }

  private void refreshRemoteNotifications() {
    runAsync(
        "refresh",
        () -> {
          try {
            List<NotificationInfo> remote = Client.fetchNotifications();
            SwingUtilities.invokeLater(() -> tableModel.setNotifications(remote));
            logInfo("Fetched " + remote.size() + " remote notifications.");
          } catch (Exception ex) {
            logInfo("Failed to fetch remote notifications: " + ex.getMessage());
          }
        });
  }

  private void handleDeleteSelected() {
    int[] rows = notificationsTable.getSelectedRows();
    if (rows.length == 0) {
      showError("Select at least one remote notification to delete.");
      return;
    }

    List<Integer> ids = new ArrayList<>();
    for (int row : rows) {
      NotificationInfo info = tableModel.getAt(notificationsTable.convertRowIndexToModel(row));
      if (info != null && info.getWebId() > 0) {
        ids.add(info.getWebId());
      }
    }
    if (ids.isEmpty()) {
      showError("No valid remote notification ids selected.");
      return;
    }

    boolean useAdmin = adminDeleteCheckBox.isSelected();
    runAsync(
        "delete",
        () -> {
          boolean deleted = Client.deleteNotifications(ids, useAdmin);
          if (deleted) {
            logInfo(
                "Deleted remote notifications " + ids + (useAdmin ? " via admin endpoint." : "."));
            refreshRemoteNotifications();
          } else {
            showError("Failed to delete selected notifications.");
          }
        });
  }

  private void handleAdminCheck() {
    runAsync(
        "admin-check",
        () -> {
          try {
            Boolean status = Client.fetchAdminStatus();
            if (status == null) {
              updateAdminLabel("Admin: unknown");
            } else {
              updateAdminLabel("Admin: " + (status ? "yes" : "no"));
            }
          } catch (Exception ex) {
            logInfo("Admin check failed: " + ex.getMessage());
            updateAdminLabel("Admin: unknown");
          }
        });
  }

  private void handleCreateUser() {
    String username = adminUserField.getText().trim();
    String password = new String(adminUserPasswordField.getPassword());
    boolean isAdmin = adminGrantCheckBox.isSelected();

    if (username.isBlank() || password.isBlank()) {
      showError("Provide username and password for the user.");
      return;
    }

    runAsync(
        "admin-create",
        () -> {
          boolean success = Client.registerUserAsSuperuser(username, password, isAdmin);
          if (success) {
            logInfo("User " + username + " saved (admin=" + isAdmin + ").");
            adminUserField.setText("");
            adminUserPasswordField.setText("");
          } else {
            showError("Failed to save user " + username + ".");
          }
        });
  }

  private void handleDeleteUsers() {
    String text = deleteUsersField.getText();
    if (text == null || text.isBlank()) {
      showError("Enter at least one username to delete.");
      return;
    }
    String[] tokens = text.split(",");
    List<String> usernames = new ArrayList<>();
    for (String token : tokens) {
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        usernames.add(trimmed);
      }
    }
    if (usernames.isEmpty()) {
      showError("Enter at least one username to delete.");
      return;
    }

    runAsync(
        "admin-delete",
        () -> {
          boolean success = Client.deleteUsers(usernames);
          if (success) {
            logInfo("Deleted users: " + usernames);
            deleteUsersField.setText("");
          } else {
            showError("Failed to delete users " + usernames);
          }
        });
  }

  private void runAsync(String label, Runnable task) {
    worker.submit(
        () -> {
          try {
            task.run();
          } catch (Exception ex) {
            Logger.error("Task '" + label + "' failed: " + ex.getMessage());
            showError("Operation failed: " + ex.getMessage());
          }
        });
  }

  private void startClockIfNeeded() {
    synchronized (this) {
      if (clockThread != null && clockThread.isAlive()) {
        return;
      }
      clockThread =
          new Thread(
              () -> {
                try {
                  clock.NotifyingCylce(db);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              },
              "notification-clock");
      clockThread.setDaemon(true);
      clockThread.start();
      updateClockState("Clock running");
    }
  }

  private void stopClock() {
    synchronized (this) {
      if (clockThread != null) {
        clock.stop();
        clockThread.interrupt();
        clockThread = null;
        updateClockState("Clock stopped");
      }
    }
  }

  private void shutdown() {
    stopClock();
    worker.shutdownNow();
    db.closeDb();
  }

  private void updateAuthState(String text, boolean success) {
    SwingUtilities.invokeLater(
        () -> {
          authStatusLabel.setText(text);
          authStatusLabel.setForeground(
              success ? new java.awt.Color(0, 128, 0) : new java.awt.Color(180, 0, 0));
        });
  }

  private void updateAdminLabel(String text) {
    SwingUtilities.invokeLater(() -> adminStatusLabel.setText(text));
  }

  private void updateClockState(String text) {
    SwingUtilities.invokeLater(() -> clockStatusLabel.setText(text));
  }

  private void logInfo(String message) {
    appendLog(message);
  }

  private void appendLog(String message) {
    SwingUtilities.invokeLater(
        () -> {
          String existing = logArea.getText();
          String prefix = java.time.LocalTime.now().withNano(0).toString();
          String next = prefix + " - " + message + "\n";
          if (existing.isBlank()) {
            logArea.setText(next);
          } else {
            logArea.append(next);
          }
          logArea.setCaretPosition(logArea.getDocument().getLength());
        });
  }

  private void showError(String message) {
    appendLog("Error: " + message);
    SwingUtilities.invokeLater(
        () -> JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE));
  }

  private long toMillis(long fireAt) {
    return fireAt < 1_000_000_000_000L ? fireAt * 1000L : fireAt;
  }

  public static void launch(DataBaseWrapper db, Clock clock) {
    SwingUtilities.invokeLater(() -> new AppWindow(db, clock).setVisible(true));
  }

  private static final class NotificationTableModel extends AbstractTableModel {
    private final List<NotificationInfo> notifications = new ArrayList<>();
    private final String[] columns = {"webId", "Title", "Payload", "Fire at (local)"};

    public void setNotifications(List<NotificationInfo> items) {
      notifications.clear();
      notifications.addAll(items == null ? Collections.emptyList() : items);
      fireTableDataChanged();
    }

    public NotificationInfo getAt(int row) {
      if (row < 0 || row >= notifications.size()) {
        return null;
      }
      return notifications.get(row);
    }

    @Override
    public int getRowCount() {
      return notifications.size();
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int column) {
      return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      NotificationInfo info = notifications.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> info.getWebId();
        case 1 -> info.getTitle();
        case 2 -> Objects.toString(info.getPayload(), "");
        case 3 -> FIRE_AT_FORMATTER.format(Instant.ofEpochMilli(normalize(info.getFireAt())));
        default -> "";
      };
    }

    private long normalize(long fireAt) {
      return fireAt < 1_000_000_000_000L ? fireAt * 1000L : fireAt;
    }
  }
}
