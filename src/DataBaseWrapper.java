import structures.NotificationInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;


public class DataBaseWrapper {

    String url = "jdbc:sqlite:sample.db";
    private Connection conn;

    public static void main(String[] args){
        DataBaseWrapper db = new DataBaseWrapper();
        db.makeDb();

        db.closeDb();
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
        create table if not exists alarms(
            id integer primary key autoincrement,
            title text not null,
            payload text,
            fire_at integer not null  -- epoch seconds (UTC)
        );
        """;






        assert conn != null;


        try(Statement stmt = conn.createStatement()){

            stmt.execute(createAlarmsTable);

        } catch (SQLException e) {
            System.out.println("Statement creation failed: " + e.getMessage());
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

        String query = "SELECT * FROM alarms ORDER BY fire_at LIMIT " + sampleSize;

        assert conn != null;
        try (Statement stmt = conn.createStatement()){
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                sample.add(new NotificationInfo(rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("payload"),
                        rs.getLong("fire_at")));
            }

        } catch (SQLException e) {
            System.out.println("Statement creation failed " + e.getMessage());
        }
        return sample;
    }

    boolean thereIsAEarlierNotification(long fireAt){

        // earliest notification in the db
        NotificationInfo n = getEarliestNotifications(1).get(0);

        return n.getFireAt() < fireAt;
    }
}
