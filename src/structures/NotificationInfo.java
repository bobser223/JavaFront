package structures;

public class NotificationInfo {
    private int id;
    private String title;
    private String payload;
    private long fireAt;

    public NotificationInfo(int id, String title, String payload, long fireAt) {
        this.id = id;
        this.title = title;
        this.payload = payload;
        this.fireAt = fireAt;
    }

    public int getId() {
        return id;
    }
    public String getTitle() {
        return title;
    }
    public String getPayload() {
        return payload;
    }
    public long getFireAt() {
        return fireAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }
    public void setFireAt(long fireAt) {
        this.fireAt = fireAt;
    }

    public String toString(){
        return "NotificationInfo {id=" + id + " | title=" + title + " | payload=" + payload + " | fire_at=" + fireAt+"}";
    };


}
