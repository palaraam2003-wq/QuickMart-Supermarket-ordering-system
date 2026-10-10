function staffLoginDestination(user) {
    const next = new URLSearchParams(window.location.search).get('next');
    if (next && next.startsWith('/') && !next.startsWith('//') && !/[\\\s]/.test(next)) {
        const destination = new URL(next, window.location.origin);
        // Resume staff pages only; never follow an external URL or return to a login page.
        if (destination.origin === window.location.origin
                && /^\/(manager|cashier|inventory|delivery-coordinator|delivery-staff|loyalty)\/[^/]+\.html$/.test(destination.pathname)) {
            return destination.pathname + destination.search + destination.hash;
        }
    }
    return Auth.homeFor(user);
}

document.getElementById('staff-login').addEventListener('submit', async event => {
    event.preventDefault();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const error = document.getElementById('login-error');
    error.textContent = '';
    if (!/^[^@\s]+@rapidmart\.local$/i.test(email)) {
        error.textContent = 'Staff email must end in @rapidmart.local.';
        return;
    }
    if (!password) {
        error.textContent = 'Enter your password.';
        return;
    }
    await UI.withBusy(document.getElementById('login-btn'), async () => {
        try {
            // Finish the initial session probe before replacing the signed-in user.
            await Auth.ready;
            const user = await Auth.login(email, password, 'staff');
            window.location.href = staffLoginDestination(user);
        } catch (err) { error.textContent = err.message; }
    });
});
