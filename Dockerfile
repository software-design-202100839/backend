# === Stage 1: Build ===
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Gradle wrapper + 설정 파일 먼저 복사 (캐시 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

# 의존성 다운로드 (소스 변경 시 캐시 재사용)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 복사 + 빌드 (테스트 제외 — CI에서 이미 통과)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# === Stage 2: Runtime ===
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 빌드 결과물만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 비root 사용자로 실행
RUN groupadd -r sscm && useradd -r -g sscm sscm
USER sscm

EXPOSE 8080

# prod 프로파일은 환경변수로 주입 (ECS Task Definition에서 설정)
ENTRYPOINT ["java", "-jar", "app.jar"]
