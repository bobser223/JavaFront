package web;

import logger.Logger;
import structures.NotificationInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Фасад HTTP-клієнта для взаємодії з віддаленим сервісом сповіщень.
 * Забезпечує керування обліковими даними, адміністрування користувачів та CRUD-операції
 * для сповіщень через прості HTTP-ендпоїнти з JSON.
 */
public final class Client {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 1488;
    private static final Pattern ADMIN_STATUS_PATTERN =
            Pattern.compile("\"isAdmin\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    private static String host = DEFAULT_HOST;
    private static int port = DEFAULT_PORT;
    private static String username;
    private static String password;

    private Client() {
    }

    /**
     * Перевизначає типовий хост або порт віддаленого сервісу.
     *
     * @param hostOverride новий хост; ігнорується, якщо null або порожній
     * @param portOverride новий порт; ігнорується, якщо значення непозитивне
     */
    public static void configureEndpoint(String hostOverride, int portOverride) {
        if (hostOverride != null && !hostOverride.isBlank()) {
            host = hostOverride.trim();
        }
        if (portOverride > 0) {
            port = portOverride;
        }
    }

    /**
     * Зберігає облікові дані, що надсилатимуться в подальших автентифікованих запитах.
     *
     * @param username ім'я користувача для basic-auth
     * @param password пароль для basic-auth
     */
    public static void setCredentials(String username, String password) {
        Client.username = username;
        Client.password = password;
    }

    /**
     * Реєструє користувача у віддаленому сервісі та кешує передані облікові дані.
     *
     * @param auth масив із логіном та паролем у позиціях 0 та 1 відповідно
     * @return {@code 1}, якщо реєстрація успішна; {@code 0} інакше
     */
    public static int sendAuth(String[] auth) {
        if (auth == null || auth.length < 2) {
            Logger.error("Auth data must contain username and password");
            return 0;
        }

        setCredentials(auth[0], auth[1]);
        String body = buildUserPayload(auth[0], auth[1], false);
        HttpResponse response = execute("POST", "/users/add/manually", body, false);

        if (response == null) {
            return 0;
        }

        if (response.isSuccessful()) {
            Logger.info("Auth succeeded: " + response.statusCode() + " -> " + response.body());
            return 1;
        }

        Logger.warn("Auth failed: " + response.statusCode() + " -> " + response.body());
        return 0;
    }

    /**
     * Реєструє або оновлює користувача через привілейований суперкористувацький ендпоїнт.
     *
     * @param targetUsername ім'я користувача для створення або оновлення
     * @param targetPassword пароль, який потрібно призначити
     * @param makeAdmin      робить створеного користувача адміністратором, якщо true
     * @return {@code true}, якщо віддалений виклик успішний
     */
    public static boolean registerUserAsSuperuser(String targetUsername, String targetPassword, boolean makeAdmin) {
        ensureCredentials();
        String body = buildUserPayload(targetUsername, targetPassword, makeAdmin);
        HttpResponse response = execute("POST", "/users/add/superuser", body, true);
        boolean success = response != null && response.isSuccessful();

        if (success) {
            Logger.info("Superuser added/updated a user successfully.");
        } else {
            Logger.warn("Superuser failed to add/update a user.");
        }

        return success;
    }

    /**
     * Отримує сповіщення для поточного автентифікованого користувача.
     *
     * @return список сповіщень, розібраний з відповіді сервера; порожній список у разі помилки
     */
    public static List<NotificationInfo> fetchNotifications() {
        ensureCredentials();
        HttpResponse response = execute("GET", "/notifications/get", null, true);

        if (response == null || !response.isSuccessful()) {
            Logger.warn("Failed to fetch notifications: " + (response == null ? "no response" : response.statusCode()));
            return List.of();
        }

        return parseNotifications(response.body());
    }

    /**
     * Перевіряє кешовані облікові дані шляхом автентифікованого запиту.
     *
     * @return {@code true}, якщо сервер приймає облікові дані
     */
    public static boolean validateCredentials() {
        ensureCredentials();
        HttpResponse response = execute("GET", "/notifications/get", null, true);

        if (response == null) {
            Logger.warn("Credential validation failed: no response from server");
            return false;
        }
        if (response.statusCode() == 401) {
            Logger.warn("Credential validation failed: unauthorized");
            return false;
        }

        if (!response.isSuccessful()) {
            Logger.warn("Credential validation failed with status " + response.statusCode());
            return false;
        }

        return true;
    }

    /**
     * Запитує статус адміністратора для поточного користувача.
     *
     * @return {@code Boolean.TRUE}, якщо користувач є адміністратором; {@code Boolean.FALSE}, якщо ні;
     * {@code null}, якщо не вдалося отримати статус
     */
    public static Boolean fetchAdminStatus() {
        ensureCredentials();
        HttpResponse response = execute("GET", "/users/status", null, true);

        if (response == null) {
            Logger.warn("Failed to fetch admin status: no response from server.");
            return null;
        }
        if (response.statusCode() == 401) {
            Logger.warn("Failed to fetch admin status: unauthorized.");
            return null;
        }
        if (!response.isSuccessful()) {
            Logger.warn("Failed to fetch admin status: status " + response.statusCode());
            return null;
        }

        Boolean parsed = parseAdminStatus(response.body());
        if (parsed == null) {
            Logger.warn("Failed to parse admin status from response: " + response.body());
        }
        return parsed;
    }

    /**
     * Надсилає одне сповіщення та повертає список статусів, які повідомив сервер.
     *
     * @param notification сповіщення для відправлення
     * @return список статусів або порожній список у разі невдачі
     */
    public static List<String> sendNotification(NotificationInfo notification) {
        ensureCredentials();
        if (notification == null) {
            Logger.warn("Notification is null, skipping upload");
            return List.of();
        }

        UploadResponse result = uploadNotifications(List.of(notification));
        if (result == null) {
            Logger.warn("Failed to upload notification for " + notification);
            return List.of();
        }

        if (!result.webIds().isEmpty()) {
            int webId = result.webIds().get(0);
            notification.setWebId(webId);
            Logger.info("Uploaded notification successfully with webId=" + webId);
        } else {
            Logger.warn("Uploaded notification but server returned no webIds.");
        }

        if (result.statuses().isEmpty()) {
            Logger.warn("Server returned no statuses for uploaded notification.");
        }

        return result.statuses();
    }

    /**
     * Надсилає пакет сповіщень на віддалений сервіс.
     *
     * @param notifications непорожній список сповіщень для відправлення
     * @return структурований результат із веб-ідентифікаторами та статусами; {@code null} у разі помилки
     */
    public static UploadResponse uploadNotifications(List<NotificationInfo> notifications) {
        ensureCredentials();
        if (notifications == null || notifications.isEmpty()) {
            Logger.warn("No notifications to upload");
            return null;
        }

        String payload = buildNotificationsPayload(notifications);
        HttpResponse response = execute("PUT", "/notifications/put", payload, true);

        if (response == null) {
            Logger.warn("Failed to upload notifications: no response");
            return null;
        }

        if (!response.isSuccessful()) {
            Logger.warn("Failed to upload notifications: " + response.statusCode());
            return null;
        }

        UploadResponse result = parseUploadResponse(response.body());
        if (result == null) {
            Logger.warn("Uploaded notifications but failed to parse server response: " + response.body());
            return null;
        }

        if (result.webIds().isEmpty()) {
            Logger.warn("Uploaded notifications but server returned no webIds: " + response.body());
        } else {
            Logger.info("Uploaded notifications successfully with webIds=" + result.webIds());
        }

        if (result.statuses().isEmpty()) {
            Logger.warn("Uploaded notifications but server returned no statuses: " + response.body());
        }

        return result;
    }

    /**
     * Запитує видалення сповіщень на віддаленому сервісі.
     *
     * @param notificationIds ідентифікатори сповіщень для видалення
     * @param superuser       чи слід використовувати привілейований ендпоїнт видалення
     * @return {@code true}, якщо віддалений виклик успішний
     */
    public static boolean deleteNotifications(List<Integer> notificationIds, boolean superuser) {
        ensureCredentials();
        if (notificationIds == null || notificationIds.isEmpty()) {
            Logger.warn("No notification ids provided for deletion");
            return false;
        }

        String target = superuser ? "/notifications/delete/superuser" : "/notifications/delete/manually";
        String payload = buildDeleteNotificationsPayload(notificationIds);
        HttpResponse response = execute("DELETE", target, payload, true);

        if (response != null && response.isSuccessful()) {
            Logger.info("Deleted notifications successfully.");
            return true;
        }

        Logger.warn("Failed to delete notifications: " + (response == null ? "no response" : response.statusCode()));
        return false;
    }

    /**
     * Запитує видалення користувачів через суперкористувацький ендпоїнт.
     *
     * @param usernames імена користувачів для видалення
     * @return {@code true}, якщо віддалений виклик успішний
     */
    public static boolean deleteUsers(List<String> usernames) {
        ensureCredentials();
        if (usernames == null || usernames.isEmpty()) {
            Logger.warn("No usernames provided for deletion");
            return false;
        }

        String payload = buildDeleteUsersPayload(usernames);
        HttpResponse response = execute("DELETE", "/users/delete/superuser", payload, true);

        if (response != null && response.isSuccessful()) {
            Logger.info("Deleted users successfully.");
            return true;
        }

        Logger.warn("Failed to delete users: " + (response == null ? "no response" : response.statusCode()));
        return false;
    }

    /**
     * Виконує HTTP-запит до налаштованого ендпоїнта.
     *
     * @param method      HTTP-метод
     * @param path        шлях запиту, що починається зі слеша
     * @param body        необов'язковий JSON-пейлоад
     * @param includeAuth чи потрібно додавати заголовок basic-auth
     * @return обгортка відповіді або {@code null}, якщо виклик не вдався
     */
    private static HttpResponse execute(String method, String path, String body, boolean includeAuth) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(method, path, includeAuth);

            if (body != null && !body.isEmpty()) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            String responseBody = readFully(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return new HttpResponse(status, responseBody == null ? "" : responseBody);
        } catch (IOException e) {
            Logger.error("HTTP request failed: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Відкриває та налаштовує {@link HttpURLConnection} для поточного ендпоїнта.
     */
    private static HttpURLConnection openConnection(String method, String path, boolean includeAuth) throws IOException {
        URL url = new URL("http://" + host + ":" + port + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setRequestProperty("Connection", "close");

        if (includeAuth) {
            ensureCredentials();
            String token = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encoded);
        }

        return connection;
    }

    /**
     * Зчитує весь вхідний потік у рядок UTF-8.
     */
    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    /**
     * Переконується, що перед автентифікованим викликом встановлено облікові дані.
     *
     * @throws IllegalStateException якщо облікові дані не задані
     */
    private static void ensureCredentials() {
        if (username == null || password == null) {
            throw new IllegalStateException("Credentials are not set. Call Client.setCredentials first.");
        }
    }

    private static String buildUserPayload(String username, String password, boolean admin) {
        return "{"
                + "\"username\":\"" + escapeJson(username) + "\","
                + "\"password\":\"" + escapeJson(password) + "\","
                + "\"isAdmin\":" + (admin ? "1" : "0")
                + "}";
    }

    private static String buildNotificationsPayload(List<NotificationInfo> notifications) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < notifications.size(); i++) {
            NotificationInfo n = notifications.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            sb.append("\"id\":").append(n.getId()).append(",");
            sb.append("\"title\":\"").append(escapeJson(n.getTitle())).append("\",");
            sb.append("\"payload\":");
            if (n.getPayload() == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escapeJson(n.getPayload())).append("\"");
            }
            sb.append(",");
            sb.append("\"fireAt\":").append(n.getFireAt());
            sb.append("}");
        }

        sb.append("]");
        return sb.toString();
    }

