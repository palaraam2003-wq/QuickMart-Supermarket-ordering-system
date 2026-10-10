// UI helper functions for RapidMart
const UI = {
    showToast(message, type = 'info') {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.setAttribute('role', 'status');
            container.setAttribute('aria-live', 'polite');
            document.body.appendChild(container);
        }

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        const colors = { danger: 'var(--accent-danger)', success: 'var(--accent-success)', warning: 'var(--accent-warning)' };
        if (colors[type]) toast.style.borderLeftColor = colors[type];

        toast.textContent = message;
        container.appendChild(toast);

        setTimeout(() => toast.remove(), 4500);
    },

    /** Escapes a value for safe insertion into HTML markup built with template strings. */
    escape(value) {
        if (value === undefined || value === null) return '';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    },

    formatCurrency(amount) {
        if (amount === undefined || amount === null || amount === '') return 'LKR 0.00';
        return 'LKR ' + Number(amount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    },

    formatDateTime(value) {
        if (!value) return '';
        const d = new Date(value);
        return isNaN(d) ? String(value) : d.toLocaleString();
    },

    formatDate(value) {
        if (!value) return '';
        const d = new Date(value);
        return isNaN(d) ? String(value) : d.toLocaleDateString();
    },

    /** Local date in yyyy-mm-dd (avoids the UTC shift of toISOString). */
    isoDate(offsetDays = 0) {
        const d = new Date();
        d.setDate(d.getDate() + offsetDays);
        const pad = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    },

    statusBadge(status) {
        const good = ['DELIVERED', 'PAID', 'REFUNDED', 'RESPONDED', 'APPROVED', 'RECEIVED', 'CONFIRMED'];
        const bad = ['CANCELLED', 'FAILED', 'REJECTED', 'CLOSED'];
        const cls = good.includes(status) ? 'badge-success' : bad.includes(status) ? 'badge-danger' : 'badge-warning';
        return `<span class="badge ${cls}">${UI.escape(status)}</span>`;
    },

    /**
     * Previous/Next controls for a Spring Data page response. Each button carries `data-<attr>` with the
     * zero-based page it opens; returns '' when everything fits on one page.
     */
    pager(page, attr) {
        if (!page || page.totalPages <= 1) return '';
        return `<div class="row mt-1" style="justify-content: center;">
                <button type="button" class="btn btn-secondary btn-sm" data-${attr}="${page.number - 1}" ${page.first ? 'disabled' : ''}>← Previous</button>
                <span class="muted small">Page ${page.number + 1} of ${page.totalPages}</span>
                <button type="button" class="btn btn-secondary btn-sm" data-${attr}="${page.number + 1}" ${page.last ? 'disabled' : ''}>Next →</button>
            </div>`;
    },

    /** Disables a button while an async action runs, then restores it. */
    async withBusy(button, action) {
        if (!button) return action();
        if (button.disabled) return;
        const label = button.textContent;
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        button.textContent = 'Please wait…';
        try {
            return await action();
        } finally {
            button.disabled = false;
            button.removeAttribute('aria-busy');
            button.textContent = label;
        }
    },

    // Background refreshes keep the current data on screen: no "Loading…" flash, and a transient failure
    // does not replace a table that was already showing.
    setLoading(container, text = 'Loading…') {
        if (UI.backgroundRefresh) return;
        if (container) container.innerHTML = `<p class="muted">${UI.escape(text)}</p>`;
    },

    setError(container, text = 'Could not load data.') {
        if (UI.backgroundRefresh) return;
        if (container) container.innerHTML = `<p class="text-danger">${UI.escape(text)}</p>`;
    },

    /** True while UI.liveRefresh is re-running a page's loaders in the background. */
    backgroundRefresh: false,

    /**
     * Re-runs `refresh` periodically so the page shows changes made by other users (or directly in the
     * database) without a manual reload. A tick is skipped while the tab is hidden, a dialog is open, or
     * the user is typing, so re-rendered tables never wipe unsaved input.
     */
    liveRefresh(refresh, { intervalMs = 15000, idleAfterInputMs = 30000 } = {}) {
        let inFlight = false;
        let lastInputAt = 0;
        document.addEventListener('input', () => { lastInputAt = Date.now(); }, true);

        const isTyping = () => {
            const el = document.activeElement;
            return Boolean(el && el.matches(
                'textarea, [contenteditable="true"], input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"])'
            ));
        };

        // Row-level controls (e.g. a quantity or driver picker inside a table) the user changed but hasn't submitted.
        const hasUnsavedRowInput = () => Array.from(document.querySelectorAll('table input, table select, table textarea'))
            .some(el => {
                if (el.type === 'checkbox' || el.type === 'radio') return el.checked !== el.defaultChecked;
                if (el.tagName === 'SELECT') {
                    const defaultIndex = Array.from(el.options).findIndex(o => o.defaultSelected);
                    return el.options.length > 0 && el.selectedIndex !== Math.max(defaultIndex, 0);
                }
                return el.value !== el.defaultValue;
            });

        const tick = async () => {
            if (inFlight
                || document.visibilityState !== 'visible'
                || document.querySelector('dialog[open]')
                || isTyping()
                || hasUnsavedRowInput()
                || Date.now() - lastInputAt < idleAfterInputMs) return;
            inFlight = true;
            UI.backgroundRefresh = true;
            try {
                await refresh();
            } catch (e) {
                // Keep the current data; the next tick retries.
            } finally {
                UI.backgroundRefresh = false;
                inFlight = false;
            }
        };

        window.setInterval(tick, intervalMs);
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') tick();
        });
        window.addEventListener('online', tick);
    },

    SLOTS: [
        ['MORNING_09_12', 'Morning (09:00 - 12:00)'],
        ['AFTERNOON_12_15', 'Afternoon (12:00 - 15:00)'],
        ['EVENING_15_18', 'Evening (15:00 - 18:00)'],
        ['NIGHT_18_21', 'Night (18:00 - 21:00)']
    ],

    /** Human-readable label for a delivery slot code, e.g. MORNING_09_12 -> "Morning (09:00 - 12:00)". */
    slotLabel(code) {
        const hit = UI.SLOTS.find(([v]) => v === code);
        return hit ? hit[1] : (code || '');
    },

    slotOptions(selected) {
        const slots = UI.SLOTS;
        const known = slots.some(([v]) => v === selected);
        return (selected && !known ? `<option value="${UI.escape(selected)}" selected>${UI.escape(selected)}</option>` : '') +
            slots.map(([v, l]) => `<option value="${v}" ${v === selected ? 'selected' : ''}>${l}</option>`).join('');
    }
};

window.UI = UI;

function addPasswordControls() {
    document.querySelectorAll('input[type="password"]:not([data-password-toggle])').forEach(input => {
        input.dataset.passwordToggle = 'true';
        if (!input.id) input.id = 'password-' + crypto.randomUUID();
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-secondary btn-sm';
        button.textContent = 'Show password';
        button.setAttribute('aria-controls', input.id);
        button.setAttribute('aria-pressed', 'false');
        button.addEventListener('click', () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            button.textContent = show ? 'Hide password' : 'Show password';
            button.setAttribute('aria-pressed', String(show));
        });
        input.after(button);
    });
}
document.addEventListener('DOMContentLoaded', () => {
    addPasswordControls();
    new MutationObserver(addPasswordControls).observe(document.body, { childList: true, subtree: true });
});
