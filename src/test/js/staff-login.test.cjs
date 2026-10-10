const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const staticRoot = resolve(__dirname, '../../main/resources/static');
const html = readFileSync(resolve(staticRoot, 'staff-login.html'), 'utf8');
const script = readFileSync(resolve(staticRoot, 'js/staff-login.js'), 'utf8');
const uiScript = readFileSync(resolve(staticRoot, 'js/ui.js'), 'utf8');

function loginPage({ next = '', login, ready = Promise.resolve(null) } = {}) {
    const elements = Object.fromEntries(['staff-login', 'email', 'password', 'login-btn', 'login-error'].map(id => [id, {
        value: '', textContent: '', disabled: false,
        addEventListener(type, handler) { this[type] = handler; },
        setAttribute() {}, removeAttribute() {}
    }]));
    elements.email.value = 'manager@rapidmart.local';
    elements.password.value = 'test-password';
    elements['login-btn'].textContent = 'Sign in';
    const calls = [];
    const context = vm.createContext({
        URL, URLSearchParams,
        window: { location: { origin: 'http://localhost:8080', search: '?next=' + encodeURIComponent(next), href: '' } },
        document: { getElementById: id => elements[id], addEventListener() {} },
        Auth: {
            ready,
            async login(...args) {
                calls.push(args);
                return login ? login(...args) : { roles: ['MANAGER'] };
            },
            homeFor: () => '/manager/dashboard.html'
        }
    });
    vm.runInContext(uiScript, context);
    vm.runInContext(script, context);
    return {
        elements, calls, location: context.window.location,
        submit: () => elements['staff-login'].submit({ preventDefault() {} })
    };
}

test('staff login form owns both credential fields and the submit button', () => {
    const form = html.match(/<form\b[^>]*\bid="staff-login"[^>]*>([\s\S]*?)<\/form>/);
    assert.ok(form, 'The opening form tag must not be removed with the help text');
    assert.match(form[1], /<input\b[^>]*\bid="email"/);
    assert.match(form[1], /<input\b[^>]*\bid="password"/);
    assert.match(form[1], /<button\b[^>]*\bid="login-btn"[^>]*\btype="submit"/);
    assert.ok(html.indexOf('/js/staff-login.js') > html.indexOf('</form>'));
});

test('sign in submits trimmed credentials to the staff portal and opens its home', async () => {
    const page = loginPage();
    page.elements.email.value = '  manager@rapidmart.local  ';
    await page.submit();
    assert.deepEqual(page.calls, [['manager@rapidmart.local', 'test-password', 'staff']]);
    assert.equal(page.location.href, '/manager/dashboard.html');
});

test('sign in resumes the requested staff page, including its query and fragment', async () => {
    const next = '/inventory/products.html?categoryId=3#products';
    const page = loginPage({ next });
    await page.submit();
    assert.equal(page.location.href, next);
});

test('external URLs and login-loop destinations fall back to the staff home', async () => {
    for (const next of ['https://example.com/', '//example.com/manager/dashboard.html', '/\\example.com/manager/dashboard.html', '/\t/%', '/staff-login.html', '/login.html', '/api/v1/auth/logout']) {
        const page = loginPage({ next });
        await page.submit();
        assert.equal(page.location.href, '/manager/dashboard.html', next);
    }
});

test('invalid staff email and empty password do not submit credentials', async () => {
    const page = loginPage();
    page.elements.email.value = 'customer@example.com';
    await page.submit();
    assert.match(page.elements['login-error'].textContent, /@rapidmart\.local/);
    page.elements.email.value = 'manager@rapidmart.local';
    page.elements.password.value = '';
    await page.submit();
    assert.match(page.elements['login-error'].textContent, /password/);
    assert.equal(page.calls.length, 0);
});

test('failed sign in shows the error and restores the button for a retry', async () => {
    let fail = true;
    const page = loginPage({ login: async () => {
        if (fail) throw new Error('Invalid email or password.');
        return { roles: ['MANAGER'] };
    } });
    await page.submit();
    assert.equal(page.elements['login-error'].textContent, 'Invalid email or password.');
    assert.equal(page.location.href, '');
    assert.equal(page.elements['login-btn'].disabled, false);
    assert.equal(page.elements['login-btn'].textContent, 'Sign in');
    fail = false;
    await page.submit();
    assert.equal(page.elements['login-error'].textContent, '');
    assert.equal(page.location.href, '/manager/dashboard.html');
});

test('waits for the session probe and prevents duplicate sign-in requests', async () => {
    let finishProbe;
    const ready = new Promise(resolve => { finishProbe = resolve; });
    const page = loginPage({ ready });
    const pending = page.submit();
    assert.equal(page.elements['login-btn'].disabled, true);
    assert.equal(page.calls.length, 0);
    await page.submit();
    finishProbe(null);
    await pending;
    assert.equal(page.calls.length, 1);
    assert.equal(page.location.href, '/manager/dashboard.html');
});
