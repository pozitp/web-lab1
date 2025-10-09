# Лабораторная работа №1 — FastCGI сервер на Java

## Сборка

```bash
./gradlew build
```

Готовый исполняемый jar: `build/libs/web-lab1-1.0-SNAPSHOT.jar`.

## Запуск FastCGI сервера

1. Выберите свободный TCP-порт, например `9000`.
2. Запустите приложение от имени пользователя, под которым работает ваш FastCGI-процесс:

```bash
java \
  -DFCGI_PORT=9000 \
  -cp "build/libs/web-lab1-1.0-SNAPSHOT.jar;lib/fastcgi-lib.jar" \
  ru.pozitp.Main
```

Приложение хранит историю попаданий в оперативной памяти, пока процесс работает.

## Настройка Apache httpd (пример)

```apache
# Статический контент (HTML/CSS/JS)
Alias /lab1/static "/path/to/web-lab1/src/main/resources/static"
<Directory "/path/to/web-lab1/src/main/resources/static">
    Require all granted
</Directory>

# FastCGI (модуль mod_fcgid)
FcgidConnectTimeout 20
FcgidIOTimeout 20
FcgidInitialEnv FCGI_PORT 9000

# Пробрасываем POST-запросы к серверу
ProxyPassMatch ^/fcgi-bin/point-checker$ fcgi://127.0.0.1:9000/
```

При необходимости скорректируйте путь `/fcgi-bin/point-checker` — он должен совпадать со значением атрибута `action` формы в `index.html`.

## Фронтенд

- Основная страница: `src/main/resources/static/index.html`.
- CSS полностью включён в документ и демонстрирует использование классов, идентификаторов, псевдоклассов и каскадирования.
- JavaScript выполняет валидацию формы, блокирует некорректный ввод и отправляет POST-запрос на FastCGI сервер через `fetch`.
- История запросов обновляется без перезагрузки страницы.

## Что ещё стоит сделать

- Заменить шаблонные данные в шапке страницы (ФИО, номер группы).
- Убедиться, что выбранный порт открыт на Helios, и процесс имеет права на его использование.
- Протестировать связку Apache ↔ FastCGI с реальной конфигурацией на Helios.

## Локальный запуск через Docker

```bash
# Первая сборка (или при изменении Java-кода/конфига)
docker compose up --build -d

# Остановка контейнеров
docker compose down
```

В результате поднимаются два контейнера:
- `fcgi` — Java FastCGI сервер, слушает порт `1337` внутри сети docker.
- `httpd` — Apache httpd, публикует статический контент из `src/main/resources/static` и проксирует POST на `/fcgi-bin/point-checker`.

Приложение доступно по адресу [http://localhost:8080/](http://localhost:8080/). Чтобы сменить порт или путь:
1. Обнови `docker-compose.yml` (порт публикации, переменная `FCGI_PORT`).
2. При необходимости скорректируй `httpd.conf` и атрибут `action` в `index.html`.
