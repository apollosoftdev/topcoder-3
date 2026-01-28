# SSL Certificates for Local Development

This directory contains SSL certificates for running the app on `https://local.topcoder-dev.com`.

## Recommended: Use mkcert (Secure)

The recommended way to generate local development certificates is using [mkcert](https://github.com/FiloSottile/mkcert):

```bash
# Install mkcert
brew install mkcert  # macOS
# or: sudo apt install mkcert  # Linux

# Install local CA
mkcert -install

# Generate certificates
mkcert -key-file ssl/local.topcoder-dev.com.key \
       -cert-file ssl/local.topcoder-dev.com.crt \
       local.topcoder-dev.com localhost 127.0.0.1
```

## Alternative: Manual OpenSSL (for reference)

If mkcert is not available, you can use OpenSSL. Note that this generates a self-signed certificate that browsers will warn about.

```bash
# Generate with passphrase (recommended)
openssl req -x509 -newkey rsa:4096 -sha256 -days 365 \
  -keyout ssl/local.topcoder-dev.com.key \
  -out ssl/local.topcoder-dev.com.crt \
  -subj "/C=US/ST=State/L=City/O=Topcoder/CN=local.topcoder-dev.com" \
  -addext "subjectAltName=DNS:local.topcoder-dev.com,DNS:localhost"

# You will be prompted to enter a passphrase for the private key
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

4. Accept the self-signed certificate warning (or use mkcert to avoid this)

## Trust the Certificate (for self-signed only)

If using self-signed certificates, you may need to manually trust them:

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

## Security Notes

- These certificates are for **local development only**
- Never use self-signed certificates in production
- The private key should never be committed to version control with real credentials
- For production, use certificates from a trusted CA (e.g., Let's Encrypt)
