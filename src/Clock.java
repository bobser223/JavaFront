import db.DataBaseWrapper;
import logger.Logger;
import structures.NotificationInfo;
import ui.NotificationPopup;
import web.Client;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
    private Boolean cachedAdminStatus = null;

    public Clock() {}

    public void Notify(NotificationInfo info){
        System.out.println("Notifying: " + info.toString());
        NotificationPopup.show(info);
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
                deleteRemoteNotification(n);
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

    private void deleteRemoteNotification(NotificationInfo info) {
        if (info.getWebId() == -1) {
            return;
        }
        int webId = info.getWebId();

        try {
            boolean deleted = Client.deleteNotifications(List.of(webId), false);
            if (!deleted && isAdmin()) {
                deleted = Client.deleteNotifications(List.of(webId), true);
            }
            if (deleted) {
                Logger.info("Deleted remote notification webId=" + webId);
            } else {
                Logger.warn("Failed to delete remote notification webId=" + webId);
            }
        } catch (IllegalStateException e) {
            Logger.warn("Cannot delete remote notification webId=" + webId + ": " + e.getMessage());
        }
    }

    private boolean isAdmin() {
        if (cachedAdminStatus != null) {
            return Boolean.TRUE.equals(cachedAdminStatus);
        }
        try {
            cachedAdminStatus = Client.fetchAdminStatus();
        } catch (IllegalStateException e) {
            Logger.warn("Cannot fetch admin status: " + e.getMessage());
            cachedAdminStatus = Boolean.FALSE;
        }
        return Boolean.TRUE.equals(cachedAdminStatus);
    }
}
