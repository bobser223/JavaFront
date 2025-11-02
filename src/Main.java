import db.DataBaseWrapper;
import logger.Logger;
import structures.NotificationInfo;
import web.Client;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    String name, password;

    public static boolean yesNo2Bool(String answer){
        if (answer == null) {
            return false;
        }
        String normalized = answer.trim().toLowerCase();
        return normalized.equals("y") || normalized.equals("yes");
    }


    public int handleRegistration(){
        Logger.info("Registration started");

        Scanner scanner = new Scanner(System.in);
        String[] auth = new String[2];

        System.out.println("Registration block");

        System.out.println("Do you exist? <yes/no> || <y/n>"); Boolean existance = yesNo2Bool(scanner.nextLine());
        Logger.info("Registration with existance: " + existance);

        System.out.println("enter name: "); auth[0] = scanner.nextLine();
        Logger.info("Registration with name: " + auth[0]);
        System.out.println("enter password: "); auth[1] = scanner.nextLine();
        Logger.info("Registration with password: " + auth[1]);
        Logger.info("Registration finished");

        this.name = auth[0];
        this.password = auth[1];

        Client.setCredentials(this.name, this.password);

        int status;
        if (!existance) {
            status = Client.sendAuth(auth);
        } else {
//            boolean valid = Client.validateCredentials();
//            status = valid ? 1 : 0;
//            if (!valid) {
//                Logger.warn("Provided credentials were rejected by the web service.");
//            }
            status = 1;
        }
        Logger.info("Auth status: " + status);

        return status;
    }

    public int handleAddNotification(DataBaseWrapper db){
        String title, payload;
        long fireAt;
        Boolean sendToWeb = false;

        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter title: "); title = scanner.nextLine();
        Logger.info("Adding notification with title " + title);
        System.out.println("Enter payload: "); payload = scanner.nextLine();
        Logger.info("Adding notification with payload " + payload);
        System.out.println("Enter fire_at (epoch seconds): "); fireAt = scanner.nextLong();
        Logger.info("Adding notification with fire_at " + fireAt);
        scanner.nextLine(); // consume leftover newline
        System.out.println("Do you want to send to web? <yes/no> || <y/n>"); sendToWeb = yesNo2Bool(scanner.nextLine());
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

        if (Client.deleteNotifications(ids, false)) {
            Logger.info("Requested remote deletion for notifications " + ids);
        } else {
            Logger.warn("Failed to delete notifications " + ids + " on remote server");
        }
    }

    public void showRemoteNotifications() {
        Logger.info("Fetching notifications from remote server");
        for (NotificationInfo info : Client.fetchNotifications()) {
            System.out.println(info);
        }
    }

    public static void main(String[] args) {

        DataBaseWrapper db = new DataBaseWrapper();
        Main app = new Main();

        int status = 0;
        while(status != 1){
            status = app.handleRegistration();
        }

        label:
        while (true){
            System.out.println("Enter command: ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();

            switch (input) {
                case "exit":
                    Logger.info("Exiting...");
                    break label;
                case "help":
                    System.out.println("exit - to exit");
                    System.out.println("help - to get help");
                    System.out.println("add notifications - create a notification and push it to the server");
                    System.out.println("show notifications - print remote notifications");
                    System.out.println("delete notifications - remove notifications from remote server");
                    break;
                case "add notifications", "an":
                    Logger.info("Adding notifications...");
                    app.handleAddNotification(db);
                    break;
                case "show notifications", "sn":
                    app.showRemoteNotifications();
                    break;
                case "delete notifications", "dn":
                    Logger.info("Deleting notifications...");
                    app.handleDeleteNotifications();
                    break;


                default:
                    Logger.warn("Unknown command");
                    System.out.println("Unknown command");
                    break;
            }



        }
    }
}
