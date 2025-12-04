# Java Notification Client

Настільний Java-клієнт для взаємодії з HTTP-сервером сповіщень. Додаток поєднує локальну чергу нагадувань (SQLite + Swing popup) із віддаленим API, підтримує базову реєстрацію, синхронізацію сповіщень і адміністративні команди.

## Як працює
1. `Main` запускає CLI-майстер: користувач підтверджує/створює акаунт на сервері (`Client.sendAuth`, `Client.validateCredentials`).
2. Після успішного входу піднімається `Clock` — окремий потік, що циклічно:
   - підтягує сповіщення з локальної БД (`DataBaseWrapper.getEarliestNotifications`) та віддаленого API (`Client.fetchNotifications`);
   - зберігає/оновлює їх у SQLite (`DataBaseWrapper.upsertNotificationByWebId`);
   - показує готові до запуску сповіщення через Swing (`NotificationPopup.show`) і видаляє їх локально/віддалено.
3. Основний CLI-цикл дозволяє створювати нові нагадування, переглядати/видаляти віддалені, а також керувати користувачами через адмінські ендпоїнти.

## Вимоги та запуск
- Java 17+ (рекомендується JDK з модулем `java.sql`).
- JDBC-драйвер SQLite (`org.xerial:sqlite-jdbc`) має бути у classpath.

```bash
# компіляція
javac -cp "lib/sqlite-jdbc.jar" src/**/*.java

# запуск UI (за замовчуванням)
java -cp "lib/sqlite-jdbc.jar:src" Main

# запуск CLI-версії
java -cp "lib/sqlite-jdbc.jar:src" Main --cli
```

> Під час першого старту буде створено файл `sample.db` з таблицею `notifications` та журнал `application.log`.

## Структура каталогу
```
src/
├── Main.java               # CLI та точка входу
├── Clock.java              # цикл опрацювання сповіщень
├── db/DataBaseWrapper.java # робота з SQLite
├── logger/Logger.java      # файл/консольний логер
├── structures/NotificationInfo.java
├── ui/NotificationPopup.java
└── web/Client.java         # HTTP-фасад до бекенду
```

У корені проєкту також лежать `sample.db` (локальна база) та `identifier.sqlite` (експериментальна БД), `application.log`, директорія `out/` з скомпільованими класами й `src/web/Client_doc_uk.md` з деталями по мережевому клієнту.

## Класи та функції
### Main (CLI)
- `yesNo2Bool(String)` – перетворює відповіді `yes/no` на boolean.
- `handleRegistration()` – запитує логін/пароль, за потреби реєструє нового користувача через `Client.sendAuth`, кешує облікові дані.
- `handleAddNotification(DataBaseWrapper)` – читає параметри нагадування, опціонально шле його на сервер (`Client.sendNotification`), потім зберігає локально.
- `handleDeleteNotifications()` – приймає список webId, перевіряє адмінські права (`Client.fetchAdminStatus`) і викликає `Client.deleteNotifications`.
- `handleAddUserAsSuperuser()` / `handleDeleteUsersAsSuperuser()` – адмінські операції створення/видалення користувачів через `Client.registerUserAsSuperuser` та `Client.deleteUsers`.
- `showRemoteNotifications()` – друк результату `Client.fetchNotifications()`.
- `main(String[])` – ініціалізує БД, запускає `Clock` у daemon-потоці, обробляє текстові команди (`help`, `add notifications`, `delete notifications`, `add user`, `delete users`, `exit`).

### Clock (фоновий планувальник)
- Тримає `PriorityQueue<NotificationInfo>` та `Set<Integer>` уже відомих записів.
- `NotifyingCylce(DataBaseWrapper)` – нескінченний цикл: синхронізує віддалені сповіщення (`syncRemoteNotifications`), підтягує локальні (`addNotificationsFromDB`), спрацьовує за таймером (`checkFirstNotification`) і показує popup (`Notify`).
- `syncRemoteNotifications(DataBaseWrapper)` – періодично викликає `Client.fetchNotifications`, оновлює SQLite та in-memory чергу.
- `deleteRemoteNotification(NotificationInfo)` – після показу зносить запис на сервері (`Client.deleteNotifications`), при потребі перевіряє адмін-статус (`isAdmin`).
- `normalizeToMillis(long)` – перераховує секунди в мілісекунди (для зворотної сумісності).
- `stop()` – завершує цикл; використовується при виході з застосунку.

