// Auth state manager. The server remains authoritative; nothing about roles is persisted in the browser.
const Auth = {
    currentUser: null,
    ready: null,

    async checkAuth() {
        try {
            const session = await API.request('/api/v1/auth/session', { silent: true });
            this.currentUser = session && session.authenticated ? session.user : null;
        } catch (e) {
            this.currentUser = null;
        }
        this.updateNav();
        return this.currentUser;
    },

    async login(email, password, portal = 'customer') {
        this.currentUser = await API.request('/api/v1/auth/login', {
            method: 'POST',
            body: { email, password, portal }
        });
        this.updateNav();
        return this.currentUser;
    },

    async logout() {
        try {
            await API.request('/api/v1/auth/logout', { method: 'POST', silent: true });
        } catch (e) { /* session may already be gone */ }
        this.currentUser = null;
        window.location.href = '/login.html';
    },

    hasRole(role) {
        return !!(this.currentUser && this.currentUser.roles.includes(role));
    },

    hasAnyRole(roles) {
        return roles.some(r => this.hasRole(r));
    },

    /** Display name for a role code. The INVENTORY_STAFF code is kept for compatibility but shown as "Inventory Officer". */
    roleLabel(role) {
        return role === 'INVENTORY_STAFF' ? 'Inventory Officer' : role;
    },

    homeFor(user) {
        const roles = (user && user.roles) || [];
        if (roles.includes('MANAGER')) return '/manager/dashboard.html';
        if (roles.includes('CASHIER')) return '/cashier/dashboard.html';
        if (roles.includes('INVENTORY_STAFF')) return '/inventory/dashboard.html';
        if (roles.includes('DELIVERY_COORDINATOR')) return '/delivery-coordinator/dashboard.html';
        if (roles.includes('DELIVERY_STAFF')) return '/delivery-staff/dashboard.html';
        if (roles.includes('LOYALTY_OFFICER')) return '/loyalty/tiers.html';
        return '/catalogue.html';
    },

    portalLinks() {
        const links = [];
        if (this.hasRole('MANAGER')) links.push(['/manager/dashboard.html', 'Manager']);
        if (this.hasAnyRole(['CASHIER', 'MANAGER'])) links.push(['/cashier/dashboard.html', 'Cashier']);
        if (this.hasAnyRole(['INVENTORY_STAFF', 'MANAGER'])) links.push(['/inventory/dashboard.html', 'Inventory']);
        if (this.hasAnyRole(['DELIVERY_COORDINATOR', 'MANAGER'])) links.push(['/delivery-coordinator/dashboard.html', 'Deliveries']);
        if (this.hasRole('DELIVERY_STAFF')) links.push(['/delivery-staff/dashboard.html', 'My Deliveries']);
        if (this.hasAnyRole(['LOYALTY_OFFICER', 'MANAGER'])) links.push(['/loyalty/tiers.html', 'Loyalty']);
        // Managers are staff only: they never see the shopping links.
        if (this.hasRole('CUSTOMER') && !this.hasRole('MANAGER')) {
            links.push(['/cart.html', 'Cart'], ['/orders.html', 'My Orders']);
        }
        return links;
    },

    updateNav() {
        const userNav = document.getElementById('user-nav');
        if (!userNav) return;
        userNav.replaceChildren();

        const addLink = (href, text, className) => {
            const a = document.createElement('a');
            a.href = href;
            a.textContent = text;
            if (className) a.className = className;
            const currentPath = window.location.pathname;
            const inventorySection = href === '/inventory/dashboard.html' && currentPath.startsWith('/inventory/');
            const loyaltySection = href === '/loyalty/tiers.html' && currentPath.startsWith('/loyalty/');
            if (currentPath === href || inventorySection || loyaltySection) a.classList.add('active');
            userNav.appendChild(a);
        };

        if (this.currentUser) {
            this.portalLinks().forEach(([href, text]) => addLink(href, text));
            addLink('/profile.html', 'Profile');

            const who = document.createElement('span');
            who.className = 'muted nav-user';
            who.textContent = this.currentUser.fullName;
            who.title = this.currentUser.roles.map(r => this.roleLabel(r)).join(', ');
            userNav.appendChild(who);

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-secondary btn-sm';
            btn.textContent = 'Logout';
            btn.addEventListener('click', () => this.logout());
            userNav.appendChild(btn);
        } else {
            addLink('/login.html', 'Customer login', 'btn btn-secondary btn-sm');
            addLink('/staff-login.html', 'Staff login', 'btn btn-secondary btn-sm');
            addLink('/register.html', 'Register', 'btn btn-primary btn-sm');
        }
    },

    /**
     * Page guard: redirects anonymous users to login and shows an access-denied panel for wrong roles.
     * Resolves to the current user when access is allowed, otherwise null.
     */
    async requireRole(roles) {
        const user = await this.ready;
        if (!user) {
            const next = encodeURIComponent(window.location.pathname + window.location.search);
            const staffPage = roles && !roles.includes('CUSTOMER');
            window.location.href = `${staffPage ? '/staff-login.html' : '/login.html'}?next=${next}`;
            return null;
        }
        if (roles && roles.length && !this.hasAnyRole(roles)) {
            const main = document.querySelector('main') || document.querySelector('.container');
            if (main) {
                main.innerHTML = `
                    <div class="glass-card access-denied">
                        <h2>Access denied</h2>
                        <p class="muted">This page requires one of these roles: ${roles.map(r => UI.escape(Auth.roleLabel(r))).join(', ')}.</p>
                        <a class="btn btn-primary" href="${Auth.homeFor(user)}">Go to my home page</a>
                    </div>`;
            }
            return null;
        }
        return user;
    }
};

window.Auth = Auth;
Auth.ready = new Promise(resolve => {
    const start = () => Auth.checkAuth().then(resolve);
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
});
