FROM clojure AS build-env

WORKDIR /usr/src/app

COPY ./project.clj /usr/src/app
RUN lein deps

COPY ./ /usr/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar

FROM openjdk:8-jre-alpine

WORKDIR /app
COPY --from=build-env /usr/src/app/app-standalone.jar /app/app-standalone.jar

ENTRYPOINT ["java", "-jar", "/app/app-standalone.jar"]
