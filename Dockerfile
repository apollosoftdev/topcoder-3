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

# Setup Jetty base
ENV JETTY_BASE=/var/lib/jetty
RUN mkdir -p ${JETTY_BASE}/webapps && \
    cd ${JETTY_BASE} && \
    java -jar ${JETTY_HOME}/start.jar --add-modules=server,http,deploy,webapp,jsp

# Copy WAR file
COPY --from=build /app/target/*.war ${JETTY_BASE}/webapps/ROOT.war

# Environment
ENV ENVIRONMENT=development
ENV PORT=8080

EXPOSE 8080

# Run Jetty
WORKDIR ${JETTY_BASE}
CMD ["java", "-jar", "/opt/jetty/start.jar"]
