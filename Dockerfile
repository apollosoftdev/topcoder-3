# AI Arena Docker Build
# Multi-stage build for Java 17 + Maven

# Build stage - using specific version with SHA256 digest for reproducibility
# hadolint ignore=DL3006
FROM maven:3.9.6-eclipse-temurin-17-alpine@sha256:ffddac7b04101358048f872fba9d978b2a1f9647955d158c1e565233cb95577d AS build
WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage - using specific version with SHA256 digest for security
# hadolint ignore=DL3006
FROM eclipse-temurin:17-jre-jammy@sha256:03c5b280ad53c041741552f231f5b65d97be39ca74fa4c7c1c9ace7f42cc3c9e

# Security: Don't store secrets in environment variables
# Labels for container metadata
LABEL org.opencontainers.image.title="AI Arena" \
      org.opencontainers.image.description="AI Arena competition platform" \
      org.opencontainers.image.vendor="Topcoder" \
      org.opencontainers.image.licenses="Apache-2.0"

# Install Jetty and required tools
# Pin specific Jetty version for security
ENV JETTY_VERSION=11.0.18 \
    JETTY_HOME=/opt/jetty \
    JETTY_BASE=/var/lib/jetty

WORKDIR /app

# Create non-root user for security first
RUN groupadd -r jetty && useradd -r -g jetty -d ${JETTY_BASE} -s /sbin/nologin jetty

# Install dependencies with security best practices
# - Update and install in single layer
# - Clean up apt cache and lists in same layer to reduce image size
# - Use --no-install-recommends to minimize attack surface
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        unzip \
        ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/* /tmp/* /var/tmp/*

# Download and install Jetty with verification
RUN curl -fsSL -o /tmp/jetty.tar.gz \
        "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz" && \
    tar xzf /tmp/jetty.tar.gz -C /opt && \
    mv /opt/jetty-home-${JETTY_VERSION} ${JETTY_HOME} && \
    rm /tmp/jetty.tar.gz

# Setup Jetty base with HTTP and HTTPS support
RUN mkdir -p ${JETTY_BASE}/webapps ${JETTY_BASE}/ssl && \
    cd ${JETTY_BASE} && \
    java -jar ${JETTY_HOME}/start.jar --add-modules=server,http,https,ssl,deploy,webapp,jsp

# Copy WAR file from build stage
COPY --from=build /app/target/*.war ${JETTY_BASE}/webapps/ROOT.war

# Copy SSL certificates directory (will be populated at runtime if needed)
# Using COPY with specific files, not ADD
COPY ssl/ ${JETTY_BASE}/ssl/

# Copy entrypoint script
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod 755 /docker-entrypoint.sh

# Set ownership to non-root user
RUN chown -R jetty:jetty ${JETTY_BASE} ${JETTY_HOME}

# Runtime environment variables (non-sensitive only)
ENV NODE_ENV=production \
    PORT=8080 \
    TC_AUTH_CONNECTOR_URL=https://accounts-auth0.topcoder-dev.com \
    TC_AUTH_URL=https://accounts-auth0.topcoder-dev.com \
    TC_ACCOUNTS_APP_URL=https://accounts.topcoder-dev.com \
    TC_AUTH0_CDN_URL=https://cdn.auth0.com \
    TC_JWKS_URL=https://topcoder-dev.auth0.com/.well-known/jwks.json \
    TC_COOKIE_NAME=tcjwt \
    TC_V3_COOKIE_NAME=v3jwt \
    TC_REFRESH_COOKIE_NAME=tcrft \
    TC_TOKEN_EXPIRATION_OFFSET=60 \
    API_BASE_URL=/api

# Expose ports
EXPOSE 8080 443

# Health check for container orchestration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

WORKDIR ${JETTY_BASE}

# Run as non-root user (security best practice)
USER jetty

ENTRYPOINT ["/docker-entrypoint.sh"]
