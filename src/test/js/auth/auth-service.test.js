/**
 * Unit tests for AuthService
 */

describe('AuthService', () => {
    let AuthService;

    // Sample JWT tokens for testing (base64 encoded)
    const createMockJWT = (payload, exp = Math.floor(Date.now() / 1000) + 3600) => {
        const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
        const payloadWithExp = { ...payload, exp };
        const payloadBase64 = btoa(JSON.stringify(payloadWithExp));
        return `${header}.${payloadBase64}.signature`;
    };

    const mockV2Token = createMockJWT({
        handle: 'testuser',
        userId: '12345',
        email: 'test@example.com',
        roles: ['Topcoder User']
    });

    const mockV3Token = createMockJWT({
        'https://topcoder.com/claims/handle': 'testuser',
        'https://topcoder.com/claims/userId': '12345',
        'https://topcoder.com/claims/email': 'test@example.com',
        'https://topcoder.com/claims/roles': ['Topcoder User'],
        sub: '12345'
    });

    const mockAdminToken = createMockJWT({
        handle: 'adminuser',
        userId: '99999',
        email: 'admin@example.com',
        roles: ['Topcoder User', 'administrator']
    });

    const expiredToken = createMockJWT({
        handle: 'expired',
        userId: '00000'
    }, Math.floor(Date.now() / 1000) - 3600); // Expired 1 hour ago

    beforeEach(() => {
        // Reset module cache for fresh load
        global.resetModules();

        // Clear cookies
        global.clearCookies();

        // Reset mocks
        jest.clearAllMocks();
        global.fetch.mockReset();

        // Create a fresh iframe mock
        document.body.innerHTML = `
            <iframe id="tc-accounts-iframe" style="display: none;"></iframe>
            <div id="auth-container"></div>
        `;

        // Load AuthService module
        global.loadModule('js/auth/auth-service.js');
        AuthService = global.AuthService;
    });

    describe('decodeToken', () => {
        test('should decode V2 token correctly', () => {
            const decoded = AuthService.decodeToken(mockV2Token);

            expect(decoded).toBeTruthy();
            expect(decoded.handle).toBe('testuser');
            expect(decoded.userId).toBe('12345');
            expect(decoded.roles).toEqual(['Topcoder User']);
        });

        test('should decode V3 token and extract namespaced claims', () => {
            const decoded = AuthService.decodeToken(mockV3Token);

            expect(decoded).toBeTruthy();
            // After tc-auth-lib style processing, handle should be at top level
            expect(decoded.handle).toBe('testuser');
            expect(decoded.sub).toBe('12345');
        });

        test('should return null for invalid token', () => {
            const decoded = AuthService.decodeToken('invalid.token');
            expect(decoded).toBeNull();
        });

        test('should return null for null token', () => {
            const decoded = AuthService.decodeToken(null);
            expect(decoded).toBeNull();
        });

        test('should return null for empty token', () => {
            const decoded = AuthService.decodeToken('');
            expect(decoded).toBeNull();
        });
    });

    describe('isTokenExpired', () => {
        test('should return false for valid token', () => {
            const decoded = AuthService.decodeToken(mockV2Token);
            expect(AuthService.isTokenExpired(decoded)).toBe(false);
        });

        test('should return true for expired token', () => {
            const decoded = AuthService.decodeToken(expiredToken);
            expect(AuthService.isTokenExpired(decoded)).toBe(true);
        });

        test('should return false for token without expiration (tc-auth-lib behavior)', () => {
            const tokenNoExp = createMockJWT({ handle: 'test' });
            // Remove exp from the token by creating one without it
            const noExpPayload = btoa(JSON.stringify({ handle: 'test' }));
            const noExpToken = `${btoa(JSON.stringify({ alg: 'HS256' }))}.${noExpPayload}.sig`;
            const decoded = AuthService.decodeToken(noExpToken);
            expect(AuthService.isTokenExpired(decoded)).toBe(false);
        });
    });

    describe('generateLoginUrl', () => {
        test('should generate login URL with current URL', () => {
            const loginUrl = AuthService.generateLoginUrl();

            expect(loginUrl).toContain(AuthConfig.AUTH_CONNECTOR_URL);
            expect(loginUrl).toContain('retUrl=');
        });

        test('should generate login URL with custom return URL', () => {
            const customUrl = 'http://example.com/custom';
            const loginUrl = AuthService.generateLoginUrl(customUrl);

            expect(loginUrl).toContain(AuthConfig.AUTH_CONNECTOR_URL);
            expect(loginUrl).toContain(encodeURIComponent(customUrl));
        });
    });

    describe('generateLogoutUrl', () => {
        test('should generate logout URL with logout parameter', () => {
            const logoutUrl = AuthService.generateLogoutUrl();

            expect(logoutUrl).toContain(AuthConfig.AUTH_CONNECTOR_URL);
            expect(logoutUrl).toContain('logout=true');
            expect(logoutUrl).toContain('retUrl=');
        });

        test('should generate logout URL with custom return URL', () => {
            const customUrl = 'http://example.com/after-logout';
            const logoutUrl = AuthService.generateLogoutUrl(customUrl);

            expect(logoutUrl).toContain(encodeURIComponent(customUrl));
        });
    });

    describe('isAuthenticated', () => {
        test('should return false when not initialized', () => {
            expect(AuthService.isAuthenticated()).toBe(false);
        });
    });

    describe('getMemberHandle', () => {
        test('should return null when not authenticated', () => {
            expect(AuthService.getMemberHandle()).toBeNull();
        });
    });

    describe('getMemberInfo', () => {
        test('should return null when not authenticated', () => {
            expect(AuthService.getMemberInfo()).toBeNull();
        });
    });

    describe('hasRole', () => {
        test('should return false when not authenticated', () => {
            expect(AuthService.hasRole('administrator')).toBe(false);
        });
    });

    describe('isAdmin', () => {
        test('should return false when not authenticated', () => {
            expect(AuthService.isAdmin()).toBe(false);
        });
    });

    describe('getToken', () => {
        test('should return token from cookie', async () => {
            document.cookie = `tcjwt=${mockV2Token}`;

            const token = await AuthService.getToken();

            expect(token).toBe(mockV2Token);
        });

        test('should return null when no cookie present', async () => {
            const token = await AuthService.getToken();

            expect(token).toBeNull();
        });
    });

    describe('getFreshToken', () => {
        test('should return valid token from cookie', async () => {
            document.cookie = `tcjwt=${mockV2Token}`;

            const token = await AuthService.getFreshToken();

            expect(token).toBe(mockV2Token);
        });

        test('should return null for expired token', async () => {
            document.cookie = `tcjwt=${expiredToken}`;

            const token = await AuthService.getFreshToken();

            expect(token).toBeNull();
        });
    });

    describe('verifyWithBackend', () => {
        test('should return authenticated false when no token', async () => {
            global.fetch.mockResolvedValueOnce({
                ok: true,
                json: () => Promise.resolve({ authenticated: false })
            });

            const result = await AuthService.verifyWithBackend();

            expect(result.authenticated).toBe(false);
        });

        test('should call backend API with token', async () => {
            document.cookie = `tcjwt=${mockV2Token}`;

            global.fetch.mockResolvedValueOnce({
                ok: true,
                json: () => Promise.resolve({
                    authenticated: true,
                    memberInfo: {
                        handle: 'testuser',
                        userId: '12345'
                    }
                })
            });

            const result = await AuthService.verifyWithBackend();

            expect(fetch).toHaveBeenCalledWith(
                AuthConfig.getAuthStatusUrl(),
                expect.objectContaining({
                    method: 'GET',
                    credentials: 'include',
                    headers: expect.objectContaining({
                        'Authorization': expect.stringContaining('Bearer')
                    })
                })
            );
        });
    });

    describe('getMemberProfile', () => {
        test('should fetch member profile by handle', async () => {
            global.fetch.mockResolvedValueOnce({
                ok: true,
                json: () => Promise.resolve({
                    handle: 'testuser',
                    status: 'active'
                })
            });

            const profile = await AuthService.getMemberProfile('testuser');

            expect(profile.handle).toBe('testuser');
            expect(fetch).toHaveBeenCalledWith(
                AuthConfig.getMemberProfileUrl('testuser'),
                expect.any(Object)
            );
        });

        test('should reject for non-existent member', async () => {
            global.fetch.mockResolvedValueOnce({
                ok: false,
                status: 404
            });

            await expect(AuthService.getMemberProfile('nonexistent'))
                .rejects.toMatchObject({
                    type: AuthService.ErrorTypes.UNAUTHORIZED
                });
        });
    });

    describe('login', () => {
        test('should redirect to login URL', () => {
            AuthService.login();

            expect(window.location.href).toContain(AuthConfig.AUTH_CONNECTOR_URL);
        });
    });

    describe('logout', () => {
        test('should redirect to logout URL', () => {
            AuthService.logout();

            expect(window.location.href).toContain('logout=true');
        });
    });

    describe('Events', () => {
        test('should expose authentication events', () => {
            expect(AuthService.Events).toBeDefined();
            expect(AuthService.Events.AUTH_SUCCESS).toBe('tc-auth-success');
            expect(AuthService.Events.AUTH_FAILURE).toBe('tc-auth-failure');
            expect(AuthService.Events.AUTH_LOGOUT).toBe('tc-auth-logout');
            expect(AuthService.Events.AUTH_STATE_CHANGE).toBe('tc-auth-state-change');
            expect(AuthService.Events.TOKEN_REFRESHED).toBe('tc-auth-token-refreshed');
        });
    });
});
