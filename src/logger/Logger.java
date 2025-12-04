package logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

  public enum OutputType {
    CONSOLE,
    FILE
  }

  private static OutputType output = OutputType.FILE;
  private static String logFilePath = "application.log";
  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // --- Налаштування ---
  public static void setOutput(OutputType type) {
    output = type;
  }

  public static void setLogFilePath(String path) {
    logFilePath = path;
  }

  // --- Методи логування ---
  public static void info(String message) {
    log("INFO", message, null);
  }

  public static void warn(String message) {
    log("WARN", message, null);
  }

  public static void error(String message) {
    log("ERROR", message, null);
  }

  public static void error(String message, Throwable throwable) {
    log("ERROR", message, throwable);
  }

  // --- Основна логіка ---
  private static void log(String level, String message, Throwable throwable) {
    String timestamp = LocalDateTime.now().format(formatter);

    // Отримуємо клас, який викликав логер
    String callerClass = getCallerClassName();

    String logEntry = String.format("[%s] [%s] [%s] %s", timestamp, level, callerClass, message);

    switch (output) {
      case CONSOLE -> {
        System.out.println(logEntry);
        if (throwable != null) throwable.printStackTrace(System.out);
      }
      case FILE -> writeToFile(logEntry, throwable);
    }
  }

  // --- Отримуємо ім’я класу, що викликав лог ---
  private static String getCallerClassName() {
    // [0] - Thread.getStackTrace
    // [1] - SimpleLogger.getCallerClassName
    // [2] - SimpleLogger.log
    // [3] - SimpleLogger.info/warn/error
    // [4] - Клас, який нас викликав
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    if (stackTrace.length >= 5) {
      return stackTrace[4].getClassName();
    } else {
      return "UnknownClass";
    }
  }

  // --- Запис у файл ---
  private static void writeToFile(String logEntry, Throwable throwable) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true))) {
      writer.println(logEntry);
      if (throwable != null) throwable.printStackTrace(writer);
    } catch (IOException e) {
      System.err.println("[LOGGER ERROR] Не вдалося записати лог у файл: " + e.getMessage());
    }
  }
}
