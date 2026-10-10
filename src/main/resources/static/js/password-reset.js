let resetEmail = '';
const resetStatus = document.getElementById('reset-status');
document.getElementById('request-form').addEventListener('submit', async event => {
    event.preventDefault();
    const email = document.getElementById('email').value.trim();
    if (!/^[^@\s]+@[^@\s]+\.com$/i.test(email)) {
        resetStatus.textContent = 'Customer email must contain @ and end in .com.';
        return;
    }
    await UI.withBusy(document.getElementById('send-code'), async () => {
        try {
            const result = await API.request('/api/v1/auth/password-reset/request', {method:'POST', body:{email}});
            resetEmail = email;
            resetStatus.textContent = result.message;
            document.getElementById('reset-form').hidden = false;
        } catch (err) { resetStatus.textContent = err.message; }
    });
});
document.getElementById('reset-form').addEventListener('submit', async event => {
    event.preventDefault();
    const password = document.getElementById('new-password').value;
    const confirmPassword = document.getElementById('confirm-password').value;
    if (password !== confirmPassword) { resetStatus.textContent = 'Passwords must match.'; return; }
    await UI.withBusy(document.getElementById('reset-button'), async () => {
        try {
            const result = await API.request('/api/v1/auth/password-reset/confirm', {
                method:'POST', body:{email:resetEmail, otp:document.getElementById('otp').value.trim(), password, confirmPassword}
            });
            document.getElementById('reset-form').reset();
            document.getElementById('reset-form').hidden = true;
            resetStatus.textContent = result.message;
        } catch (err) { resetStatus.textContent = err.message; }
    });
});
