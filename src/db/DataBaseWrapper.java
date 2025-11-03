package db;

import logger.Logger;
import structures.NotificationInfo;

import java.sql.*;
import java.util.ArrayList;


public class DataBaseWrapper {

    String url = "jdbc:sqlite:sample.db";
    private Connection conn;

    public static void main(String[] args){
        DataBaseWrapper db = new DataBaseWrapper();
        db.makeDb();

//        db.closeDb();
    }

    public DataBaseWrapper() {
        connect();
    }

    public DataBaseWrapper(String url_) {
        this.url = url_;
        connect();
    }

    private void connect() {
        try {
            conn = DriverManager.getConnection(url);
            System.out.println("Connected to SQLite database.");
        } catch (SQLException e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    public void makeDb(){

        String createAlarmsTable = """
        create table if not exists notifications(
            id integer primary key autoincrement,
            webId integer not null,
            title text not null,
            payload text,
            fire_at integer not null  -- epoch seconds (UTC)
        );
        """;






        assert conn != null;


        try(Statement stmt = conn.createStatement()){

            stmt.execute(createAlarmsTable);
            Logger.info("Created database table.");

        } catch (SQLException e) {
            Logger.error("Failed to create database table: " + e.getMessage());
        }
    }

    public void closeDb(){
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                System.out.println("Closed database connection.");
            }
        } catch (SQLException e) {
            System.out.println("Failed to close database connection: " + e.getMessage());
        }
    }

    public ArrayList<NotificationInfo> getEarliestNotifications(int sampleSize){
        ArrayList<NotificationInfo> sample = new ArrayList<>();

        String query = "SELECT * FROM notifications ORDER BY fire_at LIMIT " + sampleSize;

        assert conn != null;
        try (Statement stmt = conn.createStatement()){
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                sample.add(mapNotification(rs));
            }

        } catch (SQLException e) {
            System.out.println("Statement creation failed " + e.getMessage());
        }
        return sample;
    }

    private NotificationInfo mapNotification(ResultSet rs) throws SQLException {
        return new NotificationInfo(rs.getInt("id"),
                rs.getInt("webId"),
                rs.getString("title"),
                rs.getString("payload"),
                rs.getLong("fire_at"));
    }

    public boolean thereIsAEarlierNotification(long fireAt){

        // earliest notification in the db
        ArrayList<NotificationInfo> earliest = getEarliestNotifications(1);
        if (earliest.isEmpty()) {
            return false;
        }
        NotificationInfo n = earliest.get(0);

        return n.getFireAt() < fireAt;
    }

    public void deleteNotification(int id){
        Logger.info("Deleting notification from db: " + id);
        String query = "DELETE FROM notifications WHERE id = " + id;
        assert conn != null;
        try (Statement stmt = conn.createStatement()){
            stmt.executeUpdate(query);
        } catch (SQLException e) {
            Logger.error("Failed to delete notification with id " + id + ": " + e.getMessage());
        }
    }

    public void addNotification(NotificationInfo n){
        Logger.info("Adding notification to db: " + n.toString());
        String sql = "INSERT INTO notifications (webId, title, payload, fire_at) VALUES (?, ?, ?, ?)";
        assert conn != null;
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, n.getWebId());
            pstmt.setString(2, n.getTitle());
            if (n.getPayload() == null) {
                pstmt.setNull(3, Types.VARCHAR);
            } else {
                pstmt.setString(3, n.getPayload());
            }
            pstmt.setLong(4, n.getFireAt());
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    n.setId(generatedKeys.getInt(1));
                }
            }
            Logger.info("Notification added to db: " + n.toString());
        } catch (SQLException e) {
            Logger.error("Failed to add notification: " + e.getMessage());
        }
    }

    public NotificationInfo getNotificationByWebId(int webId) {
        if (webId <= 0) {
            return null;
        }
        String query = "SELECT * FROM notifications WHERE webId = ?";
        assert conn != null;
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, webId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapNotification(rs);
                }
            }
        } catch (SQLException e) {
            Logger.error("Failed to fetch notification with webId " + webId + ": " + e.getMessage());
        }
        return null;
    }

    public NotificationInfo upsertNotificationByWebId(NotificationInfo info) {
        if (info == null || info.getWebId() <= 0) {
            Logger.warn("Skipping upsert for notification without webId.");
            return null;
        }

        NotificationInfo existing = getNotificationByWebId(info.getWebId());
        if (existing == null) {
            addNotification(info);
            return info;
        }

        String sql = "UPDATE notifications SET title = ?, payload = ?, fire_at = ? WHERE webId = ?";
        assert conn != null;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, info.getTitle());
            if (info.getPayload() == null) {
                pstmt.setNull(2, Types.VARCHAR);
            } else {
                pstmt.setString(2, info.getPayload());
            }
            pstmt.setLong(3, info.getFireAt());
            pstmt.setInt(4, info.getWebId());
            pstmt.executeUpdate();
            existing.setTitle(info.getTitle());
            existing.setPayload(info.getPayload());
            existing.setFireAt(info.getFireAt());
            Logger.info("Notification updated in db: " + existing.toString());
        } catch (SQLException e) {
            Logger.error("Failed to update notification with webId " + info.getWebId() + ": " + e.getMessage());
        }
        return existing;
    }
}
