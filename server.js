/**
 * Simple development server for AI Arena frontend
 * Serves static files and mocks API endpoints
 *
 * Supports HTTPS for local.topcoder-dev.com development
 */
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

// Load .env file
const envPath = path.join(__dirname, '.env');
if (fs.existsSync(envPath)) {
    const envContent = fs.readFileSync(envPath, 'utf8');
    envContent.split('\n').forEach(line => {
        line = line.trim();
        if (line && !line.startsWith('#')) {
            const [key, ...valueParts] = line.split('=');
            const value = valueParts.join('=').trim();
            if (key && value && !process.env[key]) {
                process.env[key] = value;
            }
        }
    });
    console.log('Loaded .env configuration');
}

// Configuration from environment
const HTTP_PORT = parseInt(process.env.PORT) || 8080;
const HTTPS_PORT = parseInt(process.env.HTTPS_PORT) || 443;
const USE_HTTPS = process.env.USE_HTTPS === 'true' || process.argv.includes('--https');
const WEBAPP_DIR = path.join(__dirname, 'src/main/webapp');
const SSL_DIR = path.join(__dirname, 'ssl');

// SSL certificate paths from environment
const SSL_KEY = path.join(__dirname, process.env.SSL_KEY_PATH || 'ssl/local.topcoder-dev.com.key');
const SSL_CERT = path.join(__dirname, process.env.SSL_CERT_PATH || 'ssl/local.topcoder-dev.com.crt');

// Environment variables to inject into frontend
const FRONTEND_ENV = {
    NODE_ENV: process.env.NODE_ENV || 'development',
    TC_AUTH_CONNECTOR_URL: process.env.TC_AUTH_CONNECTOR_URL || 'https://accounts-auth0.topcoder-dev.com',
    TC_AUTH_URL: process.env.TC_AUTH_URL || 'https://accounts-auth0.topcoder-dev.com',
    TC_ACCOUNTS_APP_URL: process.env.TC_ACCOUNTS_APP_URL || 'https://accounts.topcoder-dev.com',
    TC_AUTH0_CDN_URL: process.env.TC_AUTH0_CDN_URL || 'https://cdn.auth0.com',
    TC_COOKIE_NAME: process.env.TC_COOKIE_NAME || 'tcjwt',
    TC_V3_COOKIE_NAME: process.env.TC_V3_COOKIE_NAME || 'v3jwt',
    TC_REFRESH_COOKIE_NAME: process.env.TC_REFRESH_COOKIE_NAME || 'tcrft',
    TC_TOKEN_EXPIRATION_OFFSET: parseInt(process.env.TC_TOKEN_EXPIRATION_OFFSET) || 60,
    API_BASE_URL: process.env.API_BASE_URL || '/api'
};

// Script to inject environment variables into HTML
const ENV_SCRIPT = `<script>window.__ENV__ = ${JSON.stringify(FRONTEND_ENV)};</script>`;

// MIME types
const MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon'
};

// Allowed CORS origins
const ALLOWED_ORIGINS = [
    'https://local.topcoder-dev.com',
    'https://accounts.topcoder.com',
    'https://accounts-auth0.topcoder.com',
    'https://accounts.topcoder-dev.com',
    'https://accounts-auth0.topcoder-dev.com',
    'http://localhost:8080',
    'https://localhost:443'
];

// Get allowed origin for CORS
function getAllowedOrigin(req) {
    const origin = req.headers.origin;
    if (!origin) return null;

    if (ALLOWED_ORIGINS.includes(origin)) {
        return origin;
    }
    // Allow topcoder subdomains
    if (origin.endsWith('.topcoder.com') || origin.endsWith('.topcoder-dev.com')) {
        return origin;
    }
    // Allow localhost variations
    if (origin.startsWith('http://localhost') || origin.startsWith('https://localhost') ||
        origin.startsWith('http://127.0.0.1') || origin.startsWith('https://127.0.0.1')) {
        return origin;
    }
    return null;
}

