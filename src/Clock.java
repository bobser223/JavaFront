import structures.NotificationInfo;

import java.sql.*;
import java.util.*;


public class Clock {
    PriorityQueue<NotificationInfo> notifications;
    int sampleSizeToHold = 10;
    int sampleSizeToLoad = 10;
    boolean isRunning = true;
    int millisecondsToSleep = 500;
    int delta = 50;

    public Clock() {}

    public static boolean inDeltaNeighbourhood(long time, long delta){
        return Math.abs(time - System.currentTimeMillis()) < delta;
    }

    public void Notify(NotificationInfo info){
        System.out.println("Notifying: " + info.toString());
    }

    public boolean checkFirstNotification(){
        if (notifications.isEmpty()) return false;
        return inDeltaNeighbourhood(notifications.peek().getFireAt(), delta);

    }

    public void NotifyingCylce(DataBaseWrapper db) throws InterruptedException {
        while (isRunning){

            if (!notifications.isEmpty()) {
                if (db.thereIsAEarlierNotification(notifications.peek().getFireAt()))
                    addNotificationsFromDB(db);

            } else {
                System.out.println("[warning] [Clock] No notifications left in queue.");
                addNotificationsFromDB(db);
            }



            if (checkFirstNotification())
                Notify(Objects.requireNonNull(notifications.poll()));




            Thread.sleep(millisecondsToSleep);
        }
    }

    public void addNotificationsFromDB(DataBaseWrapper db){
        ArrayList<NotificationInfo> newNotifications = db.getEarliestNotifications(sampleSizeToLoad);
        List<NotificationInfo> notificationList = new ArrayList<>(notifications); // it's only necessary for deleting the last element

        for (NotificationInfo n : newNotifications) {
            if (notificationList.contains(n)) continue;

            if (notifications.size() >= sampleSizeToHold) {
                notificationList.removeLast();
            }

            notificationList.add(n);
        }


        notifications = new PriorityQueue<>(notificationList);
    }




}
