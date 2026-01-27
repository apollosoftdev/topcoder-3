# SSL Certificates for Local Development

This directory contains SSL certificates for running the app on `https://local.topcoder-dev.com`.

## Generate Certificates

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ssl/local.topcoder-dev.com.key \
  -out ssl/local.topcoder-dev.com.crt \
  -subj "/C=US/ST=State/L=City/O=Topcoder/CN=local.topcoder-dev.com" \
  -addext "subjectAltName=DNS:local.topcoder-dev.com,DNS:localhost"
```

## Setup

1. Add to `/etc/hosts`:
   ```
   127.0.0.1    local.topcoder-dev.com
   ```

2. Run the server with HTTPS:
   ```bash
   sudo npm run dev:https
   ```

3. Open https://local.topcoder-dev.com in your browser

4. Accept the self-signed certificate warning (or add to trusted certificates)

## Trust the Certificate (Optional)

To avoid browser warnings, add the certificate to your system's trusted certificates:

### macOS
```bash
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain ssl/local.topcoder-dev.com.crt
```

### Linux (Ubuntu/Debian)
```bash
sudo cp ssl/local.topcoder-dev.com.crt /usr/local/share/ca-certificates/
sudo update-ca-certificates
```

### Windows
1. Double-click the .crt file
2. Click "Install Certificate"
3. Select "Local Machine" → "Trusted Root Certification Authorities"
