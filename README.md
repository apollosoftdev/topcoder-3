# AI Arena

AI Arena Platform with Topcoder Authentication Integration.

## Overview

AI Arena is a competition platform that integrates with Topcoder authentication for user management. Users can participate in AI challenges using their Topcoder accounts.

## Tech Stack

- **Backend**: Java 17, Jersey (JAX-RS), Maven
- **Frontend**: HTML5, Vanilla JavaScript, CSS
- **Authentication**: tc-auth-lib, JWT (HS256/RS256)

## Project Structure

```
ai-arena/
├── pom.xml                                    # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/com/terra/vibe/arena/
│   │   │   ├── api/                          # REST API endpoints
│   │   │   │   └── AuthResource.java         # Authentication endpoints
│   │   │   ├── auth/                         # Authentication components
│   │   │   │   ├── AuthenticationFilter.java # Request authentication filter
│   │   │   │   ├── JwtTokenValidator.java    # JWT token validation
│   │   │   │   ├── RoleAuthorizationFilter.java
│   │   │   │   ├── RoleRequired.java         # Role annotation
│   │   │   │   └── TokenInfo.java            # Token data model
│   │   │   └── config/                       # Configuration
│   │   │       ├── AuthConfig.java           # Auth configuration loader
│   │   │       └── CorsFilter.java           # CORS handling
│   │   ├── resources/
│   │   │   └── auth.properties               # Auth configuration
│   │   └── webapp/
│   │       ├── css/main.css                  # Styles
│   │       ├── js/auth/                      # Frontend auth modules
│   │       │   ├── auth-config.js            # Auth configuration
│   │       │   ├── auth-service.js           # Authentication service
│   │       │   └── auth-ui.js                # UI state management
│   │       ├── index.html                    # Home page
│   │       ├── arena.html                    # Competition arena
│   │       ├── admin.html                    # Admin dashboard
│   │       └── WEB-INF/web.xml
│   └── test/
│       ├── java/                             # JUnit tests
│       └── js/                               # Jest tests
```

## Setup

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Node.js 18+ (for frontend tests)

### Build

```bash
# Build the project
mvn clean package

# Run tests
mvn test

# Run frontend tests only
cd src/main/webapp
npm install
npm test
```

### Configuration

#### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ENVIRONMENT` | Environment mode (development/production) | production |
| `TC_AUTH_SECRET` | HS256 secret for V2 token validation | - |

#### Auth Properties

Edit `src/main/resources/auth.properties` for authentication settings:

```properties
# Production URLs
topcoder.auth.connector.url=https://accounts-auth0.topcoder.com
topcoder.auth.accounts.url=https://accounts.topcoder.com

# Development URLs (used when ENVIRONMENT=development)
topcoder.auth.connector.url.dev=https://accounts-auth0.topcoder-dev.com
topcoder.auth.accounts.url.dev=https://accounts.topcoder-dev.com
```

## API Reference

### Authentication Endpoints

#### GET /api/auth/status

Check current authentication status.

**Request Headers:**
- `Authorization: Bearer <token>` - JWT token
- Or `Cookie: tcjwt=<token>` - Token in cookie

**Response (200 - Authenticated):**
```json
{
  "authenticated": true,
  "memberInfo": {
    "handle": "username",
    "userId": "12345",
    "roles": ["Topcoder User"],
    "isV3Token": false
  }
}
```

**Response (401 - Not Authenticated):**
```json
{
  "authenticated": false,
  "error": "No authentication token provided"
}
```

#### GET /api/auth/member/{handle}

Get member profile by handle.

**Response (200):**
```json
{
  "handle": "username",
  "status": "active"
}
```

### Protected Endpoints

Use `@RoleRequired` annotation for role-based access control:

```java
@RoleRequired({"administrator"})
@GET
@Path("/admin/users")
public Response getUsers() {
    // Only accessible by administrators
}
```

## Authentication Flow

1. User clicks "Login" button
2. Redirected to Topcoder login (`https://accounts.topcoder.com`)
3. After successful login, redirected back with JWT cookie
4. Frontend reads token from cookie via tc-auth-lib
5. Token validated on backend for protected endpoints

### Token Formats

**V2 Token (HS256):**
```json
{
  "handle": "username",
  "userId": "12345",
  "email": "user@example.com",
  "roles": ["Topcoder User"]
}
```

**V3 Token (RS256):**
```json
{
  "https://topcoder.com/claims/handle": "username",
  "https://topcoder.com/claims/userId": "12345",
  "https://topcoder.com/claims/roles": ["Topcoder User"],
  "sub": "auth0|12345"
}
```

## Frontend Usage

### Initialize Authentication

```javascript
// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    AuthUI.init();
});
```

### Handle Auth State Changes

```javascript
AuthUI.onAuthStateChange(function(isAuthenticated, memberInfo) {
    if (isAuthenticated) {
        console.log('Welcome, ' + memberInfo.handle);
    }
});
```

### Check Admin Role

```javascript
if (AuthService.isAdmin()) {
    // Show admin features
}
```

## Local Development

1. Set environment to development:
   ```bash
   export ENVIRONMENT=development
   ```

2. Deploy to local server (e.g., Tomcat, Jetty)

3. Access at `http://localhost:8080/`

## Testing

### Backend Tests (JUnit 5)

```bash
mvn test
```

Tests cover:
- JWT token validation (V2 and V3 formats)
- Token expiration handling
- Role verification
- API endpoint responses

### Frontend Tests (Jest)

```bash
cd src/main/webapp
npm test
```

Tests cover:
- Token decoding
- Login/logout URL generation
- UI state transitions
- Auth state callbacks

## Troubleshooting

### Token Not Being Read

1. Check cookie domain matches your application domain
2. Verify CORS is configured for your origin
3. Check browser console for authentication errors

### 401 Unauthorized

1. Token may be expired - check `exp` claim
2. Token format may be unsupported
3. For HS256 tokens, ensure `TC_AUTH_SECRET` is set

### CORS Errors

Verify your origin is in the allowed list in `CorsFilter.java`:
- `*.topcoder.com`
- `localhost:*` (development)

## License

Copyright 2024 AI Arena. All rights reserved.