    private static Boolean parseAdminStatus(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        Matcher matcher = ADMIN_STATUS_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            return null;
        }

        return Boolean.parseBoolean(matcher.group(1));
    }

    private static UploadResponse parseUploadResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        int clientId = extractInt(responseBody, "\"clientId\":");
        List<Integer> webIds = extractIntArray(responseBody, "\"webIds\"");
        List<String> statuses = extractStringArray(responseBody, "\"statuses\"");
        if (statuses.isEmpty()) {
            String singleStatus = extractString(responseBody, "\"status\":\"");
            if (!singleStatus.isEmpty()) {
                statuses = List.of(singleStatus);
            }
        }

        return new UploadResponse(clientId, webIds, statuses);
    }

    private static List<Integer> extractIntArray(String source, String key) {
        int start = source.indexOf(key);
        if (start < 0) {
            return List.of();
        }

        int[] bounds = findArrayBounds(source, start + key.length());
        if (bounds == null) {
            return List.of();
        }

        String content = source.substring(bounds[0] + 1, bounds[1]);
        if (content.isBlank()) {
            return List.of();
        }

        List<Integer> values = new ArrayList<>();
        for (String token : content.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                Logger.warn("Failed to parse integer value '" + trimmed + "' from " + key + " array");
            }
        }
        return List.copyOf(values);
    }

    private static List<String> extractStringArray(String source, String key) {
        int start = source.indexOf(key);
        if (start < 0) {
            return List.of();
        }

        int[] bounds = findArrayBounds(source, start + key.length());
        if (bounds == null) {
            return List.of();
        }

        String content = source.substring(bounds[0] + 1, bounds[1]);
        if (content.isBlank()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        int index = 0;
        while (index < content.length()) {
            char c = content.charAt(index);
            if (Character.isWhitespace(c) || c == ',') {
                index++;
                continue;
            }
            if (c == '"') {
                index++;
                StringBuilder sb = new StringBuilder();
                boolean escaping = false;
                while (index < content.length()) {
                    char ch = content.charAt(index);
                    if (escaping) {
                        sb.append(unescapeChar(ch));
                        escaping = false;
                    } else if (ch == '\\') {
                        escaping = true;
                    } else if (ch == '"') {
                        break;
                    } else {
                        sb.append(ch);
                    }
                    index++;
                }
                values.add(sb.toString());
                if (index < content.length() && content.charAt(index) == '"') {
                    index++;
                }
            } else {
                int end = index;
                while (end < content.length() && content.charAt(end) != ',') {
                    end++;
                }
                values.add(content.substring(index, end).trim());
                index = end;
            }
        }
        return List.copyOf(values);
    }

    private static int[] findArrayBounds(String source, int fromIndex) {
        int colon = source.indexOf(':', fromIndex);
        if (colon < 0) {
            return null;
        }
        int openBracket = source.indexOf('[', colon);
        if (openBracket < 0) {
            return null;
        }
        int depth = 0;
        for (int i = openBracket; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return new int[]{openBracket, i};
                }
            }
        }
        return null;
    }

    private static String buildDeleteNotificationsPayload(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"notificationId\":").append(ids.get(i)).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String buildDeleteUsersPayload(List<String> usernames) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < usernames.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"username\":\"").append(escapeJson(usernames.get(i))).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static List<NotificationInfo> parseNotifications(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        List<NotificationInfo> notifications = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^}]*}");
        Matcher matcher = objectPattern.matcher(json);

        while (matcher.find()) {
            String entry = matcher.group();
            int id = extractInt(entry, "\"id\":");
            String title = extractString(entry, "\"title\":\"");
            String payload = extractOptionalString(entry, "\"payload\":");
            long fireAt = extractLong(entry, "\"fireAt\":");

            notifications.add(new NotificationInfo(0, id, title, payload, fireAt));
        }

        return notifications;
    }

    private static int extractInt(String source, String key) {
        int start = source.indexOf(key);
        if (start < 0) {
            return 0;
        }
        start += key.length();
        int end = start;
        while (end < source.length() && "-0123456789".indexOf(source.charAt(end)) != -1) {
            end++;
        }
        String value = source.substring(start, end).trim();
        if (value.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private static long extractLong(String source, String key) {
        int start = source.indexOf(key);
        if (start < 0) {
            return 0L;
        }
        start += key.length();
        int end = start;
        while (end < source.length() && "-0123456789".indexOf(source.charAt(end)) != -1) {
            end++;
        }
        String value = source.substring(start, end).trim();
        if (value.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private static String extractString(String source, String key) {
        int start = source.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (escaping) {
                sb.append(unescapeChar(c));
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractOptionalString(String source, String key) {
        int start = source.indexOf(key);
        if (start < 0) {
            return null;
        }
        start += key.length();
        while (start < source.length() && Character.isWhitespace(source.charAt(start))) {
            start++;
        }
        if (start >= source.length()) {
            return null;
        }
        if (source.startsWith("null", start)) {
            return null;
        }
        if (source.charAt(start) == '"') {
            return extractString(source, key);
        }
        return null;
    }

    private static char unescapeChar(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> c;
        };
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static final class UploadResponse {
        private final int clientId;
        private final List<Integer> webIds;
        private final List<String> statuses;

        UploadResponse(int clientId, List<Integer> webIds, List<String> statuses) {
            this.clientId = clientId;
            this.webIds = webIds == null ? List.of() : List.copyOf(webIds);
            this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
        }

        int clientId() {
            return clientId;
        }

        List<Integer> webIds() {
            return webIds;
        }

        List<String> statuses() {
            return statuses;
        }

        @Override
        public String toString() {
            return "UploadResponse{" +
                    "clientId=" + clientId +
                    ", webIds=" + webIds +
                    ", statuses=" + statuses +
                    '}';
        }
    }

    private static final class HttpResponse {
        private final int statusCode;
        private final String body;

        /**
         * Легка обгортка відповіді, що зберігає код стану та тіло.
         */
        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        int statusCode() {
            return statusCode;
        }

        String body() {
            return body;
        }

        boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }

        @Override
        public String toString() {
            return "HttpResponse{" +
                    "statusCode=" + statusCode +
                    ", body='" + body + '\'' +
                    '}';
        }
    }
}
