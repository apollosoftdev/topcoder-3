# AI Arena

AI Arena competition platform with Topcoder authentication integration.

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 17, Jersey (JAX-RS), Maven |
| **Frontend** | HTML5, Vanilla JavaScript (ES6+), CSS3 |
| **Authentication** | tc-auth-lib, JWT (HS256/RS256) |
| **Testing** | JUnit 5 (Java), Jest 29.7.0 (JavaScript) |
| **Container** | Docker, Jetty 11 |

## Quick Start

### Using Docker (Recommended)

```bash
npm run docker:build
npm run docker:run
```

After pulling updates:
```bash
npm run docker:restart
```

### Using Docker with HTTPS (local.topcoder-dev.com)

For testing with Topcoder authentication using the `local.topcoder-dev.com` domain:

**1. Add hosts entry:**
```bash
npm run hosts:add
# Or manually: echo '127.0.0.1    local.topcoder-dev.com' | sudo tee -a /etc/hosts
```

**2. Generate SSL certificates:**
```bash
npm run ssl:generate
```

**3. Trust the certificate (optional, avoids browser warnings):**
```bash
# macOS
npm run ssl:trust:mac

# Linux
npm run ssl:trust:linux
```

**4. Build and run with HTTPS:**
```bash
npm run docker:restart:https
```

**5. Access the application:**
- HTTPS: https://local.topcoder-dev.com/
- HTTP: http://local.topcoder-dev.com:8080/

> **Note:** The HTTPS Docker setup uses production Topcoder auth URLs to avoid CSP issues with dev auth servers.

### Using Node.js (Development)

```bash
npm run dev
```

This starts a development server with mock APIs at http://localhost:8080

For HTTPS development with `local.topcoder-dev.com`:
```bash
# Generate and trust certificates first (see Docker HTTPS section above)
sudo npm run dev:https
```

Access at https://local.topcoder-dev.com/

> **Note:** `sudo` is required for port 443 on macOS/Linux.

### Using Maven (Production)

Requires Java 17 and Maven installed.

```bash
npm run build
npm start
```

### Access URLs

| Page | HTTP | HTTPS (local.topcoder-dev.com) |
|------|------|--------------------------------|
| Home | http://localhost:8080/ | https://local.topcoder-dev.com/ |
| Arena | http://localhost:8080/arena.html | https://local.topcoder-dev.com/arena.html |
| Admin | http://localhost:8080/admin.html | https://local.topcoder-dev.com/admin.html |

---

## 1. Frontend Authentication UI

### Header Components

Both `arena.html` and `admin.html` include authentication controls in the header:

| Component | State | Description |
|-----------|-------|-------------|
| Login Button | Unauthenticated | Redirects to Topcoder auth |
| Member Handle | Authenticated | Displays user's Topcoder handle |
| Logout Button | Authenticated | Clears session and redirects |
| Loading Indicator | Loading | Shown during auth check |

### Authentication States

- **Unauthenticated**: Shows "Login" button only
- **Authenticated**: Shows member handle + "Logout" button
- **Loading**: Shows spinner during authentication check
- **Error**: Shows error message with retry option

---

## 2. Frontend Authentication Integration

### tc-auth-lib Integration

The frontend uses Topcoder's tc-auth-lib pattern:

```
src/main/webapp/js/auth/
├── auth-config.js    # Environment configuration
├── auth-service.js   # Token management, API calls
└── auth-ui.js        # UI state management
```

### Token Management

- **V3 Token**: Obtained from tc-auth-lib connector (RS256)
- **V2 Token**: Read from `tcjwt` cookie (HS256, legacy)
- **Auto-refresh**: Tokens refreshed before expiration
- **Storage**: Memory only (not localStorage)

### Member Information

Retrieved from decoded JWT token:

| Field | Required | Usage |
|-------|----------|-------|
| `handle` | Yes | Displayed in header |
| `userId` | No | Internal tracking |
| `roles` | No | Admin access control |

---

## 3. Backend Authentication Integration

### Configuration

File: `src/main/resources/auth.properties`

```properties
# Development
topcoder.auth.connector.url=https://accounts-auth0.topcoder-dev.com
topcoder.auth.jwks.url=https://topcoder-dev.auth0.com/.well-known/jwks.json

# Production
topcoder.auth.connector.url=https://accounts-auth0.topcoder.com
topcoder.auth.jwks.url=https://topcoder.auth0.com/.well-known/jwks.json
```

### JWT Validation

Backend validates tokens using:

| Token Type | Algorithm | Validation Method |
|------------|-----------|-------------------|
| V2 | HS256 | Shared secret (`TC_AUTH_SECRET`) |
| V3 | RS256 | Public JWKS (no secret needed) |

### CORS Configuration

Allowed origins (configured in `CorsFilter.java`):

- `https://*.topcoder.com`
- `https://*.topcoder-dev.com`
- `http://localhost:*`

---

## 4. Login Flow

1. User clicks **Login** button
2. Frontend generates login URL with return URL
3. User redirected to `accounts-auth0.topcoder[-dev].com`
4. User authenticates with Topcoder credentials
5. Redirected back with JWT in `tcjwt` cookie
6. Frontend detects token, updates UI to authenticated state

### Login URL Format

```
https://accounts-auth0.topcoder-dev.com/?retUrl={encoded_return_url}
```

---

## 5. Logout Flow

1. User clicks **Logout** button
2. Frontend clears local authentication state
3. Frontend clears member information from memory
4. Redirect to Topcoder logout URL
5. Cookie cleared, UI shows login button

### Logout URL Format

```
https://accounts-auth0.topcoder-dev.com/?logout=true&retUrl={encoded_return_url}
```

---

## 6. Admin Interface Authentication

### Role-Based Access Control

Admin features require appropriate roles:

| Role | Access Level |
|------|--------------|
| `administrator` | Full admin access |
| `Topcoder User` | Standard user access |

### Admin API Protection

All admin endpoints:
- Require valid authentication token
- Check user roles before processing
- Return `401` for missing/invalid token
- Return `403` for insufficient permissions

---

## 7. Integration with Existing Features

### Contestant Registration

- Stores member handle for authenticated users
- Anonymous registration still supported for testing

### Score Tracking

- Scores associated with member handle
- Leaderboards display member handles
- Score history available for authenticated users

### Progress Tracking

- Progress persisted by member handle
- Cross-session progress retrieval
- History available for authenticated users

---

## 8. Error Handling

### Error Types

| Error | Cause | User Action |
|-------|-------|-------------|
| Invalid Token | Malformed JWT | Re-login |
| Expired Token | Token past expiration | Re-login |
| Network Error | Connection failed | Retry |
| Service Unavailable | Auth service down | Wait and retry |

### Loading States

Loading indicators shown during:
- Initial authentication check
- Token refresh operations
- Member profile retrieval
- Logout process

---

## 9. Testing

### Run All Tests

```bash
npm test
```

### Backend Tests (JUnit 5)

```bash
npm run test:backend
```

Tests cover:
- Valid token validation
- Invalid/expired token handling
- Member profile retrieval
- Role verification
- CORS configuration

### Frontend Tests (Jest)

```bash
npm run test:frontend
```

Tests cover:
- Authentication initialization
- Token retrieval and management
- Login/logout URL generation
- UI state transitions

---

## 10. API Reference

### Authentication Endpoints

#### GET /api/auth/status

Check authentication status.

**Response (Authenticated):**
```json
{
  "authenticated": true,
  "memberInfo": {
    "handle": "billsedison",
    "userId": "12345",
    "roles": ["Topcoder User"]
  }
}
```

**Response (Unauthenticated):**
```json
{
  "authenticated": false,
  "error": "No authentication token provided"
}
```

#### GET /api/auth/member/{handle}

Get member profile by handle.

**Response:**
```json
{
  "handle": "billsedison",
  "userId": "12345",
  "status": "active"
}
```

### Contestant Endpoints

| Method | Endpoint | Auth Required |
|--------|----------|---------------|
| POST | `/api/contestants/register` | Yes |
| GET | `/api/contestants/registration/{competitionId}` | Yes |
| GET | `/api/contestants/my-registrations` | Yes |
| DELETE | `/api/contestants/unregister/{competitionId}` | Yes |

### Score Endpoints

| Method | Endpoint | Auth Required |
|--------|----------|---------------|
| POST | `/api/scores/submit` | Yes |
| GET | `/api/scores/leaderboard/{competitionId}` | No |
| GET | `/api/scores/my-scores` | Yes |
| GET | `/api/scores/my-rank/{competitionId}` | Yes |

### Progress Endpoints

| Method | Endpoint | Auth Required |
|--------|----------|---------------|
| POST | `/api/progress/save` | Yes |
| GET | `/api/progress/load/{competitionId}` | Yes |
| GET | `/api/progress/history` | Yes |
| DELETE | `/api/progress/clear/{competitionId}` | Yes |

