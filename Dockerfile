# Multi-stage Docker build for Scala application
# Stage 1: Build the application
FROM sbtscala/scala-sbt:eclipse-temurin-jammy-22.0.2_9_1.10.7_3.4.0 AS builder

# Set working directory
WORKDIR /app

# Copy build files first (for better caching)
COPY build.sbt ./
COPY project/ ./project/

# Download dependencies (this layer will be cached if build.sbt doesn't change)
RUN sbt update

# Copy source code
COPY src/ ./src/

# Build the application
RUN sbt clean compile stage

# Stage 2: Create runtime image
FROM eclipse-temurin:22-jre-jammy AS runtime

# Create application user for security
RUN groupadd -r smarthome && useradd -r -g smarthome -d /app -s /bin/false smarthome

# Set working directory
WORKDIR /app

# Copy application from builder stage
COPY --from=builder /app/target/universal/stage/ ./

# Create directories for application data
RUN mkdir -p /app/data /app/logs && \
    chown -R smarthome:smarthome /app

# Switch to application user
USER smarthome

# Expose default port (if your app uses HTTP/WebSocket)
EXPOSE 8080

# Health check (simple process check since we removed curl)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD ps aux | grep "[s]mart-home-state" > /dev/null || exit 1

# Set default environment variables (can be overridden by docker-compose)
ENV JAVA_OPTS="-Xmx256m -Xms128m -XX:+UseG1GC -XX:+UseContainerSupport"

# Start the application
CMD ["sh", "-c", "exec bin/smart-home-state $JAVA_OPTS"]
