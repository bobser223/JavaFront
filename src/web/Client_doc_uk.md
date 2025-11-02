# Документація `web.Client`

## Загальний огляд
Клас `web.Client` реалізує фасад HTTP-клієнта для взаємодії з сервером сповіщень. Він надає методи для:
- налаштування цільового ендпоїнта (хост/порт);
- зберігання та перевірки облікових даних;
- керування користувачами (реєстрація, видалення);
- виконання CRUD-операцій над сповіщеннями;
- внутрішньої обробки HTTP-запитів та відповідей.

Клієнт використовує прості JSON-пейлоади й базову автентифікацію (Basic Auth).

## API конфігурації та автентифікації
- `configureEndpoint(String hostOverride, int portOverride)` – дозволяє перевизначити хост і порт сервера. Порожній або некоректний аргумент ігнорується.
- `setCredentials(String username, String password)` – зберігає облікові дані для подальших автентифікованих запитів.
- `sendAuth(String[] auth)` – реєструє користувача на сервері та кешує його облікові дані. Повертає `1` у разі успіху, `0` – якщо реєстрація завершилася помилкою.
- `validateCredentials()` – виконує перевірочний запит до сервера, щоб переконатися, що збережені облікові дані дійсні.

## Керування користувачами
- `registerUserAsSuperuser(String targetUsername, String targetPassword, boolean makeAdmin)` – створює або оновлює користувача через привілейований ендпоїнт. За потреби може призначити роль адміністратора.
- `deleteUsers(List<String> usernames)` – видаляє перелік користувачів, використовуючи суперкористувацький доступ.

## Операції зі сповіщеннями
- `fetchNotifications()` – отримує сповіщення поточного користувача. У разі збою повертає порожній список.
- `sendNotification(NotificationInfo notification)` – надсилає одне сповіщення та повертає список статусів, які надіслав сервер (порожній список у разі збою).
- `uploadNotifications(List<NotificationInfo> notifications)` – завантажує список сповіщень і повертає `Client.UploadResponse` з ідентифікатором клієнта, веб-ідентифікаторами та статусами; у разі помилки повертає `null`.
- `deleteNotifications(List<Integer> notificationIds, boolean superuser)` – видаляє сповіщення за вказаними ідентифікаторами; може використовувати суперкористувацький ендпоїнт.

## Допоміжні методи
- `execute(String method, String path, String body, boolean includeAuth)` – єдина точка виконання HTTP-запитів; відповідає за формування заголовків, відправлення тіла та читання відповіді.
- `openConnection(String method, String path, boolean includeAuth)` – відкриває та налаштовує `HttpURLConnection`, додає заголовок авторизації за потреби.
- `readFully(InputStream stream)` – перетворює вхідний потік у рядок UTF-8.
- `ensureCredentials()` – перевіряє, що облікові дані встановлені перед виконанням захищених запитів.
- `buildUserPayload`, `buildNotificationsPayload`, `buildDeleteNotificationsPayload`, `buildDeleteUsersPayload` – формують відповідні JSON-пейлоади.
- `parseNotifications(String json)` та допоміжні методи `extractInt`, `extractLong`, `extractString`, `extractOptionalString` – виконують ручний розбір JSON-відповідей.
- `parseUploadResponse(String responseBody)` разом із `extractIntArray`, `extractStringArray` – розбирає відповідь сервера про результати завантаження.
- `escapeJson(String value)` і `unescapeChar(char c)` – обробляють спеціальні символи в рядках.

## Внутрішній клас `HttpResponse`
Приватний статичний клас-обгортка, що інкапсулює HTTP-статус і тіло відповіді та надає метод `isSuccessful()` для швидкої перевірки успішності виклику.