### Response Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 401 | Not authenticated |
| 403 | Forbidden (insufficient permissions) |
| 404 | Not found |

---

## Environment Configuration

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `ENVIRONMENT` | `development` or `production` | No (default: `production`) |
| `TC_AUTH_SECRET` | HS256 secret for V2 tokens | Only for V2 tokens |

> **Note:** V3 (RS256) tokens use public JWKS - no secret required.

### Development vs Production

| Setting | Development | Production |
|---------|-------------|------------|
| Auth URL | `accounts-auth0.topcoder-dev.com` | `accounts-auth0.topcoder.com` |
| JWKS URL | `topcoder-dev.auth0.com` | `topcoder.auth0.com` |
| Cookie Domain | `localhost` | `.topcoder.com` |

---

## NPM Scripts

| Script | Description |
|--------|-------------|
| `npm run dev` | Start dev server with mock APIs |
| `npm run dev:https` | Start dev server with HTTPS on port 443 |
| `npm run build` | Build with Maven |
| `npm start` | Run production server (Maven/Jetty) |
| `npm test` | Run all tests |
| `npm run test:frontend` | Run Jest tests |
| `npm run test:backend` | Run JUnit tests |
| `npm run docker:build` | Build Docker image |
| `npm run docker:run` | Run Docker container (HTTP) |
| `npm run docker:run:https` | Run Docker container (HTTPS on 443) |
| `npm run docker:stop` | Stop Docker container |
| `npm run docker:restart` | Rebuild and restart Docker (HTTP) |
| `npm run docker:restart:https` | Rebuild and restart Docker (HTTPS) |
| `npm run docker:logs` | View Docker container logs |
| `npm run ssl:generate` | Generate self-signed SSL certificates |
| `npm run ssl:trust:mac` | Trust SSL certificate on macOS |
| `npm run ssl:trust:linux` | Trust SSL certificate on Linux |
| `npm run ssl:setup:mac` | Generate + trust certificate (macOS) |
| `npm run ssl:setup:linux` | Generate + trust certificate (Linux) |
| `npm run hosts:add` | Add local.topcoder-dev.com to /etc/hosts |
| `npm run clean` | Clean build artifacts |

---

## Project Structure

```
src/main/
├── java/com/terra/vibe/arena/
│   ├── api/              # REST endpoints (AuthResource, etc.)
│   ├── auth/             # JWT validation, filters
│   ├── config/           # Auth config, CORS filter
│   ├── model/            # Data models (Contestant, Score, Progress)
│   └── service/          # Business logic services
├── resources/
│   └── auth.properties   # Authentication configuration
└── webapp/
    ├── js/auth/          # Frontend authentication
    │   ├── auth-config.js
    │   ├── auth-service.js
    │   └── auth-ui.js
    ├── css/              # Stylesheets
    ├── index.html        # Home page
    ├── arena.html        # Contestant interface
    └── admin.html        # Admin interface
```

---

## Security Considerations

- **Token Storage**: Tokens stored in memory only, never in localStorage
- **HTTPS**: Required in production for secure cookies
- **CORS**: Configured to allow only legitimate origins
- **Server Validation**: Tokens always validated server-side
- **Role Checks**: Admin features protected by role verification
- **Error Messages**: Sensitive information not exposed in errors

---

## Browser Compatibility

| Browser | Supported Versions |
|---------|-------------------|
| Chrome/Edge | Latest 2 versions |
| Firefox | Latest 2 versions |
| Safari | Latest 2 versions |

---

## Troubleshooting

### 401 Unauthorized
- Token expired → Re-login
- Missing `TC_AUTH_SECRET` for V2 tokens
- Invalid token format

### CORS Errors
- Check origin is allowed in `CorsFilter.java`
- Ensure credentials included in requests

### Cookie Not Set
- Ensure HTTPS in production
- Check cookie domain matches app domain
- Verify SameSite cookie settings

### CSP Errors (localhost)
- Normal on localhost due to cross-origin restrictions
- Does not affect production deployment
- Use `npm run dev` which skips iframe auth
- For full auth testing, use HTTPS with `local.topcoder-dev.com`

### SSL Certificate Issues
- **Browser warnings**: Trust the certificate using `npm run ssl:trust:mac` or `ssl:trust:linux`
- **Certificate not found**: Run `npm run ssl:generate` first
- **Port 443 permission denied**: Use `sudo` (required on macOS/Linux for port 443)

### Login Redirect Issues
- Check return URL is properly encoded
- Verify auth URL matches environment

---

## License

Copyright 2024 AI Arena. All rights reserved.
