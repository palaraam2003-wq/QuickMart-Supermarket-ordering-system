// RapidMart API client: session cookies, CSRF header on state-changing requests, consistent error handling
class ApiError extends Error {
    constructor(message, status, data) {
        super(message);
        this.status = status;
        this.data = data;
    }
}

const API = {
    csrfHeaderName: 'X-XSRF-TOKEN',

    readCsrfCookie() {
        const match = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
        return match ? decodeURIComponent(match.substring('XSRF-TOKEN='.length)) : null;
    },

    async csrfToken() {
        let token = this.readCsrfCookie();
        if (!token) {
            try {
                const res = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
                if (res.ok) {
                    const data = await res.json();
                    if (data.headerName) this.csrfHeaderName = data.headerName;
                    token = this.readCsrfCookie() || data.token;
                }
            } catch (e) {
                console.warn('CSRF token initialization failed:', e);
            }
        }
        return token;
    },

    /**
     * options: { method, body, silent }
     *  - body objects are sent as JSON
     *  - silent: do not show error toasts or redirect on 401 (used for background auth checks)
     */
    async request(url, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        // Background live-refresh reads fail quietly instead of toasting or redirecting every tick.
        const backgroundRead = method === 'GET' && Boolean(window.UI && window.UI.backgroundRefresh);
        const { silent = backgroundRead, ...fetchOptions } = options;
        fetchOptions.headers = { Accept: 'application/json', ...(fetchOptions.headers || {}) };
        fetchOptions.credentials = 'same-origin';

        if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
            const token = await this.csrfToken();
            if (token) fetchOptions.headers[this.csrfHeaderName] = token;
        }

        if (fetchOptions.body && typeof fetchOptions.body === 'object' && !(fetchOptions.body instanceof FormData)) {
            fetchOptions.headers['Content-Type'] = 'application/json';
            fetchOptions.body = JSON.stringify(fetchOptions.body);
        }

        let response;
        try {
            response = await fetch(url, fetchOptions);
        } catch (networkError) {
            if (!silent && window.UI) window.UI.showToast('Cannot reach the server. Check your connection.', 'danger');
            throw new ApiError('Network error', 0, null);
        }

        if (response.status === 204) {
            return null;
        }

        const contentType = response.headers.get('Content-Type') || '';
        const data = contentType.includes('json') ? await response.json().catch(() => null) : await response.text();

        if (!response.ok) {
            let message = (data && data.message) || 'An error occurred. Please try again.';
            if (data && data.fieldErrors) {
                message += ' ' + Object.values(data.fieldErrors).join(' ');
            }

            if (!silent) {
                if (response.status === 401 && !url.startsWith('/api/v1/auth/login')) {
                    const next = encodeURIComponent(window.location.pathname + window.location.search);
                    if (window.UI) window.UI.showToast('Please sign in to continue.', 'warning');
                    const staffPage = /^\/(cashier|manager|inventory|delivery-coordinator|delivery-staff|loyalty)\//.test(window.location.pathname);
                    setTimeout(() => { window.location.href = `${staffPage ? '/staff-login.html' : '/login.html'}?next=${next}`; }, 800);
                } else if (response.status === 403 && (!data || data.errorCode === 'FORBIDDEN')) {
                    if (window.UI) window.UI.showToast('Access denied: your role cannot perform this action.', 'danger');
                } else if (window.UI) {
                    window.UI.showToast(message, 'danger');
                }
            }
            throw new ApiError(message, response.status, data);
        }

        return data;
    }
};

window.API = API;
window.ApiError = ApiError;
