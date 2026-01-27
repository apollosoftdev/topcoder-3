/**
 * Score Service
 * Handles score tracking and leaderboard with authentication integration
 *
 * Section 7.2: Score Tracking
 * - Track scores by member handle
 * - Display scores by member handle in leaderboards
 * - Support score history for authenticated members
 */
const ScoreService = (function() {
    'use strict';

    const API_BASE = '/api/scores';

    /**
     * Submit a score for the current user
     *
     * @param {Object} scoreData - Score details
     * @param {string} scoreData.competitionId - Competition ID
     * @param {number} scoreData.score - Score value
     * @param {Object} [scoreData.metadata] - Additional score metadata
     * @returns {Promise<Object>} Submitted score record
     */
    const submitScore = function(scoreData) {
        return AuthService.getToken()
            .then(function(token) {
                const memberInfo = AuthService.getMemberInfo();
                const payload = {
                    competitionId: scoreData.competitionId,
                    memberHandle: memberInfo ? memberInfo.handle : null,
                    memberId: memberInfo ? memberInfo.userId : null,
                    score: scoreData.score,
                    metadata: scoreData.metadata || {},
                    submittedAt: new Date().toISOString()
                };

                const headers = {
                    'Content-Type': 'application/json'
                };

                if (token) {
                    headers['Authorization'] = 'Bearer ' + token;
                }

                return fetch(API_BASE + '/submit', {
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
                throw new Error('Score submission failed: ' + response.status);
            });
    };

    /**
     * Get leaderboard for a competition
     *
     * @param {string} competitionId - Competition ID
     * @param {Object} [options] - Query options
     * @param {number} [options.limit=50] - Max results
     * @param {number} [options.offset=0] - Pagination offset
     * @returns {Promise<Object>} Leaderboard with entries
     */
    const getLeaderboard = function(competitionId, options) {
        const params = new URLSearchParams();
        params.append('limit', (options && options.limit) || 50);
        params.append('offset', (options && options.offset) || 0);

        return fetch(API_BASE + '/leaderboard/' + encodeURIComponent(competitionId) + '?' + params.toString(), {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(function(response) {
            if (response.ok) {
                return response.json();
            }
            throw new Error('Failed to get leaderboard');
        });
    };

    /**
     * Get score history for current user
     *
     * @param {string} [competitionId] - Optional competition filter
     * @returns {Promise<Array>} List of score records
     */
    const getMyScoreHistory = function(competitionId) {
        return AuthService.getToken()
            .then(function(token) {
                if (!token) {
                    return [];
                }

                let url = API_BASE + '/my-scores';
                if (competitionId) {
                    url += '?competitionId=' + encodeURIComponent(competitionId);
                }

                return fetch(url, {
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
     * Get scores for a specific member by handle
     *
     * @param {string} memberHandle - Member handle
     * @param {string} [competitionId] - Optional competition filter
     * @returns {Promise<Array>} List of score records
     */
    const getScoresByHandle = function(memberHandle, competitionId) {
        let url = API_BASE + '/by-handle/' + encodeURIComponent(memberHandle);
        if (competitionId) {
            url += '?competitionId=' + encodeURIComponent(competitionId);
        }

        return fetch(url, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(function(response) {
            if (response.ok) {
                return response.json();
            }
            if (response.status === 404) {
                return [];
            }
            throw new Error('Failed to get scores');
        });
    };

    /**
     * Get current user's rank in a competition
     *
     * @param {string} competitionId - Competition ID
     * @returns {Promise<Object|null>} Rank info or null if not ranked
     */
    const getMyRank = function(competitionId) {
        return AuthService.getToken()
            .then(function(token) {
                if (!token) {
                    return null;
                }

                return fetch(API_BASE + '/my-rank/' + encodeURIComponent(competitionId), {
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
                return null;
            });
    };

    return {
        submitScore: submitScore,
        getLeaderboard: getLeaderboard,
        getMyScoreHistory: getMyScoreHistory,
        getScoresByHandle: getScoresByHandle,
        getMyRank: getMyRank
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ScoreService;
}
