# Лабораторная работа №1 — FastCGI сервер на Java

## Сборка

```bash
./gradlew build
```

Готовый исполняемый jar: `build/libs/labwork1.jar`.

## Запуск FastCGI сервера

1. Выберите свободный TCP-порт, например `9000`.
2. Запустите приложение от имени пользователя, под которым работает ваш FastCGI-процесс:

```bash
java \
  -DFCGI_PORT=9000 \
  -cp "build/libs/labwork1.jar;lib/fastcgi-lib.jar" \
  ru.pozitp.Main
```

Приложение хранит историю попаданий в оперативной памяти, пока процесс работает.

## Фронтенд

- Основная страница: `src/main/resources/static/index.html`.
- JavaScript выполняет валидацию формы, блокирует некорректный ввод и отправляет POST-запрос на FastCGI сервер через `fetch`.
- История запросов обновляется без перезагрузки страницы.

## Локальный запуск через Docker

```bash
# Первая сборка (или при изменении Java-кода/конфига)
docker compose up --build -d

# Остановка контейнеров
docker compose down
```

Приложение доступно по адресу [http://localhost:8080/](http://localhost:8080/). 
