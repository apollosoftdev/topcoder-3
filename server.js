/**
 * Simple development server for AI Arena frontend
 * Serves static files and mocks API endpoints
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;
const WEBAPP_DIR = path.join(__dirname, 'src/main/webapp');

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

// Mock data for API endpoints
const mockContestants = new Map();
const mockScores = [];
const mockProgress = new Map();

// Handle API requests
function handleAPI(req, res) {
    const url = new URL(req.url, `http://localhost:${PORT}`);
    const pathname = url.pathname;

    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Access-Control-Allow-Origin', '*');
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
    let filePath = path.join(WEBAPP_DIR, req.url === '/' ? 'index.html' : req.url);

    // Remove query string
    filePath = filePath.split('?')[0];

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
                        res.writeHead(200, { 'Content-Type': 'text/html' });
                        res.end(content2);
                    }
                });
            } else {
                res.writeHead(500);
                res.end('Server error');
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content);
        }
    });
}

// Create server
const server = http.createServer((req, res) => {
    console.log(`${req.method} ${req.url}`);

    if (req.url.startsWith('/api/')) {
        handleAPI(req, res);
    } else {
        handleStatic(req, res);
    }
});

server.listen(PORT, () => {
    console.log(`
╔════════════════════════════════════════════════════════════╗
║                    AI Arena Dev Server                     ║
╠════════════════════════════════════════════════════════════╣
║  Server running at: http://localhost:${PORT}                  ║
║                                                            ║
║  Pages:                                                    ║
║    - Home:  http://localhost:${PORT}/                         ║
║    - Arena: http://localhost:${PORT}/arena.html               ║
║    - Admin: http://localhost:${PORT}/admin.html               ║
║                                                            ║
║  Note: This is a dev server with mock APIs.                ║
║  For full functionality, use Docker or install Java/Maven. ║
╚════════════════════════════════════════════════════════════╝
`);
});