// Content Security Policy for development
// Note: unsafe-inline needed for injected env script, but removing unsafe-eval
const CSP_HEADER = [
    "default-src 'self'",
    "script-src 'self' 'unsafe-inline' https://cdn.auth0.com https://*.topcoder.com https://*.topcoder-dev.com",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https:",
    "font-src 'self' https:",
    "connect-src 'self' https://cdn.auth0.com https://*.auth0.com https://*.topcoder.com https://*.topcoder-dev.com",
    "frame-src 'self' https://*.auth0.com https://*.topcoder.com https://*.topcoder-dev.com",
    "frame-ancestors 'self'"
].join('; ');

// Proxy configuration for external services
const PROXY_ROUTES = {
    '/proxy/auth0': 'cdn.auth0.com',
    '/proxy/topcoder-dev': 'accounts-auth0.topcoder-dev.com',
    '/proxy/topcoder': 'accounts-auth0.topcoder.com'
};

// Handle proxy requests to external services
function handleProxy(req, res) {
    const url = new URL(req.url, `http://localhost:${HTTP_PORT}`);

    // Find matching proxy route
    let targetHost = null;
    let targetPath = url.pathname;

    for (const [prefix, host] of Object.entries(PROXY_ROUTES)) {
        if (url.pathname.startsWith(prefix)) {
            targetHost = host;
            targetPath = url.pathname.replace(prefix, '') || '/';
            break;
        }
    }

    if (!targetHost) {
        res.writeHead(404);
        res.end('Proxy route not found');
        return;
    }

    const targetUrl = `https://${targetHost}${targetPath}${url.search}`;
    console.log(`Proxying: ${req.url} -> ${targetUrl}`);

    const proxyReq = https.request(targetUrl, {
        method: req.method,
        headers: {
            ...req.headers,
            host: targetHost
        }
    }, (proxyRes) => {
        // Set CORS headers (only for allowed origins)
        const allowedOrigin = getAllowedOrigin(req);
        if (allowedOrigin) {
            res.setHeader('Access-Control-Allow-Origin', allowedOrigin);
            res.setHeader('Access-Control-Allow-Credentials', 'true');
        }
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

        // Forward response headers (excluding problematic ones)
        Object.entries(proxyRes.headers).forEach(([key, value]) => {
            if (!['content-encoding', 'transfer-encoding', 'content-security-policy'].includes(key.toLowerCase())) {
                res.setHeader(key, value);
            }
        });

        res.writeHead(proxyRes.statusCode);
        proxyRes.pipe(res);
    });

    proxyReq.on('error', (err) => {
        console.error('Proxy error:', err.message);
        res.writeHead(502);
        res.end(JSON.stringify({ error: 'Proxy error', message: err.message }));
    });

    req.pipe(proxyReq);
}

// Mock data for API endpoints
const mockContestants = new Map();
const mockScores = [];
const mockProgress = new Map();

