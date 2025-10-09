# syntax=docker/dockerfile:1

FROM gradle:8.10-jdk17 AS build
WORKDIR /home/gradle/src
COPY . .
RUN gradle clean build -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV FCGI_PORT=1337
COPY --from=build /home/gradle/src/build/libs/labwork1.jar ./app.jar
COPY lib/fastcgi-lib.jar ./lib/fastcgi-lib.jar
EXPOSE 1337
ENTRYPOINT ["sh","-c","java -DFCGI_PORT=${FCGI_PORT:-1337} -cp /app/app.jar:/app/lib/fastcgi-lib.jar ru.pozitp.Main"]
