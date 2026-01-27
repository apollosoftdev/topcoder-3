# AI Arena Docker Build
# Multi-stage build for Java 17 + Maven

# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
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

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install Jetty
ENV JETTY_VERSION=11.0.18
ENV JETTY_HOME=/opt/jetty
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz | tar xzf - -C /opt && \
    mv /opt/jetty-home-${JETTY_VERSION} ${JETTY_HOME} && \
    useradd -m jetty && \
    rm -rf /var/lib/apt/lists/*

# Setup Jetty base with HTTP and HTTPS support
ENV JETTY_BASE=/var/lib/jetty
RUN mkdir -p ${JETTY_BASE}/webapps ${JETTY_BASE}/ssl && \
    cd ${JETTY_BASE} && \
    java -jar ${JETTY_HOME}/start.jar --add-modules=server,http,https,ssl,deploy,webapp,jsp

# Copy WAR file
COPY --from=build /app/target/*.war ${JETTY_BASE}/webapps/ROOT.war

# Copy SSL certificates (optional - mount at runtime if not present)
COPY ssl/local.topcoder-dev.com.* ${JETTY_BASE}/ssl/

# Create keystore from certificates
RUN if [ -f ${JETTY_BASE}/ssl/local.topcoder-dev.com.crt ]; then \
    openssl pkcs12 -export -in ${JETTY_BASE}/ssl/local.topcoder-dev.com.crt \
        -inkey ${JETTY_BASE}/ssl/local.topcoder-dev.com.key \
        -out ${JETTY_BASE}/ssl/keystore.p12 \
        -name jetty -password pass:changeit && \
    keytool -importkeystore -srckeystore ${JETTY_BASE}/ssl/keystore.p12 \
        -srcstoretype PKCS12 -srcstorepass changeit \
        -destkeystore ${JETTY_BASE}/ssl/keystore.jks \
        -deststorepass changeit -noprompt; \
    fi

# Configure Jetty SSL
RUN echo "jetty.ssl.port=443" >> ${JETTY_BASE}/start.d/ssl.ini && \
    echo "jetty.sslContext.keyStorePath=ssl/keystore.jks" >> ${JETTY_BASE}/start.d/ssl.ini && \
    echo "jetty.sslContext.keyStorePassword=changeit" >> ${JETTY_BASE}/start.d/ssl.ini && \
    echo "jetty.sslContext.keyManagerPassword=changeit" >> ${JETTY_BASE}/start.d/ssl.ini

# Environment
ENV ENVIRONMENT=production
ENV PORT=8080

EXPOSE 8080 443

# Run Jetty
WORKDIR ${JETTY_BASE}
CMD ["java", "-jar", "/opt/jetty/start.jar"]
