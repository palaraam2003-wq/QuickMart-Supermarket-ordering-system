// Shared controller for the split Inventory Officer pages (role INVENTORY_STAFF).
(() => {
    'use strict';

    const page = document.body.dataset.inventoryPage;
    const inventoryNav = [
        ['dashboard', '/inventory/dashboard.html', 'Dashboard'],
        ['products', '/inventory/products.html', 'Products'],
        ['categories', '/inventory/categories.html', 'Categories'],
        ['stock-levels', '/inventory/stock-levels.html', 'Stock Levels'],
        ['stock-operations', '/inventory/stock-operations.html', 'Stock Operations']
    ];

    const byId = id => document.getElementById(id);
    const pageItems = response => (response && Array.isArray(response.content) ? response.content : []);

    function renderSectionNav() {
        const nav = byId('inventory-section-nav');
        if (!nav) return;
        nav.innerHTML = inventoryNav.map(([key, href, label]) =>
            `<a href="${href}" class="${key === page ? 'active' : ''}" ${key === page ? 'aria-current="page"' : ''}>${label}</a>`
        ).join('');
    }

    const normalize = value => String(value || '').toLowerCase();
    const searchTerms = input => normalize(input.value).split(/\s+/).filter(Boolean);

    // Client-side search over a rendered table. Cells holding controls (buttons, inputs) are ignored so
    // "Edit" etc. never match. The filter is re-applied whenever live refresh re-renders the table.
    function attachTableSearch(inputId, containerId) {
        const input = byId(inputId);
        const container = byId(containerId);
        if (!input || !container) return;
        const count = byId(`${inputId}-count`);
        const apply = () => {
            const terms = searchTerms(input);
            const rows = Array.from(container.querySelectorAll('tbody tr'));
            let shown = 0;
            rows.forEach(row => {
                const text = normalize(Array.from(row.cells)
                    .filter(cell => !cell.querySelector('button, input, select'))
                    .map(cell => cell.textContent).join(' '));
                row.hidden = !terms.every(term => text.includes(term));
                if (!row.hidden) shown++;
            });
            if (count) count.textContent = terms.length && rows.length ? `Showing ${shown} of ${rows.length}` : '';
            const empty = container.querySelector('.search-empty');
            if (terms.length && rows.length && !shown) {
                const message = empty || container.appendChild(Object.assign(document.createElement('p'), { className: 'muted mt-1 search-empty' }));
                message.textContent = `No results match "${input.value.trim()}".`;
            } else if (empty) {
                empty.remove();
            }
        };
        input.addEventListener('input', apply);
        new MutationObserver(apply).observe(container, { childList: true });
    }

    // Narrows a long <select> to matching options; returns the filter so it can be re-run after repopulating.
    function attachSelectSearch(inputId, selectId) {
        const input = byId(inputId);
        const select = byId(selectId);
        const apply = () => {
            const terms = searchTerms(input);
            const options = Array.from(select.options);
            options.forEach(option => {
                const text = normalize(option.textContent);
                option.hidden = !terms.every(term => text.includes(term));
            });
            const selected = select.options[select.selectedIndex];
            if (!selected || selected.hidden) {
                const firstMatch = options.find(option => !option.hidden);
                if (firstMatch) select.value = firstMatch.value;
            }
        };
        input.addEventListener('input', apply);
        return apply;
    }

    async function fetchProducts() {
        return pageItems(await API.request('/api/v1/inventory/products?size=200'));
    }

    async function fetchRestockRequests() {
        return pageItems(await API.request('/api/v1/inventory/restock-requests?size=200'));
    }

    function movementTable(movements) {
        if (!movements.length) return '<p class="muted mt-1">No stock movements found.</p>';
        return `<table>
            <thead><tr><th>When</th><th>Product</th><th>Change</th><th>Type</th><th>Reference</th><th>By</th></tr></thead>
            <tbody>${movements.map(m => `<tr>
                <td class="small">${UI.formatDateTime(m.createdAt)}</td>
                <td><strong>${UI.escape(m.productName)}</strong><div class="muted small">${UI.escape(m.productSku)}</div></td>
                <td class="${m.quantityDelta >= 0 ? 'text-success' : 'text-danger'}"><strong>${m.quantityDelta > 0 ? '+' : ''}${m.quantityDelta}</strong></td>
                <td><span class="badge badge-info">${UI.escape(m.movementType)}</span></td>
                <td class="small muted">${UI.escape(m.referenceId || '—')}</td>
                <td class="small">${UI.escape(m.actorEmail || '—')}</td>
            </tr>`).join('')}</tbody>
        </table>`;
    }

    async function initDashboard() {
        const lowContainer = byId('dashboard-low-stock');
        const restockContainer = byId('dashboard-restocks');
        const movementContainer = byId('dashboard-movements');
        try {
            const [categories, productResponse, lowStock, requestResponse, movementResponse] = await Promise.all([
                API.request('/api/v1/inventory/categories'),
                API.request('/api/v1/inventory/products?size=200'),
                API.request('/api/v1/inventory/low-stock'),
                API.request('/api/v1/inventory/restock-requests?size=200'),
                API.request('/api/v1/inventory/movements?size=8')
            ]);
            const products = pageItems(productResponse);
            const requests = pageItems(requestResponse);
            const movements = pageItems(movementResponse);
            const pending = requests.filter(r => r.status === 'PENDING');

            byId('stat-products').textContent = products.length;
            byId('stat-categories').textContent = categories.length;
            byId('stat-low-stock').textContent = lowStock.length;
            byId('stat-pending').textContent = pending.length;

            lowContainer.innerHTML = lowStock.length ? `<table>
                <thead><tr><th>Product</th><th>Current</th><th>Minimum</th></tr></thead>
                <tbody>${lowStock.slice(0, 5).map(i => `<tr>
                    <td><strong>${UI.escape(i.productName)}</strong><div class="muted small">${UI.escape(i.productSku)}</div></td>
                    <td class="text-danger"><strong>${i.quantity}</strong></td><td>${i.minimumLevel}</td>
                </tr>`).join('')}</tbody>
            </table>` : '<p class="text-success mt-1">All stock levels are above their minimums.</p>';

            restockContainer.innerHTML = pending.length ? `<table>
                <thead><tr><th>Product</th><th>Quantity</th><th>Requested</th></tr></thead>
                <tbody>${pending.slice(0, 5).map(r => `<tr>
                    <td><strong>${UI.escape(r.productName)}</strong><div class="muted small">${UI.escape(r.productSku)}</div></td>
                    <td>${r.requestedQuantity}</td><td class="small">${UI.formatDateTime(r.createdAt)}</td>
                </tr>`).join('')}</tbody>
            </table>` : '<p class="muted mt-1">No requests are waiting for approval.</p>';

            movementContainer.innerHTML = movementTable(movements);
        } catch (error) {
            ['stat-products', 'stat-categories', 'stat-low-stock', 'stat-pending'].forEach(id => { byId(id).textContent = '—'; });
            UI.setError(lowContainer);
            UI.setError(restockContainer);
            UI.setError(movementContainer);
        }
    }

    let categories = [];

    async function loadCategoriesPage() {
        const container = byId('categories-container');
        UI.setLoading(container);
        try {
            categories = await API.request('/api/v1/inventory/categories');
            container.innerHTML = categories.length ? `<table>
                <thead><tr><th>Name</th><th>Description</th><th>Status</th><th>Action</th></tr></thead>
                <tbody>${categories.map(c => `<tr>
                    <td><strong>${UI.escape(c.name)}</strong></td>
                    <td class="muted small">${UI.escape(c.description || '')}</td>
                    <td><span class="badge ${c.active ? 'badge-success' : 'badge-danger'}">${c.active ? 'Active' : 'Inactive'}</span></td>
                    <td><button type="button" class="btn btn-secondary btn-sm" data-edit-category="${c.id}">Edit</button></td>
                </tr>`).join('')}</tbody>
            </table>` : '<p class="muted">No categories yet. Create one before adding products.</p>';
        } catch (error) {
            UI.setError(container);
        }
    }

    function openCategoryForm(category) {
        const form = byId('category-form');
        form.reset();
        form.hidden = false;
        byId('category-form-title').textContent = category ? 'Edit Category' : 'New Category';
        byId('categoryEditId').value = category ? category.id : '';
        byId('categoryName').value = category ? category.name : '';
        byId('categoryDescription').value = category ? (category.description || '') : '';
        byId('categoryActive').checked = category ? category.active : true;
        byId('categoryName').focus();
    }

    async function initCategories() {
        attachTableSearch('category-search', 'categories-container');
        byId('new-category').addEventListener('click', () => openCategoryForm(null));
        byId('category-cancel').addEventListener('click', () => { byId('category-form').hidden = true; });
        byId('categories-container').addEventListener('click', event => {
            const button = event.target.closest('[data-edit-category]');
            if (!button) return;
            openCategoryForm(categories.find(c => String(c.id) === button.dataset.editCategory));
        });
        byId('category-form').addEventListener('submit', async event => {
            event.preventDefault();
            const id = byId('categoryEditId').value;
            const body = {
                name: byId('categoryName').value.trim(),
                description: byId('categoryDescription').value.trim(),
                active: byId('categoryActive').checked
            };
            if (!body.name) return UI.showToast('Category name is required.', 'warning');
            await UI.withBusy(byId('category-save'), async () => {
                try {
                    await API.request(id ? `/api/v1/inventory/categories/${id}` : '/api/v1/inventory/categories', {
                        method: id ? 'PUT' : 'POST', body
                    });
                    UI.showToast('Category saved.', 'success');
                    byId('category-form').hidden = true;
                    await loadCategoriesPage();
                } catch (error) { /* API client shows the error. */ }
            });
        });
        await loadCategoriesPage();
        UI.liveRefresh(loadCategoriesPage);
    }

    let products = [];
    let productCategories = [];

    function categoryOptions(selectedId) {
        const visible = productCategories.filter(c => c.active || String(c.id) === String(selectedId || ''));
        if (!visible.length) return '<option value="">Create an active category first</option>';
        return visible.map(c => `<option value="${c.id}" ${String(c.id) === String(selectedId || '') ? 'selected' : ''}>${UI.escape(c.name)}${c.active ? '' : ' (inactive)'}</option>`).join('');
    }

    function openProductForm(product) {
        const form = byId('product-form');
        form.reset();
        form.hidden = false;
        byId('product-form-title').textContent = product ? 'Edit Product' : 'New Product';
        byId('productEditId').value = product ? product.id : '';
        byId('sku').value = product ? product.sku : '';
        byId('pname').value = product ? product.name : '';
        byId('price').value = product ? product.price : '';
        byId('unit').value = product ? product.unit : '';
        byId('imageUrl').value = product ? (product.imageUrl || '') : '';
        byId('description').value = product ? (product.description || '') : '';
        byId('productActive').checked = product ? product.active : true;
        byId('initial-stock-group').hidden = !!product;
        byId('categoryId').innerHTML = categoryOptions(product && product.categoryId);
        byId('sku').focus();
    }

    async function loadProductsPage() {
        const container = byId('products-table-container');
        UI.setLoading(container);
        try {
            products = await fetchProducts();
            container.innerHTML = products.length ? `<table>
                <thead><tr><th>SKU</th><th>Name</th><th>Category</th><th>Price</th><th>Stock</th><th>Status</th><th>Actions</th></tr></thead>
                <tbody>${products.map(p => `<tr>
                    <td>${UI.escape(p.sku)}</td>
                    <td><strong>${UI.escape(p.name)}</strong><div class="muted small">${UI.escape(p.unit)}</div></td>
                    <td>${UI.escape(p.categoryName)}</td><td>${UI.formatCurrency(p.price)}</td><td>${p.availableStock}</td>
                    <td><span class="badge ${p.active ? 'badge-success' : 'badge-danger'}">${p.active ? 'Active' : 'Inactive'}</span></td>
                    <td><div class="row"><button type="button" class="btn btn-secondary btn-sm" data-edit-product="${p.id}">Edit</button>
                    ${p.active ? `<button type="button" class="btn btn-danger btn-sm" data-deactivate-product="${p.id}">Deactivate</button>` : ''}</div></td>
                </tr>`).join('')}</tbody>
            </table>` : '<p class="muted">No products yet.</p>';
        } catch (error) {
            UI.setError(container);
        }
    }

    async function initProducts() {
        try {
            productCategories = await API.request('/api/v1/inventory/categories');
        } catch (error) {
            UI.showToast('Could not load product categories.', 'danger');
        }
        attachTableSearch('product-search', 'products-table-container');
        byId('new-product').addEventListener('click', () => openProductForm(null));
        byId('product-cancel').addEventListener('click', () => { byId('product-form').hidden = true; });
        byId('products-table-container').addEventListener('click', async event => {
            const editButton = event.target.closest('[data-edit-product]');
            if (editButton) return openProductForm(products.find(p => String(p.id) === editButton.dataset.editProduct));

            const deactivateButton = event.target.closest('[data-deactivate-product]');
            if (!deactivateButton || !confirm('Deactivate this product? It will remain in order history but will be hidden from customers.')) return;
            await UI.withBusy(deactivateButton, async () => {
                try {
                    await API.request(`/api/v1/inventory/products/${deactivateButton.dataset.deactivateProduct}/deactivate`, { method: 'PUT' });
                    UI.showToast('Product deactivated.', 'info');
                    await loadProductsPage();
                } catch (error) { /* API client shows the error. */ }
            });
        });
        byId('product-form').addEventListener('submit', async event => {
            event.preventDefault();
            const id = byId('productEditId').value;
            const body = {
                sku: byId('sku').value.trim(), name: byId('pname').value.trim(),
                price: parseFloat(byId('price').value), unit: byId('unit').value.trim(),
                imageUrl: byId('imageUrl').value.trim(), description: byId('description').value.trim(),
                categoryId: parseInt(byId('categoryId').value, 10), active: byId('productActive').checked,
                initialStock: parseInt(byId('initialStock').value || '0', 10)
            };
            if (!body.sku || !body.name || !body.unit) return UI.showToast('SKU, name, and unit are required.', 'warning');
            if (!(body.price > 0)) return UI.showToast('Price must be greater than zero.', 'warning');
            if (!body.categoryId) return UI.showToast('Create and select an active category first.', 'warning');
            if (!id && (!Number.isInteger(body.initialStock) || body.initialStock < 0 || body.initialStock > 1000)) return UI.showToast('Initial stock must be between 0 and 1000.', 'warning');

            await UI.withBusy(byId('product-save'), async () => {
                try {
                    await API.request(id ? `/api/v1/inventory/products/${id}` : '/api/v1/inventory/products', {
                        method: id ? 'PUT' : 'POST', body
                    });
                    UI.showToast('Product saved.', 'success');
                    byId('product-form').hidden = true;
                    await loadProductsPage();
                } catch (error) { /* API client shows the error. */ }
            });
        });
        await loadProductsPage();
        UI.liveRefresh(loadProductsPage);
    }

    let inventoryItems = [];

    async function loadStockLevels() {
        const container = byId('stock-container');
        UI.setLoading(container, 'Loading stock levels…');
        try {
            const stockProducts = await fetchProducts();
            const results = await Promise.allSettled(stockProducts.map(p => API.request(`/api/v1/inventory/products/${p.id}`)));
            inventoryItems = results.filter(r => r.status === 'fulfilled').map(r => r.value)
                .sort((a, b) => Number(a.quantity > a.minimumLevel) - Number(b.quantity > b.minimumLevel) || a.productName.localeCompare(b.productName));
            container.innerHTML = inventoryItems.length ? `<table>
                <thead><tr><th>Product</th><th>Current</th><th>Minimum</th><th>Maximum</th><th>State</th><th>Action</th></tr></thead>
                <tbody>${inventoryItems.map(i => {
                    const low = i.quantity <= i.minimumLevel;
                    const atMaximum = i.quantity >= i.maximumLevel;
                    return `<tr class="${low ? 'stock-low-row' : ''}">
                        <td><strong>${UI.escape(i.productName)}</strong><div class="muted small">${UI.escape(i.productSku)}</div></td>
                        <td class="${low ? 'text-danger' : ''}"><strong>${i.quantity}</strong></td><td>${i.minimumLevel}</td><td>${i.maximumLevel}</td>
                        <td><span class="badge ${low ? 'badge-danger' : atMaximum ? 'badge-warning' : 'badge-success'}">${low ? 'Low stock' : atMaximum ? 'At maximum' : 'Healthy'}</span></td>
                        <td><button type="button" class="btn btn-secondary btn-sm" data-edit-limits="${i.productId}">Edit Limits</button></td>
                    </tr>`;
                }).join('')}</tbody>
            </table>` : '<p class="muted">No stock records found.</p>';
        } catch (error) {
            UI.setError(container);
        }
    }

    function openLimitsForm(item) {
        byId('limits-form').hidden = false;
        byId('limitsProductId').value = item.productId;
        byId('limits-product').textContent = `${item.productName} (${item.productSku}) — current quantity: ${item.quantity}`;
        byId('minimumLevel').value = item.minimumLevel;
        byId('maximumLevel').value = item.maximumLevel;
        byId('minimumLevel').focus();
    }

    async function initStockLevels() {
        attachTableSearch('stock-search', 'stock-container');
        byId('limits-cancel').addEventListener('click', () => { byId('limits-form').hidden = true; });
        byId('stock-container').addEventListener('click', event => {
            const button = event.target.closest('[data-edit-limits]');
            if (!button) return;
            openLimitsForm(inventoryItems.find(i => String(i.productId) === button.dataset.editLimits));
        });
        byId('limits-form').addEventListener('submit', async event => {
            event.preventDefault();
            const productId = byId('limitsProductId').value;
            const minimumLevel = parseInt(byId('minimumLevel').value, 10);
            const maximumLevel = parseInt(byId('maximumLevel').value, 10);
            if (!Number.isInteger(minimumLevel) || minimumLevel < 0) return UI.showToast('Minimum level must be 0 or more.', 'warning');
            if (!Number.isInteger(maximumLevel) || maximumLevel < 1) return UI.showToast('Maximum level must be at least 1.', 'warning');
            if (minimumLevel >= maximumLevel) return UI.showToast('Minimum level must be lower than maximum level.', 'warning');
            await UI.withBusy(byId('limits-save'), async () => {
                try {
                    await API.request(`/api/v1/inventory/products/${productId}/limits`, { method: 'PUT', body: { minimumLevel, maximumLevel } });
                    UI.showToast('Stock limits updated.', 'success');
                    byId('limits-form').hidden = true;
                    await loadStockLevels();
                } catch (error) { /* API client shows the error. */ }
            });
        });
        await loadStockLevels();
        UI.liveRefresh(loadStockLevels);
    }

    // Stock operations follow separation of duties: Inventory Officers (INVENTORY_STAFF) create requests and
    // receive approved stock; Managers approve or reject on their dashboard. The server enforces this too.
    let operationProducts = [];
    let restockRequests = [];
    let isInventoryOfficer = false;
    let filterRestockProducts = () => {};

    function quantitySummary(r) {
        const parts = [`Requested ${r.requestedQuantity}`];
        if (r.approvedQuantity != null) parts.push(`Approved ${r.approvedQuantity}`);
        if (r.receivedQuantity != null) parts.push(`Received ${r.receivedQuantity}`);
        return parts.map(UI.escape).join('<br>');
    }

    function reviewSummary(r) {
        if (r.status === 'REJECTED') {
            return `${UI.escape(r.rejectedByEmail || '—')}<div class="muted">${UI.formatDateTime(r.rejectedAt)}</div>
                <div class="text-danger">Reason: ${UI.escape(r.rejectionReason || '—')}</div>`;
        }
        if (r.approvedByEmail) {
            return `${UI.escape(r.approvedByEmail)}<div class="muted">${UI.formatDateTime(r.approvedAt)}</div>`;
        }
        return '<span class="muted">Awaiting manager</span>';
    }

    function receiptSummary(r) {
        if (r.status !== 'RECEIVED') return '<span class="muted">—</span>';
        return `${UI.escape(r.receivedByEmail || '—')}<div class="muted">${UI.formatDateTime(r.receivedAt)}</div>`;
    }

    function renderRequestLog() {
        const container = byId('restock-container');
        container.innerHTML = restockRequests.length ? `<table>
            <thead><tr><th>Requested</th><th>Product</th><th>Quantities</th><th>Status</th><th>Requested By</th><th>Reviewed By</th><th>Received By</th></tr></thead>
            <tbody>${restockRequests.map(r => `<tr>
                <td class="small">${UI.formatDateTime(r.requestedAt || r.createdAt)}</td>
                <td><strong>${UI.escape(r.productName)}</strong><div class="muted small">${UI.escape(r.productSku)}</div></td>
                <td class="small">${quantitySummary(r)}</td><td>${UI.statusBadge(r.status)}</td>
                <td class="small">${UI.escape(r.requestedByEmail)}</td>
                <td class="small">${reviewSummary(r)}</td>
                <td class="small">${receiptSummary(r)}</td>
            </tr>`).join('')}</tbody>
        </table>` : '<p class="muted mt-1">No stock requests yet.</p>';
    }

    function receivableLimit(r) {
        const approved = r.approvedQuantity != null ? r.approvedQuantity : r.requestedQuantity;
        const room = r.maximumLevel != null && r.currentStock != null ? r.maximumLevel - r.currentStock : approved;
        return Math.max(0, Math.min(approved, room));
    }

    function renderReceiveStock() {
        const approved = restockRequests.filter(r => r.status === 'APPROVED');
        const container = byId('receive-container');
        container.innerHTML = approved.length ? `<table>
            <thead><tr><th>Product</th><th>Stock (max)</th><th>Approved</th><th>Approved By</th><th>Quantity Received</th><th>Action</th></tr></thead>
            <tbody>${approved.map(r => {
                const approvedQty = r.approvedQuantity != null ? r.approvedQuantity : r.requestedQuantity;
                const limit = receivableLimit(r);
                return `<tr>
                <td><strong>${UI.escape(r.productName)}</strong><div class="muted small">${UI.escape(r.productSku)}</div></td>
                <td>${r.currentStock != null ? r.currentStock : '—'} <span class="muted small">(${r.maximumLevel != null ? r.maximumLevel : '—'})</span></td>
                <td>${approvedQty}${approvedQty !== r.requestedQuantity ? `<div class="muted small">of ${r.requestedQuantity} requested</div>` : ''}</td>
                <td class="small">${UI.escape(r.approvedByEmail || '—')}<div class="muted">${UI.formatDateTime(r.approvedAt)}</div></td>
                ${isInventoryOfficer ? `
                <td><label class="sr-only" for="receive-qty-${r.id}">Quantity received for ${UI.escape(r.productName)}</label>
                    <input type="number" id="receive-qty-${r.id}" class="form-control table-action-input" min="1" max="${approvedQty}" value="${limit || approvedQty}">
                    ${limit < approvedQty ? `<div class="muted small">Room for ${limit} before maximum</div>` : ''}</td>
                <td><button type="button" class="btn btn-primary btn-sm" data-receive-id="${r.id}">Receive Stock</button></td>`
                : '<td colspan="2" class="muted small">Received by an Inventory Officer</td>'}
            </tr>`;
            }).join('')}</tbody>
        </table>` : '<p class="muted mt-1">No approved requests are awaiting receipt.</p>';
    }

    async function loadOperationProducts() {
        try {
            operationProducts = await fetchProducts();
            const restockSelect = byId('restockProduct');
            const historySelect = byId('historyProduct');
            const [selectedRestock, selectedHistory] = [restockSelect.value, historySelect.value];
            restockSelect.innerHTML = operationProducts
                .map(p => `<option value="${p.id}">${UI.escape(p.name)} (${UI.escape(p.sku)}) — ${p.availableStock} in stock</option>`)
                .join('') || '<option value="">No products available</option>';
            historySelect.innerHTML = '<option value="">All products</option>' + operationProducts
                .map(p => `<option value="${p.id}">${UI.escape(p.name)} (${UI.escape(p.sku)})</option>`).join('');
            if (selectedRestock) restockSelect.value = selectedRestock;
            historySelect.value = selectedHistory;
            filterRestockProducts();
        } catch (error) {
            if (!UI.backgroundRefresh) UI.showToast('Could not load products for stock operations.', 'danger');
        }
    }

    async function loadOperationRequests() {
        try {
            restockRequests = await fetchRestockRequests();
            renderRequestLog();
            renderReceiveStock();
        } catch (error) {
            UI.setError(byId('restock-container'));
            UI.setError(byId('receive-container'));
        }
    }

    let movementPage = 0;

    async function loadMovementHistory(productId = '', page = 0) {
        const container = byId('movements-container');
        UI.setLoading(container);
        const query = productId ? `&productId=${encodeURIComponent(productId)}` : '';
        try {
            const response = await API.request(`/api/v1/inventory/movements?size=25&page=${page}${query}`);
            movementPage = page;
            container.innerHTML = movementTable(pageItems(response)) + UI.pager(response, 'movement-page');
        } catch (error) {
            UI.setError(container);
        }
    }

    async function initStockOperations() {
        isInventoryOfficer = Auth.hasRole('INVENTORY_STAFF');
        byId('restock-form').hidden = !isInventoryOfficer;
        byId('create-request-readonly').hidden = isInventoryOfficer;
        filterRestockProducts = attachSelectSearch('restockProductSearch', 'restockProduct');
        attachTableSearch('request-search', 'restock-container');
        attachTableSearch('movement-search', 'movements-container');
        await loadOperationProducts();

        byId('restock-form').addEventListener('submit', async event => {
            event.preventDefault();
            const productId = parseInt(byId('restockProduct').value, 10);
            const requestedQuantity = parseInt(byId('restockQty').value, 10);
            if (!productId) return UI.showToast('Select a product.', 'warning');
            if (!Number.isInteger(requestedQuantity) || requestedQuantity < 1) return UI.showToast('Quantity must be at least 1.', 'warning');
            await UI.withBusy(byId('restock-btn'), async () => {
                try {
                    await API.request('/api/v1/inventory/restock-requests', { method: 'POST', body: { productId, requestedQuantity } });
                    UI.showToast('Stock request created and sent to a manager for approval.', 'success');
                    await loadOperationRequests();
                } catch (error) { /* API client shows the error. */ }
            });
        });

        byId('receive-container').addEventListener('click', async event => {
            const button = event.target.closest('[data-receive-id]');
            if (!button) return;
            const requestId = button.dataset.receiveId;
            const request = restockRequests.find(r => String(r.id) === requestId);
            if (!request) return;
            const approvedQty = request.approvedQuantity != null ? request.approvedQuantity : request.requestedQuantity;
            const quantity = parseInt(byId(`receive-qty-${requestId}`).value, 10);
            if (!Number.isInteger(quantity) || quantity < 1) return UI.showToast('Received quantity must be at least 1.', 'warning');
            if (quantity > approvedQty) return UI.showToast(`Received quantity cannot exceed the approved ${approvedQty}.`, 'warning');
            const stockLine = request.currentStock != null ? ` Stock will go from ${request.currentStock} to ${request.currentStock + quantity}.` : '';
            if (!confirm(`Receive ${quantity} unit(s) of ${request.productName} (${request.productSku})?${stockLine}\n\nThis completes the request and cannot be undone.`)) return;
            await UI.withBusy(button, async () => {
                try {
                    await API.request(`/api/v1/inventory/restock-requests/${requestId}/receive?quantityReceived=${encodeURIComponent(quantity)}`, { method: 'POST' });
                    UI.showToast('Stock received and inventory updated.', 'success');
                } catch (error) { /* API client shows the error. */ }
                // Refresh after success and after a conflict (e.g. already received elsewhere) alike.
                await Promise.all([loadOperationProducts(), loadOperationRequests(), loadMovementHistory(byId('historyProduct').value)]);
            });
        });

        byId('movements-container').addEventListener('click', event => {
            const button = event.target.closest('button[data-movement-page]');
            if (button) loadMovementHistory(byId('historyProduct').value, Number(button.dataset.movementPage));
        });

        byId('history-filter').addEventListener('submit', async event => {
            event.preventDefault();
            await UI.withBusy(byId('history-filter-btn'), () => loadMovementHistory(byId('historyProduct').value));
        });
        byId('history-clear').addEventListener('click', () => {
            byId('historyProduct').value = '';
            byId('movement-search').value = '';
            loadMovementHistory();
        });

        await Promise.all([loadOperationRequests(), loadMovementHistory()]);
        UI.liveRefresh(() => Promise.all([loadOperationRequests(), loadMovementHistory(byId('historyProduct').value, movementPage)]));
    }

    document.addEventListener('DOMContentLoaded', async () => {
        if (!await Auth.requireRole(['INVENTORY_STAFF', 'MANAGER'])) return;
        renderSectionNav();
        const initializers = {
            dashboard: async () => {
                await initDashboard();
                UI.liveRefresh(initDashboard);
            },
            categories: initCategories,
            products: initProducts,
            'stock-levels': initStockLevels,
            'stock-operations': initStockOperations
        };
        if (initializers[page]) await initializers[page]();
    });
})();
