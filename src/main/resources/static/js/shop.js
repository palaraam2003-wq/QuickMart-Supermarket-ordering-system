// Customer storefront helpers shared by the landing page and the catalogue: product cards and add-to-cart.
const Shop = {
    LOW_STOCK: 10,

    /** Emoji shown for a category chip and for products that have no photo yet. */
    categoryIcon(name) {
        const n = (name || '').toLowerCase();
        if (n.includes('fruit') || n.includes('veg')) return '🥕';
        if (n.includes('dairy') || n.includes('egg')) return '🥛';
        if (n.includes('bak')) return '🍞';
        if (n.includes('rice') || n.includes('grain')) return '🌾';
        if (n.includes('bever') || n.includes('drink')) return '🧃';
        if (n.includes('house') || n.includes('clean')) return '🧴';
        return '🛒';
    },

    card(p) {
        const inStock = p.availableStock > 0;
        const flag = !inStock ? '<span class="product-flag is-out">Out of stock</span>'
            : p.availableStock <= Shop.LOW_STOCK ? `<span class="product-flag is-low">Only ${p.availableStock} left</span>` : '';
        // The placeholder sits under the photo; a missing or broken photo removes itself and reveals it.
        const media = `<span class="product-placeholder" aria-hidden="true">${Shop.categoryIcon(p.categoryName)}</span>`
            + (p.imageUrl ? `<img src="${UI.escape(p.imageUrl)}" alt="${UI.escape(p.name)}" loading="lazy" onerror="this.remove()">` : '');
        return `
            <article class="product-card" data-product="${p.id}">
                <div class="product-media">${flag}${media}</div>
                <div class="product-meta">
                    <span>${UI.escape(p.categoryName || '')}</span>
                    <span class="${inStock ? 'text-success' : 'text-danger'}">${inStock ? `In stock: ${p.availableStock}` : 'Unavailable'}</span>
                </div>
                <h3 class="product-name" title="${UI.escape(p.description || p.name)}">${UI.escape(p.name)}</h3>
                <div class="product-price">${UI.formatCurrency(p.price)} <span class="product-unit">/ ${UI.escape(p.unit)}</span></div>
                <div class="product-actions">
                    <div class="qty-stepper">
                        <button type="button" data-step="-1" aria-label="Decrease quantity of ${UI.escape(p.name)}" ${inStock ? '' : 'disabled'}>&minus;</button>
                        <label class="sr-only" for="qty-${p.id}">Quantity of ${UI.escape(p.name)}</label>
                        <input type="number" id="qty-${p.id}" min="1" max="${Math.max(p.availableStock, 1)}" value="1" inputmode="numeric" ${inStock ? '' : 'disabled'}>
                        <button type="button" data-step="1" aria-label="Increase quantity of ${UI.escape(p.name)}" ${inStock ? '' : 'disabled'}>+</button>
                    </div>
                    <button type="button" class="btn btn-primary add-btn" data-id="${p.id}" ${inStock ? '' : 'disabled'}>${inStock ? '🛒 Add' : 'Sold out'}</button>
                </div>
            </article>`;
    },

    async addToCart(button) {
        const user = await Auth.ready;
        if (!user) {
            UI.showToast('Please log in to add items to your cart.', 'warning');
            const next = encodeURIComponent(window.location.pathname + window.location.search);
            setTimeout(() => window.location.href = `/login.html?next=${next}`, 900);
            return;
        }
        if (!Auth.hasRole('CUSTOMER') || Auth.hasRole('MANAGER')) {
            UI.showToast('Only customer accounts can shop. Staff accounts cannot use the cart.', 'warning');
            return;
        }
        const productId = Number(button.dataset.id);
        const quantity = parseInt(document.getElementById(`qty-${productId}`).value, 10);
        if (!Number.isInteger(quantity) || quantity < 1) {
            UI.showToast('Quantity must be at least 1.', 'warning');
            return;
        }
        await UI.withBusy(button, async () => {
            try {
                await API.request('/api/v1/customer/cart/items', { method: 'POST', body: { productId, quantity } });
                UI.showToast('Item added to cart!', 'success');
            } catch (e) { /* toast shown */ }
        });
    },

    /** Wires quantity steppers and Add buttons for every card rendered inside `container`. */
    bind(container) {
        container.addEventListener('click', e => {
            const add = e.target.closest('.add-btn');
            if (add) return Shop.addToCart(add);
            const step = e.target.closest('.qty-stepper [data-step]');
            if (!step) return;
            const input = step.parentElement.querySelector('input');
            const next = (parseInt(input.value, 10) || 1) + Number(step.dataset.step);
            input.value = Math.min(Number(input.max) || next, Math.max(1, next));
            // Counts as user input, so UI.liveRefresh does not re-render the grid and reset the quantity.
            input.dispatchEvent(new Event('input', { bubbles: true }));
        });
    }
};

window.Shop = Shop;
