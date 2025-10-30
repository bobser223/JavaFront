import db.DataBaseWrapper;
import logger.Logger;
import structures.NotificationInfo;

import java.math.BigInteger;
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
        BigInteger i = new BigInteger("1");
        while (isRunning){
            Logger.info("Running clock cycle " + i.intValue());

            if (!notifications.isEmpty()) {
                Logger.warn("No notifications left in queue.");
                if (db.thereIsAEarlierNotification(notifications.peek().getFireAt())){
                    Logger.warn("There is an earlier notification in the db.");
                    addNotificationsFromDB(db);
                }


            } else {
                Logger.warn("No notifications left in queue.");
                addNotificationsFromDB(db);
            }



            if (checkFirstNotification()){
                Logger.info("First notification is due.");
                var n = Objects.requireNonNull(notifications.poll());
                Notify(Objects.requireNonNull(n));
                Logger.info("Notified: " + n.toString());


                db.deleteNotification(n.getId());

            }


            i = i.add(BigInteger.ONE);

            Logger.info("Clock cycle " + i.intValue() + " finished.");
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
