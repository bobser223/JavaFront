import db.DataBaseWrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import logger.Logger;
import structures.NotificationInfo;
import web.Client;

// TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

  String name, password;

  public static boolean yesNo2Bool(String answer) {
    if (answer == null) {
      return false;
    }
    String normalized = answer.trim().toLowerCase();
    return normalized.equals("y") || normalized.equals("yes");
  }

  public int handleRegistration() {
    Logger.info("Registration started");

    Scanner scanner = new Scanner(System.in);
    String[] auth = new String[2];

    System.out.println("Registration block");

    System.out.println("Do you exist? <yes/no> || <y/n>");
    Boolean existance = yesNo2Bool(scanner.nextLine());
    Logger.info("Registration with existance: " + existance);

    System.out.println("enter name: ");
    auth[0] = scanner.nextLine();
    Logger.info("Registration with name: " + auth[0]);
    System.out.println("enter password: ");
    auth[1] = scanner.nextLine();
    Logger.info("Registration with password: " + auth[1]);
    Logger.info("Registration finished");

    this.name = auth[0];
    this.password = auth[1];

    Client.setCredentials(this.name, this.password);

    int status;
    if (!existance) {
      status = Client.sendAuth(auth);
    } else {
      boolean valid = Client.validateCredentials();
      status = valid ? 1 : 0;
      if (!valid) {
        Logger.warn("Provided credentials were rejected by the web service.");
      }
    }
    Logger.info("Auth status: " + status);

    return status;
  }

  public int handleAddNotification(DataBaseWrapper db) {
    String title, payload;
    long delaySeconds;
    Boolean sendToWeb = false;

    Scanner scanner = new Scanner(System.in);
    System.out.println("Enter title: ");
    title = scanner.nextLine();
    Logger.info("Adding notification with title " + title);
    System.out.println("Enter payload: ");
    payload = scanner.nextLine();
    Logger.info("Adding notification with payload " + payload);
    System.out.println("Enter delay in seconds from now: ");
    delaySeconds = scanner.nextLong();
    Logger.info("Adding notification with delay " + delaySeconds + " seconds");
    scanner.nextLine(); // consume leftover newline
    long fireAt = (System.currentTimeMillis() / 1000L) + delaySeconds;
    Logger.info("Calculated fire_at timestamp " + fireAt);
    System.out.println("Do you want to send to web? <yes/no> || <y/n>");
    sendToWeb = yesNo2Bool(scanner.nextLine());
    Logger.info("Adding notification with sendToWeb " + sendToWeb);

    NotificationInfo n = new NotificationInfo(0, 0, title, payload, fireAt);
    if (sendToWeb) {
      List<String> statuses = Client.sendNotification(n);
      if (statuses.isEmpty()) {
        Logger.warn("Failed to send notification to web service.");
        return -1;
      }
      Logger.info("Web service response statuses: " + statuses);
      if (n.getWebId() > 0) {
        Logger.info("Sent notification to web service with id " + n.getWebId());
      } else {
        Logger.warn("Notification uploaded but server did not provide a webId.");
      }
    }
    db.addNotification(n);

    return 0;
  }

  public void handleDeleteNotifications() {
    Scanner scanner = new Scanner(System.in);
    System.out.println("Enter remote notification ids to delete (comma separated): ");
    String line = scanner.nextLine();

    if (line == null || line.isBlank()) {
      Logger.warn("No ids entered for deletion.");
      return;
    }

    String[] tokens = line.split(",");
    List<Integer> ids = new ArrayList<>();
    for (String token : tokens) {
      try {
        ids.add(Integer.parseInt(token.trim()));
      } catch (NumberFormatException e) {
        Logger.warn("Skipping invalid notification id: " + token);
      }
    }

    if (ids.isEmpty()) {
      Logger.warn("No valid ids provided for deletion.");
      return;
    }

    Boolean adminStatus;
    try {
      adminStatus = Client.fetchAdminStatus();
    } catch (IllegalStateException e) {
      Logger.warn("Cannot verify admin status for deletion: " + e.getMessage());
      System.out.println("Failed to verify credentials. Please re-authenticate.");
      return;
    }

    boolean useAdminEndpoint = Boolean.TRUE.equals(adminStatus);
    if (adminStatus == null) {
      Logger.warn("Unable to verify administrative privileges; using standard deletion endpoint.");
      System.out.println(
          "Could not verify administrative privileges; attempting regular deletion.");
    }

    if (Client.deleteNotifications(ids, useAdminEndpoint)) {
      Logger.info("Requested remote deletion for notifications " + ids);
    } else {
      Logger.warn("Failed to delete notifications " + ids + " on remote server");
    }
  }

  private boolean requireSuperuser() {
    Boolean adminStatus;
    try {
      adminStatus = Client.fetchAdminStatus();
    } catch (IllegalStateException e) {
      Logger.warn("Cannot verify admin status: " + e.getMessage());
      System.out.println("Failed to verify credentials. Please re-authenticate.");
      return false;
    }

    if (adminStatus == null) {
      Logger.warn("Unable to verify administrative privileges from server.");
      System.out.println("Could not verify administrative privileges. Please try again later.");
      return false;
    }

    if (!adminStatus) {
      Logger.warn("Attempted to perform admin-only operation without privileges.");
      System.out.println("This command is available only to administrators.");
      return false;
    }
    return true;
  }

  public void handleAddUserAsSuperuser() {
    if (!requireSuperuser()) {
      return;
    }

    Scanner scanner = new Scanner(System.in);
    System.out.println("Enter username to create/update: ");
    String targetUsername = scanner.nextLine().trim();

    if (targetUsername.isEmpty()) {
      Logger.warn("No username provided for admin user creation.");
      System.out.println("Username cannot be empty.");
      return;
    }

    System.out.println("Enter password for the user: ");
    String targetPassword = scanner.nextLine();

    System.out.println("Should this user be an administrator? <yes/no> || <y/n>");
    boolean makeAdmin = yesNo2Bool(scanner.nextLine());

    boolean success = Client.registerUserAsSuperuser(targetUsername, targetPassword, makeAdmin);
    if (success) {
      Logger.info("Superuser created or updated user " + targetUsername);
      System.out.println("User " + targetUsername + " created/updated successfully.");
    } else {
      Logger.warn("Failed to create or update user " + targetUsername);
      System.out.println("Failed to create/update user " + targetUsername + ".");
    }
  }

  public void handleDeleteUsersAsSuperuser() {
    if (!requireSuperuser()) {
      return;
    }

    Scanner scanner = new Scanner(System.in);
    System.out.println("Enter usernames to delete (comma separated): ");
    String line = scanner.nextLine();

    if (line == null || line.isBlank()) {
      Logger.warn("No usernames entered for deletion.");
      return;
    }

    String[] tokens = line.split(",");
    List<String> usernames = new ArrayList<>();
    for (String token : tokens) {
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        usernames.add(trimmed);
      }
    }

    if (usernames.isEmpty()) {
      Logger.warn("No valid usernames provided for deletion.");
      return;
    }

    boolean success = Client.deleteUsers(usernames);
    if (success) {
      Logger.info("Superuser deleted users " + usernames);
      System.out.println("Users " + usernames + " deleted successfully.");
    } else {
      Logger.warn("Failed to delete users " + usernames);
      System.out.println("Failed to delete users " + usernames + ".");
    }
  }

  public void showRemoteNotifications() {
    Logger.info("Fetching notifications from remote server");
    for (NotificationInfo info : Client.fetchNotifications()) {
      System.out.println(info);
    }
  }

  public void runCli(DataBaseWrapper db, Clock clock) {
    System.out.println("Starting CLI mode. Pass --cli to force CLI, omit for Swing UI.");

    int status = 0;
    while (status != 1) {
      status = handleRegistration();
    }

    Thread clockThread =
        new Thread(
            () -> {
              try {
                clock.NotifyingCylce(db);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.warn("Notification clock interrupted");
              }
            },
            "notification-clock");
    clockThread.setDaemon(true);
    clockThread.start();

    label:
    while (true) {
      System.out.println("Enter command: ");
      Scanner scanner = new Scanner(System.in);
      String input = scanner.nextLine().toLowerCase().replaceAll("\\s", "");
      ;

      switch (input) {
        case "exit":
          Logger.info("Exiting...");
          clock.stop();
          clockThread.interrupt();
          break label;
        case "help":
          System.out.println("exit - to exit");
          System.out.println("help - to get help");
          System.out.println("add notifications - create a notification and push it to the server");
          System.out.println("show notifications - print remote notifications");
          System.out.println("delete notifications - remove notifications from remote server");
          System.out.println("add user - create or update a user via admin API");
          System.out.println("delete users - remove users via admin API");
          break;
        case "add notifications", "an":
          Logger.info("Adding notifications...");
          handleAddNotification(db);
          break;
        case "show notifications", "sn":
          showRemoteNotifications();
          break;
        case "delete notifications", "dn":
          Logger.info("Deleting notifications...");
          handleDeleteNotifications();
          break;
        case "add user", "au":
          Logger.info("Admin requested to add user...");
          handleAddUserAsSuperuser();
          break;
        case "delete users", "du":
          Logger.info("Admin requested to delete users...");
          handleDeleteUsersAsSuperuser();
          break;

        default:
          Logger.warn("Unknown command");
          System.out.println("Unknown command");
          break;
      }
    }
    clock.stop();
    try {
      clockThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    db.closeDb();
  }

  public static void main(String[] args) {
    boolean useCli =
        args != null && Arrays.stream(args).anyMatch(arg -> "--cli".equalsIgnoreCase(arg));

    DataBaseWrapper db = new DataBaseWrapper();
    db.makeDb();
    Clock clock = new Clock();
    Main app = new Main();

    if (useCli) {
      app.runCli(db, clock);
      return;
    }

    AppWindow.launch(db, clock);
  }
}
