# AI Arena

AI Arena competition platform with Topcoder authentication integration.

## Tech Stack

- **Backend:** Java 17, Jersey (JAX-RS), Maven
- **Frontend:** HTML5, Vanilla JavaScript, CSS
- **Authentication:** tc-auth-lib, JWT (HS256/RS256)

## Quick Start

### Using Docker (Recommended)

```bash
npm run docker:build
npm run docker:run
```

### Using npm

```bash
# Development (mock APIs)
npm run dev

# Production (requires Java 17 + Maven)
npm run build
npm start
```

### Access

- **Home:** http://localhost:8080/
- **Arena:** http://localhost:8080/arena.html
- **Admin:** http://localhost:8080/admin.html

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ENVIRONMENT` | `development` or `production` | `production` |
| `TC_AUTH_SECRET` | HS256 secret for V2 tokens (optional) | - |

> **Note:** `TC_AUTH_SECRET` is only needed for V2 (HS256) tokens. V3 (RS256) tokens use public JWKS - no secret required. For development, you can skip this.

### Development vs Production

| Setting | Development | Production |
|---------|-------------|------------|
| Auth URL | `accounts-auth0.topcoder-dev.com` | `accounts-auth0.topcoder.com` |
| JWKS URL | `topcoder-dev.auth0.com` | `topcoder.auth0.com` |

Configuration file: `src/main/resources/auth.properties`

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/status` | Check authentication status |
| GET | `/api/auth/member/{handle}` | Get member profile |

### Contestants

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/contestants/register` | Register for competition |
| GET | `/api/contestants/registration/{competitionId}` | Get registration status |
| DELETE | `/api/contestants/unregister/{competitionId}` | Unregister |

### Scores

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/scores/submit` | Submit a score |
| GET | `/api/scores/leaderboard/{competitionId}` | Get leaderboard |
| GET | `/api/scores/my-scores` | Get user's score history |
| GET | `/api/scores/my-rank/{competitionId}` | Get user's rank |

### Progress

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/progress/save` | Save progress checkpoint |
| GET | `/api/progress/load/{competitionId}` | Load progress |
| GET | `/api/progress/history` | Get progress history |
| DELETE | `/api/progress/clear/{competitionId}` | Clear progress |

### Response Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 401 | Not authenticated |
| 403 | Forbidden (insufficient permissions) |
| 404 | Not found |

## Authentication Flow

1. User clicks **Login** → Redirected to Topcoder auth
2. User logs in → Redirected back with JWT cookie (`tcjwt`)
3. Frontend reads token → Displays member handle
4. API requests include token → Backend validates JWT

### Token Formats

**V2 (HS256):** Direct claims
```json
{ "handle": "user", "userId": "123", "roles": ["Topcoder User"] }
```

**V3 (RS256):** Namespaced claims
```json
{ "https://topcoder.com/claims/handle": "user", "sub": "auth0|123" }
```

## Testing

```bash
# All tests
npm test

# Backend only (JUnit)
npm run test:backend

# Frontend only (Jest)
npm run test:frontend
```

## NPM Scripts

| Script | Description |
|--------|-------------|
| `npm run dev` | Start dev server with mock APIs |
| `npm run build` | Build with Maven |
| `npm start` | Run production server |
| `npm test` | Run all tests |
| `npm run docker:build` | Build Docker image |
| `npm run docker:run` | Run Docker container |
| `npm run docker:stop` | Stop Docker container |

## Troubleshooting

### 401 Unauthorized
- Token expired → Re-login
- Missing `TC_AUTH_SECRET` for V2 tokens

### CORS Errors
- Check origin is allowed in `CorsFilter.java`
- Allowed: `*.topcoder.com`, `localhost:*`

### Cookie Not Set
- Ensure HTTPS in production
- Check cookie domain matches app domain

## Project Structure

```
src/main/
├── java/com/terra/vibe/arena/
│   ├── api/          # REST endpoints
│   ├── auth/         # JWT validation, filters
│   ├── config/       # Auth config, CORS
│   ├── model/        # Data models
│   └── service/      # Business logic
├── resources/
│   └── auth.properties
└── webapp/
    ├── js/auth/      # Frontend auth
    ├── css/          # Styles
    └── *.html        # Pages
```

## License

Copyright 2024 AI Arena. All rights reserved.
