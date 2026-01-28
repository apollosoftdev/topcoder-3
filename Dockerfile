# AI Arena Docker Build
# Multi-stage build for Java 17 + Maven

# Build stage
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies with cache mount (BuildKit)
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B

# Copy source and build with cache mount
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn clean package -DskipTests -B

# Runtime stage - using specific version tag instead of 'latest'
FROM eclipse-temurin:17.0.9_9-jre-jammy
WORKDIR /app

# Install Jetty and required tools
# Pin specific Jetty version for security
ENV JETTY_VERSION=11.0.18
ENV JETTY_HOME=/opt/jetty
ENV JETTY_BASE=/var/lib/jetty

# Create non-root user for security (Critical misconfiguration fix)
RUN groupadd -r jetty && useradd -r -g jetty jetty

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl unzip ca-certificates && \
    curl -fsSL https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz | tar xzf - -C /opt && \
    mv /opt/jetty-home-${JETTY_VERSION} ${JETTY_HOME} && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Setup Jetty base with HTTP and HTTPS support
RUN mkdir -p ${JETTY_BASE}/webapps ${JETTY_BASE}/ssl && \
    cd ${JETTY_BASE} && \
    java -jar ${JETTY_HOME}/start.jar --add-modules=server,http,https,ssl,deploy,webapp,jsp

# Copy WAR file
COPY --from=build /app/target/*.war ${JETTY_BASE}/webapps/ROOT.war

# Copy SSL certificates (optional - mount at runtime if not present)
COPY ssl/local.topcoder-dev.com.* ${JETTY_BASE}/ssl/

# SSL keystore password - use build arg, not hardcoded
ARG SSL_KEYSTORE_PASSWORD
ENV SSL_KEYSTORE_PASSWORD=${SSL_KEYSTORE_PASSWORD:-}

# Create keystore from certificates (only if password is provided)
RUN if [ -f ${JETTY_BASE}/ssl/local.topcoder-dev.com.crt ] && [ -n "${SSL_KEYSTORE_PASSWORD}" ]; then \
    openssl pkcs12 -export -in ${JETTY_BASE}/ssl/local.topcoder-dev.com.crt \
        -inkey ${JETTY_BASE}/ssl/local.topcoder-dev.com.key \
        -out ${JETTY_BASE}/ssl/keystore.p12 \
        -name jetty -password pass:${SSL_KEYSTORE_PASSWORD} && \
    keytool -importkeystore -srckeystore ${JETTY_BASE}/ssl/keystore.p12 \
        -srcstoretype PKCS12 -srcstorepass ${SSL_KEYSTORE_PASSWORD} \
        -destkeystore ${JETTY_BASE}/ssl/keystore.jks \
        -deststorepass ${SSL_KEYSTORE_PASSWORD} -noprompt && \
    echo "jetty.ssl.port=443" >> ${JETTY_BASE}/start.d/ssl.ini && \
    echo "jetty.sslContext.keyStorePath=ssl/keystore.jks" >> ${JETTY_BASE}/start.d/ssl.ini && \
    echo "jetty.sslContext.keyStorePassword=${SSL_KEYSTORE_PASSWORD}" >> ${JETTY_BASE}/start.d/ssl.ini && \
    echo "jetty.sslContext.keyManagerPassword=${SSL_KEYSTORE_PASSWORD}" >> ${JETTY_BASE}/start.d/ssl.ini; \
    fi

# Copy entrypoint script
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Set ownership to non-root user
RUN chown -R jetty:jetty ${JETTY_BASE} ${JETTY_HOME}

# Environment variables for runtime configuration
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

EXPOSE 8080 443

# Health check (fixes misconfiguration)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

WORKDIR ${JETTY_BASE}

# Run as non-root user (Critical security fix)
USER jetty

ENTRYPOINT ["/docker-entrypoint.sh"]
