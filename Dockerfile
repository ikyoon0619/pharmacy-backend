# 1단계: 소스를 빌드해서 app.jar 를 만든다
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# 2단계: 실행에 필요한 JRE 와 app.jar 만 담는다 (이미지가 작아진다)
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY --from=build /workspace/build/libs/app.jar app.jar
# 무료 서버 메모리(512MB)에 맞춰 힙 크기를 제한
ENTRYPOINT ["java", "-Xmx300m", "-jar", "app.jar"]