### db.DataBaseWrapper (SQLite шар)
- Конструктори одразу викликають `connect()` до `jdbc:sqlite:sample.db`.
- `makeDb()` – створює таблицю `notifications(id, webId, title, payload, fire_at)`.
- `getEarliestNotifications(int)` – повертає найстаріші записи за `fire_at`.
- `addNotification(NotificationInfo)` – додає сутність і проставляє згенерований `id` через `Statement.RETURN_GENERATED_KEYS`.
- `getNotificationByWebId(int)` / `upsertNotificationByWebId(NotificationInfo)` – пошук/оновлення записів, що прийшли з сервера.
- `deleteNotification(int)` – видаляє за локальним `id`.
- `thereIsAEarlierNotification(long)` – швидка перевірка, чи є нагадування раніше заданого часу (використовується для оптимізації).
- `closeDb()` – закриває з'єднання.

### web.Client (HTTP фасад)
- Конфігурація: `configureEndpoint`, `setCredentials`, `ensureCredentials`.
- Аутентифікація: `sendAuth`, `validateCredentials`, `registerUserAsSuperuser`, `fetchAdminStatus`.
- Нагадування: `fetchNotifications`, `sendNotification`, `uploadNotifications`, `deleteNotifications`.
- Користувачі: `deleteUsers`.
- Низькорівневі утиліти: `execute` (єдина точка HTTP), `openConnection`, `readFully`, генератори JSON (`buildUserPayload`, `buildNotificationsPayload`, `buildDeleteNotificationsPayload`, `buildDeleteUsersPayload`), парсери (`parseNotifications`, `parseUploadResponse`, `extractInt/Long/String`, `extractIntArray`, `extractStringArray`, `escapeJson`, `unescapeChar`).
- Внутрішні класи: `HttpResponse` (код/тіло + `isSuccessful()`), `UploadResponse` (clientId, webIds, statuses).

### structures.NotificationInfo
DTO із полями `id`, `webId`, `title`, `payload`, `fireAt`, геттерами/сеттерами та `toString()` для логів. `id` використовується для локальної БД, `webId` — для синхронізації з сервером.

### ui.NotificationPopup
Статичний метод `show(NotificationInfo)` створює немодальне Swing-вікно `JOptionPane`, яке автоматично закривається через 5 секунд (`Timer`). Використовується `Clock`-ом при спрацюванні нагадування.

### logger.Logger
Простий статичний логер із можливістю писати в консоль або файл. Методи `info/warn/error`, автоматичне визначення класу-джерела (`getCallerClassName`) та запис із timestamp.

## CLI-команди
Команди вводяться у довільному регістрі (пробіли ігноруються).
- `help` – список команд.
- `add notifications` / `an` – створити локальне сповіщення, за бажанням одразу відправити на сервер.
- `show notifications` / `sn` – показати всі віддалені сповіщення користувача.
- `delete notifications` / `dn` – видалити віддалені сповіщення за webId.
- `add user` / `au` – (адмін) створити/оновити користувача.
- `delete users` / `du` – (адмін) видалити користувачів.
- `exit` – коректно завершити `Clock`, закрити БД і вийти.

## Взаємодія з HTTP API
Клієнт очікує, що сервер підтримує наступні ендпоїнти (Basic Auth, JSON):
- `PUT /notifications/put` – завантаження нагадувань (масив `{id,title,payload,fireAt}`).
- `GET /notifications/get` – отримання власних сповіщень.
- `DELETE /notifications/delete/manually` – видалення власних webId.
- `DELETE /notifications/delete/superuser` – видалення будь-яких webId (адмін).
- `GET /users/status` – перевірка, чи поточний користувач адміністратор.
- `POST /users/add/manually` – самореєстрація новачка (без авторизації).
- `POST /users/add/superuser` / `DELETE /users/delete/superuser` – адмінські команди для керування користувачами.
