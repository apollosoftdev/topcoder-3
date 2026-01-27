/**
 * Contestant Service
 * Handles contestant registration and management with authentication integration
 *
 * Section 7.1: Contestant Registration
 * - Store member handle when authenticated user registers
 * - Use member handle instead of anonymous contestant ID
 * - Preserve anonymous registration option for testing
 */
const ContestantService = (function() {
    'use strict';

    const API_BASE = '/api/contestants';

    /**
     * Register a contestant for a competition
     * Uses authenticated member handle if available, otherwise anonymous
     *
     * @param {Object} registrationData - Registration details
     * @param {string} registrationData.competitionId - Competition to register for
     * @param {string} [registrationData.displayName] - Optional display name for anonymous users
     * @returns {Promise<Object>} Registered contestant info
     */
    const register = function(registrationData) {
        return AuthService.getToken()
            .then(function(token) {
                const memberInfo = AuthService.getMemberInfo();
                const payload = {
                    competitionId: registrationData.competitionId,
                    memberHandle: memberInfo ? memberInfo.handle : null,
                    memberId: memberInfo ? memberInfo.userId : null,
                    displayName: registrationData.displayName || (memberInfo ? memberInfo.handle : 'Anonymous'),
                    isAuthenticated: !!memberInfo,
                    registeredAt: new Date().toISOString()
                };

                const headers = {
                    'Content-Type': 'application/json'
                };

                if (token) {
                    headers['Authorization'] = 'Bearer ' + token;
                }

                return fetch(API_BASE + '/register', {
                    method: 'POST',
                    credentials: 'include',
                    headers: headers,
                    body: JSON.stringify(payload)
                });
            })
            .then(function(response) {
                if (response.ok) {
                    return response.json();
                }
                throw new Error('Registration failed: ' + response.status);
            });
    };

    /**
     * Get contestant registration status
     *
     * @param {string} competitionId - Competition ID
     * @returns {Promise<Object|null>} Registration info or null if not registered
     */
    const getRegistration = function(competitionId) {
        return AuthService.getToken()
            .then(function(token) {
                const headers = {
                    'Content-Type': 'application/json'
                };

                if (token) {
                    headers['Authorization'] = 'Bearer ' + token;
                }

                return fetch(API_BASE + '/registration/' + encodeURIComponent(competitionId), {
                    method: 'GET',
                    credentials: 'include',
                    headers: headers
                });
            })
            .then(function(response) {
                if (response.ok) {
                    return response.json();
                }
                if (response.status === 404) {
                    return null;
                }
                throw new Error('Failed to get registration');
            });
    };

    /**
     * List all registrations for current user
     *
     * @returns {Promise<Array>} List of registrations
     */
    const listRegistrations = function() {
        return AuthService.getToken()
            .then(function(token) {
                if (!token) {
                    return [];
                }

                return fetch(API_BASE + '/my-registrations', {
                    method: 'GET',
                    credentials: 'include',
                    headers: {
                        'Authorization': 'Bearer ' + token,
                        'Content-Type': 'application/json'
                    }
                });
            })
            .then(function(response) {
                if (response && response.ok) {
                    return response.json();
                }
                return [];
            });
    };

    /**
     * Unregister from a competition
     *
     * @param {string} competitionId - Competition ID
     * @returns {Promise<boolean>} Success status
     */
    const unregister = function(competitionId) {
        return AuthService.getToken()
            .then(function(token) {
                const headers = {
                    'Content-Type': 'application/json'
                };

                if (token) {
                    headers['Authorization'] = 'Bearer ' + token;
                }

                return fetch(API_BASE + '/unregister/' + encodeURIComponent(competitionId), {
                    method: 'DELETE',
                    credentials: 'include',
                    headers: headers
                });
            })
            .then(function(response) {
                return response.ok;
            });
    };

    return {
        register: register,
        getRegistration: getRegistration,
        listRegistrations: listRegistrations,
        unregister: unregister
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ContestantService;
}