// Handle API requests
function handleAPI(req, res) {
    const url = new URL(req.url, `http://localhost:${HTTP_PORT}`);
    const pathname = url.pathname;

    res.setHeader('Content-Type', 'application/json');

    // Set CORS headers only for allowed origins
    const allowedOrigin = getAllowedOrigin(req);
    if (allowedOrigin) {
        res.setHeader('Access-Control-Allow-Origin', allowedOrigin);
        res.setHeader('Access-Control-Allow-Credentials', 'true');
    }
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    // Handle CORS preflight
    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    // Auth status (mock - always returns unauthenticated without real token)
    if (pathname === '/api/auth/status') {
        const authHeader = req.headers['authorization'];
        if (authHeader && authHeader.startsWith('Bearer ')) {
            // Mock authenticated response
            res.writeHead(200);
            res.end(JSON.stringify({
                authenticated: true,
                memberInfo: {
                    handle: 'testuser',
                    userId: '12345',
                    roles: ['Topcoder User'],
                    isV3Token: false
                }
            }));
        } else {
            res.writeHead(401);
            res.end(JSON.stringify({
                authenticated: false,
                error: 'No authentication token provided'
            }));
        }
        return;
    }

    // Member profile
    if (pathname.startsWith('/api/auth/member/')) {
        const handle = pathname.split('/').pop();
        // Validate handle format (alphanumeric, underscore, hyphen, 1-50 chars)
        if (!handle || !/^[a-zA-Z0-9_-]{1,50}$/.test(handle)) {
            res.writeHead(400);
            res.end(JSON.stringify({ error: 'Invalid handle format' }));
            return;
        }
        res.writeHead(200);
        res.end(JSON.stringify({
            handle: handle,
            status: 'active'
        }));
        return;
    }

    // Contestants
    if (pathname === '/api/contestants/register' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            const data = JSON.parse(body);
            const id = Date.now().toString();
            const contestant = { id, ...data, registeredAt: new Date().toISOString() };
            mockContestants.set(id, contestant);
            res.writeHead(200);
            res.end(JSON.stringify(contestant));
        });
        return;
    }

    // Scores leaderboard
    if (pathname.startsWith('/api/scores/leaderboard/')) {
        const competitionId = pathname.split('/').pop();
        // Validate competitionId format (alphanumeric, underscore, hyphen, 1-50 chars)
        if (!competitionId || !/^[a-zA-Z0-9_-]{1,50}$/.test(competitionId)) {
            res.writeHead(400);
            res.end(JSON.stringify({ error: 'Invalid competition ID format' }));
            return;
        }
        const entries = mockScores
            .filter(s => s.competitionId === competitionId)
            .sort((a, b) => b.score - a.score)
            .slice(0, 50)
            .map((s, i) => ({ rank: i + 1, ...s }));
        res.writeHead(200);
        res.end(JSON.stringify({ competitionId, entries }));
        return;
    }

    // Submit score
    if (pathname === '/api/scores/submit' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            const data = JSON.parse(body);
            const score = { id: Date.now().toString(), ...data, submittedAt: new Date().toISOString() };
            mockScores.push(score);
            res.writeHead(200);
            res.end(JSON.stringify(score));
        });
        return;
    }

    // Default: 404
    res.writeHead(404);
    res.end(JSON.stringify({ error: 'Not found' }));
}

