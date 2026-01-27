#!/bin/bash
# Docker entrypoint script
# Injects environment variables into frontend before starting Jetty

set -e

WEBAPP_DIR="${JETTY_BASE}/webapps/ROOT"

# Extract WAR if not already extracted
if [ -f "${JETTY_BASE}/webapps/ROOT.war" ] && [ ! -d "$WEBAPP_DIR" ]; then
    echo "Extracting WAR file..."
    mkdir -p "$WEBAPP_DIR"
    cd "$WEBAPP_DIR"
    jar -xf ../ROOT.war
    rm ../ROOT.war
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

echo "Environment config generated:"
cat "${WEBAPP_DIR}/js/env-config.js"

# Start Jetty
echo "Starting Jetty..."
exec java -jar /opt/jetty/start.jar
