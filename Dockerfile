# ── Stage 1: Compile ─────────────────────────────────────────
FROM ghcr.io/graalvm/native-image-community:21 AS builder

WORKDIR /app

# Install xargs - required by the Gradle wrapper, missing frmo the base image
RUN microdnf install -y findutils

# Copy build files BEFORE source code
# If only the code changes, Docker reutilizes dependency caches
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Download dependencies (separate layers = efficient cache)
RUN ./gradlew dependencies --no-daemon -q

# Copy and compile source
COPY src/ src/
RUN ./gradlew nativeCompile --no-daemon

# ── Stage 2: Runtime ────────────────────────────────────────────
FROM debian:bookworm-slim

WORKDIR /app

COPY --from=builder /app/build/native/nativeCompile/rinha-backend-2026 .
COPY resources/ /app/resources

EXPOSE 9999

ENTRYPOINT ["./rinha-backend-2026"]