// Handle static file requests
function handleStatic(req, res) {
    // Sanitize URL to prevent path traversal attacks
    let requestPath = req.url === '/' ? 'index.html' : req.url;

    // Remove query string first
    requestPath = requestPath.split('?')[0];

    // Decode URL and normalize to prevent traversal
    try {
        requestPath = decodeURIComponent(requestPath);
    } catch (e) {
        res.writeHead(400);
        res.end('Bad request');
        return;
    }

    // Remove any path traversal attempts
    requestPath = requestPath.replace(/\.\./g, '').replace(/\/+/g, '/');

    // Build and validate the final path
    let filePath = path.join(WEBAPP_DIR, requestPath);

    // Security: Ensure the resolved path is within WEBAPP_DIR
    const resolvedPath = path.resolve(filePath);
    const resolvedWebappDir = path.resolve(WEBAPP_DIR);
    if (!resolvedPath.startsWith(resolvedWebappDir)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    const ext = path.extname(filePath);
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    fs.readFile(filePath, (err, content) => {
        if (err) {
            if (err.code === 'ENOENT') {
                // Try index.html for SPA routing
                fs.readFile(path.join(WEBAPP_DIR, 'index.html'), (err2, content2) => {
                    if (err2) {
                        res.writeHead(404);
                        res.end('Not found');
                    } else {
                        // Inject environment variables into HTML
                        let html = content2.toString();
                        html = html.replace('<head>', '<head>\n    ' + ENV_SCRIPT);
                        res.writeHead(200, {
                            'Content-Type': 'text/html',
                            'Content-Security-Policy': CSP_HEADER
                        });
                        res.end(html);
                    }
                });
            } else {
                res.writeHead(500);
                res.end('Server error');
            }
        } else {
            const headers = { 'Content-Type': contentType };
            // Add CSP header and inject env for HTML files
            if (ext === '.html') {
                headers['Content-Security-Policy'] = CSP_HEADER;
                // Inject environment variables into HTML
                let html = content.toString();
                html = html.replace('<head>', '<head>\n    ' + ENV_SCRIPT);
                res.writeHead(200, headers);
                res.end(html);
                return;
            }
            res.writeHead(200, headers);
            res.end(content);
        }
    });
}

// Request handler
function requestHandler(req, res) {
    console.log(`${req.method} ${req.url}`);

    // Handle CORS preflight for all routes
    if (req.method === 'OPTIONS') {
        const allowedOrigin = getAllowedOrigin(req);
        if (allowedOrigin) {
            res.setHeader('Access-Control-Allow-Origin', allowedOrigin);
            res.setHeader('Access-Control-Allow-Credentials', 'true');
        }
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
        res.writeHead(200);
        res.end();
        return;
    }

    if (req.url.startsWith('/proxy/')) {
        handleProxy(req, res);
    } else if (req.url.startsWith('/api/')) {
        handleAPI(req, res);
    } else {
        handleStatic(req, res);
    }
}

// Start server
function startServer() {
    if (USE_HTTPS) {
        // Check if SSL certificates exist
        if (!fs.existsSync(SSL_KEY) || !fs.existsSync(SSL_CERT)) {
            console.error('SSL certificates not found. Run:');
            console.error('  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \\');
            console.error('    -keyout ssl/local.topcoder-dev.com.key \\');
            console.error('    -out ssl/local.topcoder-dev.com.crt \\');
            console.error('    -subj "/CN=local.topcoder-dev.com"');
            process.exit(1);
        }

        const httpsOptions = {
            key: fs.readFileSync(SSL_KEY),
            cert: fs.readFileSync(SSL_CERT)
        };

        const server = https.createServer(httpsOptions, requestHandler);

        server.listen(HTTPS_PORT, () => {
            console.log(`
╔════════════════════════════════════════════════════════════╗
║              AI Arena Dev Server (HTTPS)                   ║
╠════════════════════════════════════════════════════════════╣
║  Server running at: https://local.topcoder-dev.com        ║
║                                                            ║
║  Pages:                                                    ║
║    - Home:  https://local.topcoder-dev.com/                ║
║    - Arena: https://local.topcoder-dev.com/arena.html      ║
║    - Admin: https://local.topcoder-dev.com/admin.html      ║
║                                                            ║
║  Note: Add to /etc/hosts:                                  ║
║    127.0.0.1    local.topcoder-dev.com                     ║
║                                                            ║
║  Trust the certificate in your browser to avoid warnings.  ║
╚════════════════════════════════════════════════════════════╝
`);
        });

        server.on('error', (err) => {
            if (err.code === 'EACCES') {
                console.error(`Port ${HTTPS_PORT} requires elevated permissions.`);
                console.error('Run with: sudo npm run dev:https');
                console.error('Or allow node to bind to low ports:');
                console.error("  sudo setcap 'cap_net_bind_service=+ep' $(which node)");
            } else {
                console.error('Server error:', err.message);
            }
            process.exit(1);
        });
    } else {
        // HTTP server for local development only
        // HTTPS is available via USE_HTTPS=true flag (see npm run dev:https)
        const server = http.createServer(requestHandler); // nosemgrep: using-http-server

        server.listen(HTTP_PORT, () => {
            console.log(`
╔════════════════════════════════════════════════════════════╗
║                    AI Arena Dev Server                     ║
╠════════════════════════════════════════════════════════════╣
║  Server running at: http://localhost:${HTTP_PORT}                  ║
║                                                            ║
║  Pages:                                                    ║
║    - Home:  http://localhost:${HTTP_PORT}/                         ║
║    - Arena: http://localhost:${HTTP_PORT}/arena.html               ║
║    - Admin: http://localhost:${HTTP_PORT}/admin.html               ║
║                                                            ║
║  For HTTPS mode (Topcoder auth), run:                      ║
║    sudo npm run dev:https                                  ║
║                                                            ║
║  Note: This is a dev server with mock APIs.                ║
║  For full functionality, use Docker or install Java/Maven. ║
╚════════════════════════════════════════════════════════════╝
`);
        });
    }
}

startServer();
