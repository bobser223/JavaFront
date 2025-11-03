import db.DataBaseWrapper;
import logger.Logger;
import structures.NotificationInfo;
import web.Client;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;


public class Clock {
    private final PriorityQueue<NotificationInfo> notifications =
            new PriorityQueue<>(Comparator.comparingLong(NotificationInfo::getFireAt));
    private final Set<Integer> knownNotificationIds = new HashSet<>();
    private final int sampleSizeToLoad = 10;
    private volatile boolean isRunning = true;
    private final int millisecondsToSleep = 500;
    private final long deltaMillis = 1_000;
    private final long remoteSyncIntervalMillis = 30_000;
    private final int minQueueSizeBeforeRemoteSync = 3;
    private long lastRemoteSyncMillis = 0;

    public Clock() {}

    public void Notify(NotificationInfo info){
        System.out.println("Notifying: " + info.toString());
    }

    public boolean checkFirstNotification(){
        NotificationInfo next = notifications.peek();
        if (next == null) {
            return false;
        }
        long fireAtMillis = normalizeToMillis(next.getFireAt());
        return fireAtMillis <= System.currentTimeMillis() + deltaMillis;
    }

    public void NotifyingCylce(DataBaseWrapper db) throws InterruptedException {
        BigInteger i = new BigInteger("1");
        while (isRunning){
            syncRemoteNotifications(db);
            addNotificationsFromDB(db);
            while (checkFirstNotification()){
                NotificationInfo n = notifications.poll();
                if (n == null) {
                    break;
                }
                knownNotificationIds.remove(n.getId());
                Notify(n);
                Logger.info("Notified: " + n.toString());
                db.deleteNotification(n.getId());
            }

            i = i.add(BigInteger.ONE);
            Thread.sleep(millisecondsToSleep);
        }
    }

    public void addNotificationsFromDB(DataBaseWrapper db){
        var newNotifications = db.getEarliestNotifications(sampleSizeToLoad);
        for (NotificationInfo n : newNotifications) {
            if (knownNotificationIds.add(n.getId())) {
                notifications.offer(n);
            }
        }
    }

    public void stop() {
        isRunning = false;
    }

    private long normalizeToMillis(long fireAt) {
        return fireAt < 1_000_000_000_000L ? fireAt * 1000L : fireAt;
    }

    private void syncRemoteNotifications(DataBaseWrapper db) {
        long now = System.currentTimeMillis();
        boolean queueLow = notifications.size() < minQueueSizeBeforeRemoteSync;
        if (!queueLow && (now - lastRemoteSyncMillis) < remoteSyncIntervalMillis) {
            return;
        }

        lastRemoteSyncMillis = now;
        try {
            var remoteNotifications = Client.fetchNotifications();
            for (NotificationInfo remote : remoteNotifications) {
                NotificationInfo stored = db.upsertNotificationByWebId(remote);
                if (stored == null) {
                    continue;
                }
                if (knownNotificationIds.add(stored.getId())) {
                    notifications.offer(stored);
                } else if (notifications.removeIf(existing -> existing.getId() == stored.getId())) {
                    notifications.offer(stored);
                }
            }
            Logger.info("Remote sync loaded " + remoteNotifications.size() + " notifications.");
        } catch (IllegalStateException e) {
            Logger.warn("Skipping remote sync: " + e.getMessage());
        }
    }
}
