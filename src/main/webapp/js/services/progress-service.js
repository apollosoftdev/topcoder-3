/**
 * Progress Service
 * Handles permanent progress tracking with authentication integration
 *
 * Section 7.3: Permanent Progress
 * - Track user progress by member handle
 * - Store progress data for authenticated members
 * - Display progress history to authenticated users
 */
const ProgressService = (function() {
    'use strict';

    const API_BASE = '/api/progress';

    // Validation pattern for IDs (alphanumeric, underscore, hyphen)
    const ID_PATTERN = /^[a-zA-Z0-9_-]{1,100}$/;

    // In-memory cache for progress data (using Map for safer key access)
    const _progressCache = new Map();

    /**
     * Validate ID format to prevent injection
     * @param {string} id - ID to validate
     * @returns {boolean} True if valid
     */
    function isValidId(id) {
        return typeof id === 'string' && ID_PATTERN.test(id);
    }

    /**
     * Safe fetch wrapper that validates URL stays within API base
     * @param {string} path - API path (must start with API_BASE)
     * @param {Object} options - Fetch options
     * @returns {Promise<Response>}
     */
    function safeFetch(path, options) {
        // Ensure path starts with API_BASE to prevent SSRF
        if (!path.startsWith(API_BASE)) {
            return Promise.reject(new Error('Invalid API path'));
        }
        // nosemgrep: javascript-ssrf-rule-node_ssrf
        return fetch(path, options);
    }

    /**
     * Save progress for the current user
     *
     * @param {Object} progressData - Progress details
     * @param {string} progressData.competitionId - Competition ID
     * @param {string} progressData.checkpointId - Checkpoint identifier
     * @param {Object} progressData.data - Progress data to save
     * @returns {Promise<Object>} Saved progress record
     */
    const saveProgress = function(progressData) {
        // Validate IDs
        if (!isValidId(progressData.competitionId)) {
            return Promise.reject(new Error('Invalid competitionId format'));
        }
        if (!isValidId(progressData.checkpointId)) {
            return Promise.reject(new Error('Invalid checkpointId format'));
        }

        return AuthService.getToken()
            .then(function(token) {
                const memberInfo = AuthService.getMemberInfo();

                if (!memberInfo) {
                    // For anonymous users, store in local cache only
                    const key = progressData.competitionId + ':' + progressData.checkpointId;
                    const cacheEntry = {
                        data: progressData.data,
                        savedAt: new Date().toISOString()
                    };
                    _progressCache.set(key, cacheEntry);
                    return Promise.resolve(cacheEntry);
                }

                const payload = {
                    competitionId: progressData.competitionId,
                    checkpointId: progressData.checkpointId,
                    memberHandle: memberInfo.handle,
                    memberId: memberInfo.userId,
                    data: progressData.data,
                    savedAt: new Date().toISOString()
                };

                return safeFetch(API_BASE + '/save', {
                    method: 'POST',
                    credentials: 'include',
                    headers: {
                        'Authorization': 'Bearer ' + token,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(payload)
                });
            })
            .then(function(response) {
                if (response.ok) {
                    return response.json();
                }
                // If response is already the cached data (for anonymous)
                if (response.data) {
                    return response;
                }
                throw new Error('Failed to save progress');
            });
    };

    /**
     * Load progress for the current user
     *
     * @param {string} competitionId - Competition ID
     * @param {string} [checkpointId] - Optional checkpoint filter
     * @returns {Promise<Object|Array>} Progress data
     */
    const loadProgress = function(competitionId, checkpointId) {
        // Validate IDs
        if (!isValidId(competitionId)) {
            return Promise.reject(new Error('Invalid competitionId format'));
        }
        if (checkpointId && !isValidId(checkpointId)) {
            return Promise.reject(new Error('Invalid checkpointId format'));
        }

        return AuthService.getToken()
            .then(function(token) {
                const memberInfo = AuthService.getMemberInfo();

                if (!memberInfo) {
                    // For anonymous users, load from local cache
                    if (checkpointId) {
                        const key = competitionId + ':' + checkpointId;
                        return Promise.resolve(_progressCache.get(key) || null);
                    }
                    // Return all cached progress for the competition
                    const result = [];
                    const prefix = competitionId + ':';
                    _progressCache.forEach(function(value, key) {
                        if (key.startsWith(prefix)) {
                            result.push({
                                checkpointId: key.substring(prefix.length),
                                data: value.data,
                                savedAt: value.savedAt
                            });
                        }
                    });
                    return Promise.resolve(result);
                }

                let url = API_BASE + '/load/' + encodeURIComponent(competitionId);
                if (checkpointId) {
                    url += '/' + encodeURIComponent(checkpointId);
                }

                return safeFetch(url, {
                    method: 'GET',
                    credentials: 'include',
                    headers: {
                        'Authorization': 'Bearer ' + token,
                        'Content-Type': 'application/json'
                    }
                });
            })
            .then(function(response) {
                if (response.ok) {
                    return response.json();
                }
                // If response is already the cached data (for anonymous)
                if (Array.isArray(response) || response === null || response.data) {
                    return response;
                }
                if (response.status === 404) {
                    return null;
                }
                throw new Error('Failed to load progress');
            });
    };

    /**
     * Get full progress history for current user
     *
     * @returns {Promise<Array>} List of all progress records
     */
    const getProgressHistory = function() {
        return AuthService.getToken()
            .then(function(token) {
                if (!token) {
                    // Return cached progress for anonymous
                    const result = [];
                    _progressCache.forEach(function(value, key) {
                        const parts = key.split(':');
                        result.push({
                            competitionId: parts[0],
                            checkpointId: parts[1],
                            data: value.data,
                            savedAt: value.savedAt
                        });
                    });
                    return result;
                }

                return safeFetch(API_BASE + '/history', {
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
                if (Array.isArray(response)) {
                    return response;
                }
                return [];
            });
    };

    /**
     * Clear progress for a specific competition
     *
     * @param {string} competitionId - Competition ID
     * @returns {Promise<boolean>} Success status
     */
    const clearProgress = function(competitionId) {
        // Validate competitionId
        if (!isValidId(competitionId)) {
            return Promise.reject(new Error('Invalid competitionId format'));
        }

        return AuthService.getToken()
            .then(function(token) {
                const memberInfo = AuthService.getMemberInfo();

                if (!memberInfo) {
                    // For anonymous, clear from local cache
                    const prefix = competitionId + ':';
                    const keysToDelete = [];
                    _progressCache.forEach(function(value, key) {
                        if (key.startsWith(prefix)) {
                            keysToDelete.push(key);
                        }
                    });
                    keysToDelete.forEach(function(key) {
                        _progressCache.delete(key);
                    });
                    return Promise.resolve(true);
                }

                return safeFetch(API_BASE + '/clear/' + encodeURIComponent(competitionId), {
                    method: 'DELETE',
                    credentials: 'include',
                    headers: {
                        'Authorization': 'Bearer ' + token,
                        'Content-Type': 'application/json'
                    }
                });
            })
            .then(function(response) {
                if (response === true) {
                    return true;
                }
                return response.ok;
            });
    };

    /**
     * Get progress statistics for current user
     *
     * @returns {Promise<Object>} Progress statistics
     */
    const getProgressStats = function() {
        return AuthService.getToken()
            .then(function(token) {
                if (!token) {
                    // Calculate stats from cache for anonymous
                    const competitions = new Set();
                    let totalCheckpoints = 0;
                    _progressCache.forEach(function(value, key) {
                        competitions.add(key.split(':')[0]);
                        totalCheckpoints++;
                    });
                    return {
                        competitionsCount: competitions.size,
                        checkpointsCount: totalCheckpoints,
                        isAuthenticated: false
                    };
                }

                return safeFetch(API_BASE + '/stats', {
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
                if (response.competitionsCount !== undefined) {
                    return response;
                }
                return { competitionsCount: 0, checkpointsCount: 0 };
            });
    };

    /**
     * Clear all cached progress (for anonymous users or on logout)
     */
    const clearCache = function() {
        _progressCache.clear();
    };

    // Listen for logout to clear cache
    if (typeof document !== 'undefined') {
        document.addEventListener('tc-auth-logout', clearCache);
    }

    return {
        saveProgress: saveProgress,
        loadProgress: loadProgress,
        getProgressHistory: getProgressHistory,
        clearProgress: clearProgress,
        getProgressStats: getProgressStats,
        clearCache: clearCache
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ProgressService;
}
