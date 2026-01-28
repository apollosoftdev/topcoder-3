#!/bin/bash
# Docker entrypoint script
# Injects environment variables into frontend and configures SSL at runtime

set -e

WEBAPP_DIR="${JETTY_BASE}/webapps/ROOT"

# Extract WAR if not already extracted
if [ -f "${JETTY_BASE}/webapps/ROOT.war" ] && [ ! -d "$WEBAPP_DIR" ]; then
    echo "Extracting WAR file..."
    mkdir -p "$WEBAPP_DIR"
    unzip -q "${JETTY_BASE}/webapps/ROOT.war" -d "$WEBAPP_DIR"
    rm "${JETTY_BASE}/webapps/ROOT.war"
fi

# Generate environment config for frontend
echo "Generating frontend environment config..."
cat > "${WEBAPP_DIR}/js/env-config.js" << EOF
/**
 * Environment configuration (generated at container startup)
 * DO NOT EDIT - this file is auto-generated from environment variables
 */
window.__ENV__ = {
    NODE_ENV: "${NODE_ENV:-local}",
    TC_AUTH_CONNECTOR_URL: "${TC_AUTH_CONNECTOR_URL:-https://accounts-auth0.topcoder-dev.com}",
    TC_AUTH_URL: "${TC_AUTH_URL:-https://accounts-auth0.topcoder-dev.com}",
    TC_ACCOUNTS_APP_URL: "${TC_ACCOUNTS_APP_URL:-https://accounts.topcoder-dev.com}",
    TC_AUTH0_CDN_URL: "${TC_AUTH0_CDN_URL:-https://cdn.auth0.com}",
    TC_COOKIE_NAME: "${TC_COOKIE_NAME:-tcjwt}",
    TC_V3_COOKIE_NAME: "${TC_V3_COOKIE_NAME:-v3jwt}",
    TC_REFRESH_COOKIE_NAME: "${TC_REFRESH_COOKIE_NAME:-tcrft}",
    TC_TOKEN_EXPIRATION_OFFSET: ${TC_TOKEN_EXPIRATION_OFFSET:-60},
    API_BASE_URL: "${API_BASE_URL:-/api}"
};
EOF

echo "Environment config generated."

# Configure SSL keystore at runtime (if credentials provided via environment)
# This avoids storing secrets in the Docker image
if [ -f "${JETTY_BASE}/ssl/local.topcoder-dev.com.crt" ] && [ -n "${SSL_KEYSTORE_PASSWORD}" ]; then
    echo "Configuring SSL keystore..."

    # Create PKCS12 keystore from certificates
    openssl pkcs12 -export \
        -in "${JETTY_BASE}/ssl/local.topcoder-dev.com.crt" \
        -inkey "${JETTY_BASE}/ssl/local.topcoder-dev.com.key" \
        -out "${JETTY_BASE}/ssl/keystore.p12" \
        -name jetty \
        -password "pass:${SSL_KEYSTORE_PASSWORD}"

    # Convert to JKS format
    keytool -importkeystore \
        -srckeystore "${JETTY_BASE}/ssl/keystore.p12" \
        -srcstoretype PKCS12 \
        -srcstorepass "${SSL_KEYSTORE_PASSWORD}" \
        -destkeystore "${JETTY_BASE}/ssl/keystore.jks" \
        -deststorepass "${SSL_KEYSTORE_PASSWORD}" \
        -noprompt 2>/dev/null || true

    # Configure Jetty SSL
    cat >> "${JETTY_BASE}/start.d/ssl.ini" << SSLEOF
jetty.ssl.port=443
jetty.sslContext.keyStorePath=ssl/keystore.jks
jetty.sslContext.keyStorePassword=${SSL_KEYSTORE_PASSWORD}
jetty.sslContext.keyManagerPassword=${SSL_KEYSTORE_PASSWORD}
SSLEOF

    echo "SSL configured successfully."
fi

# Change to JETTY_BASE before starting Jetty
cd "${JETTY_BASE}"

# Start Jetty
echo "Starting Jetty from ${JETTY_BASE}..."
exec java -jar /opt/jetty/start.jar
