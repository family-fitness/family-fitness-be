# syntax=docker/dockerfile:1

# 운영 이미지. GitHub Actions(backend-deploy)가 main 에 올라온 코드로 만들어 GHCR 에 올리고,
# Lightsail 서버가 받아 띄운다(deploy/README.md). 로컬에서 만들어 볼 때: docker build -t familyfitness-be backend

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Gradle 배포판과 플러그인을 먼저 받아 두면 src 만 바뀐 빌드는 이 층을 캐시에서 다시 쓴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# 시험은 이미지를 만들기 전에 CI 가 돌렸다(backend-deploy 의 ci 작업). 여기서는 jar 만 만든다.
# jar 를 층별로 풀어 두면 서버가 새 이미지를 받을 때 바뀌지 않은 의존성 층은 다시 받지 않는다.
RUN ./gradlew --no-daemon bootJar \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar$')" application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /application
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
