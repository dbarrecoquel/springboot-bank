# ═══════════════════════════════════════════════════════════════
#  banking-app — Dockerfile multi-stage
#
#  Stage 1 (builder) : compile et package avec Maven
#  Stage 2 (extractor): décompresse le JAR en couches (layertools)
#  Stage 3 (runtime) : image finale minimale (JRE 21 distroless)
#
#  Build :
#    docker build -t banking-app:latest .
#
#  Build sans tests :
#    docker build --build-arg SKIP_TESTS=true -t banking-app:latest .
#
#  Run :
#    docker run -p 8080:8080 --env-file .env banking-app:latest
# ═══════════════════════════════════════════════════════════════


# ───────────────────────────────────────────
#  Stage 1 — Builder Maven
# ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

ARG SKIP_TESTS=false
ARG MAVEN_OPTS="-Xmx1g -Xms512m"

WORKDIR /workspace

# Copie des POM en premier pour profiter du cache des couches Maven
COPY pom.xml .
COPY banking-domain/pom.xml       banking-domain/pom.xml
COPY banking-common/pom.xml       banking-common/pom.xml
COPY banking-security/pom.xml     banking-security/pom.xml
COPY banking-service/pom.xml      banking-service/pom.xml
COPY banking-infrastructure/pom.xml banking-infrastructure/pom.xml
COPY banking-api/pom.xml          banking-api/pom.xml

# Pré-téléchargement des dépendances (couche cachée séparément)
RUN apk add --no-cache curl && \
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz \
    | tar xz -C /opt && \
    ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn

RUN mvn dependency:go-offline -B --no-transfer-progress

# Copie des sources
COPY banking-domain/src       banking-domain/src
COPY banking-common/src       banking-common/src
COPY banking-security/src     banking-security/src
COPY banking-service/src      banking-service/src
COPY banking-infrastructure/src banking-infrastructure/src
COPY banking-api/src          banking-api/src

# Compilation et packaging
RUN mvn clean package -B --no-transfer-progress \
    -DskipTests=${SKIP_TESTS} \
    -Dmaven.test.skip=${SKIP_TESTS}


# ───────────────────────────────────────────
#  Stage 2 — Extracteur de couches JAR
#  Spring Boot layertools décompose le JAR en 4 couches :
#    dependencies / spring-boot-loader / snapshot-dependencies / application
# ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS extractor

WORKDIR /workspace

COPY --from=builder /workspace/banking-api/target/*.jar app.jar

RUN java -Djarmode=layertools -jar app.jar extract --destination extracted


# ───────────────────────────────────────────
#  Stage 3 — Image runtime finale (distroless)
# ───────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12:nonroot AS runtime

# Métadonnées OCI
LABEL org.opencontainers.image.title="banking-app" \
      org.opencontainers.image.description="Application bancaire Spring Boot 4" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="bank" \
      org.opencontainers.image.base.name="gcr.io/distroless/java21-debian12:nonroot"

WORKDIR /app

# Couches copiées dans l'ordre croissant de fréquence de modification
# → meilleur réutilisation du cache Docker lors des redéploiements
COPY --from=extractor /workspace/extracted/dependencies          ./
COPY --from=extractor /workspace/extracted/spring-boot-loader    ./
COPY --from=extractor /workspace/extracted/snapshot-dependencies ./
COPY --from=extractor /workspace/extracted/application           ./

# Ports exposés
EXPOSE 8080
EXPOSE 8081

# Variables d'environnement par défaut (surchargées via docker-compose ou K8s)
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    MANAGEMENT_SERVER_PORT=8081 \
    JAVA_TOOL_OPTIONS="\
      -XX:+UseContainerSupport \
      -XX:MaxRAMPercentage=75.0 \
      -XX:InitialRAMPercentage=50.0 \
      -XX:+UseG1GC \
      -XX:MaxGCPauseMillis=200 \
      -Djava.security.egd=file:/dev/./urandom \
      -Dfile.encoding=UTF-8"

# Healthcheck interne (utile hors docker-compose)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD ["java", "-cp", "BOOT-INF/lib/*:.", \
       "org.springframework.boot.loader.launch.JarLauncher", \
       "--spring.profiles.active=${SPRING_PROFILES_ACTIVE}", \
       "--management.endpoint.health.show-details=never"]

# Entrypoint Spring Boot layered JAR
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]