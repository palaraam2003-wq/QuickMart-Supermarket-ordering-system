// Delivery-staff account management, order limits, and delayed deliveries for coordinator portal.
(() => {
    'use strict';

    const byId = id => document.getElementById(id);
    let managedDrivers = [];

    function driverQuery() {
        const params = new URLSearchParams();
        const search = byId('driver-search').value.trim();
        const active = byId('driver-status').value;
        const available = byId('driver-availability').value;
        const date = byId('availability-date').value;
        const slot = byId('availability-slot').value;
        if (search) params.set('search', search);
        if (active) params.set('active', active);
        if (available) params.set('available', available);
        if (date) params.set('availabilityDate', date);
        if (slot) params.set('availabilitySlot', slot);
        const query = params.toString();
        return query ? `?${query}` : '';
    }

    function availabilityBadge(driver) {
        if (!driver.active) return '<span class="badge badge-danger">Inactive</span>';
        return driver.available
            ? '<span class="badge badge-success">Available</span>'
            : '<span class="badge badge-warning">Unavailable</span>';
    }

    function renderDrivers() {
        const container = byId('drivers-container');
        if (!managedDrivers.length) {
            container.innerHTML = '<p class="muted">No delivery staff match the selected filters.</p>';
            return;
        }
        container.innerHTML = `<table>
            <thead><tr><th>Driver</th><th>Contact</th><th>Account</th><th>Availability</th><th>Assignments</th><th>Actions</th></tr></thead>
            <tbody>${managedDrivers.map(driver => `<tr>
                <td><strong>${UI.escape(driver.fullName)}</strong><div class="muted small">ID ${driver.id}</div></td>
                <td class="small">${UI.escape(driver.email)}<div class="muted">${UI.escape(driver.phone || 'No phone')}</div></td>
                <td><span class="badge ${driver.active ? 'badge-success' : 'badge-danger'}">${driver.active ? 'Active' : 'Deactivated'}</span></td>
                <td>${availabilityBadge(driver)}<div class="muted small">${UI.escape(driver.availabilityDate || 'Current')} · ${UI.escape(driver.availabilitySlot || '')}</div></td>
                <td><strong>${driver.activeAssignmentCount}</strong> active<div class="muted small">${driver.totalAssignmentCount} total</div></td>
                <td><div class="row">
                    <button type="button" class="btn btn-secondary btn-sm" data-driver-assignments="${driver.id}">Assignments</button>
                    ${driver.coordinatorManageable ? `
                        <button type="button" class="btn btn-secondary btn-sm" data-edit-driver="${driver.id}">Edit</button>
                        <button type="button" class="btn ${driver.active ? 'btn-danger' : 'btn-success'} btn-sm" data-toggle-driver="${driver.id}">${driver.active ? 'Deactivate' : 'Activate'}</button>
                    ` : '<span class="muted small">Manager-controlled roles</span>'}
                </div></td>
            </tr>`).join('')}</tbody>
        </table>`;
    }

    async function loadDrivers() {
        const container = byId('drivers-container');
        UI.setLoading(container, 'Loading delivery staff…');
        try {
            managedDrivers = await API.request('/api/v1/delivery-coordinator/staff' + driverQuery());
            renderDrivers();
        } catch (error) {
            UI.setError(container, 'Could not load delivery staff.');
        }
    }

    function openDriverForm(driver) {
        const form = byId('driver-form');
        form.reset();
        form.hidden = false;
        byId('driver-form-title').textContent = driver ? `Edit ${driver.fullName}` : 'Add Delivery Staff';
        byId('driverId').value = driver ? driver.id : '';
        byId('driverName').value = driver ? driver.fullName : '';
        byId('driverPhone').value = driver ? (driver.phone || '') : '';
        byId('driverEmail').value = '';
        byId('driverPassword').value = '';
        byId('driver-credentials').hidden = !!driver;
        byId('driver-active-group').hidden = !driver;
        byId('driverActive').checked = driver ? driver.active : true;
        byId('driverName').focus();
    }

    async function saveDriver(event) {
        event.preventDefault();
        const id = byId('driverId').value;
        const fullName = byId('driverName').value.trim();
        const phone = byId('driverPhone').value.trim() || null;
        if (!fullName) return UI.showToast('Driver name is required.', 'warning');

        const body = id
            ? { fullName, phone, active: byId('driverActive').checked }
            : { email: byId('driverEmail').value.trim(), password: byId('driverPassword').value, fullName, phone };
        if (!id && !body.email) return UI.showToast('Driver email is required.', 'warning');
        if (!id && body.password.length < 8) return UI.showToast('Password must be at least 8 characters.', 'warning');

        await UI.withBusy(byId('driver-save'), async () => {
            try {
                await API.request(id ? `/api/v1/delivery-coordinator/staff/${id}` : '/api/v1/delivery-coordinator/staff', {
                    method: id ? 'PUT' : 'POST', body
                });
                UI.showToast(id ? 'Delivery staff updated.' : 'Delivery staff added.', 'success');
                byId('driver-form').hidden = true;
                await Promise.all([loadDrivers(), window.loadData ? window.loadData() : Promise.resolve()]);
            } catch (error) { /* API client shows error */ }
        });
    }

    async function toggleDriver(driver, button) {
        const nextActive = !driver.active;
        if (!confirm(`${nextActive ? 'Activate' : 'Deactivate'} ${driver.fullName}?`)) return;
        await UI.withBusy(button, async () => {
            try {
                await API.request(`/api/v1/delivery-coordinator/staff/${driver.id}`, {
                    method: 'PUT',
                    body: { fullName: driver.fullName, phone: driver.phone, active: nextActive }
                });
                UI.showToast(`Driver account ${nextActive ? 'activated' : 'deactivated'}.`, 'success');
                await Promise.all([loadDrivers(), window.loadData ? window.loadData() : Promise.resolve()]);
            } catch (error) { /* API client shows error */ }
        });
    }

    async function showAssignments(driver) {
        const panel = byId('driver-assignments-panel');
        const container = byId('driver-assignments-container');
        panel.hidden = false;
        byId('driver-assignments-title').textContent = `${driver.fullName} — Delivery Assignments`;
        UI.setLoading(container);
        try {
            const assignments = await API.request(`/api/v1/delivery-coordinator/staff/${driver.id}/assignments`);
            container.innerHTML = assignments.length ? `<table>
                <thead><tr><th>Order</th><th>Schedule</th><th>Delivery Status</th><th>Address</th><th>Completed</th></tr></thead>
                <tbody>${assignments.map(delivery => `<tr>
                    <td><strong>${UI.escape(delivery.orderNumber)}</strong><div class="muted small">${UI.formatCurrency(delivery.totalAmount)}</div></td>
                    <td class="small">${UI.escape(delivery.scheduledDate)}<div class="muted">${UI.escape(UI.slotLabel(delivery.scheduledSlot))}</div></td>
                    <td>${UI.statusBadge(delivery.deliveryStatus)}</td>
                    <td class="small">${UI.escape(delivery.deliveryStreet)}, ${UI.escape(delivery.deliveryCity)}</td>
                    <td class="small">${delivery.completedAt ? UI.formatDateTime(delivery.completedAt) : '&mdash;'}</td>
                </tr>`).join('')}</tbody>
            </table>` : '<p class="muted mt-1">No deliveries have been assigned to this driver.</p>';
            panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        } catch (error) {
            UI.setError(container, 'Could not load driver assignments.');
        }
    }

    // --- Driver Slot Limits ---

    const MAX_ORDERS_PER_SLOT = 50;
    const ALL_DRIVERS = 'ALL';
    let slotLimits = [];
    let limitDrivers = [];

    /** Called by the dashboard whenever the active driver list is (re)loaded. */
    function populateLimitDrivers(activeDrivers) {
        limitDrivers = activeDrivers;
        const options = activeDrivers.map(d => `<option value="${d.id}">${UI.escape(d.fullName)}</option>`).join('');
        const formSelect = byId('limit-driver');
        const filterSelect = byId('limit-filter-driver');
        const formValue = formSelect.value;
        const filterValue = filterSelect.value;
        formSelect.innerHTML = '<option value="">Select driver</option>'
            + (activeDrivers.length > 1 ? `<option value="${ALL_DRIVERS}">All active drivers (${activeDrivers.length})</option>` : '')
            + options;
        filterSelect.innerHTML = '<option value="">All drivers</option>' + options;
        formSelect.value = [...formSelect.options].some(o => o.value === formValue) ? formValue : '';
        filterSelect.value = [...filterSelect.options].some(o => o.value === filterValue) ? filterValue : '';
    }
    window.populateLimitDrivers = populateLimitDrivers;

    function capacityState(limit) {
        if (limit.maxOrders === 0) return { cls: 'badge-danger', label: 'Closed', bar: 'is-closed' };
        if (limit.assignedCount > limit.maxOrders) return { cls: 'badge-danger', label: 'Over limit', bar: 'is-over' };
        if (limit.assignedCount === limit.maxOrders) return { cls: 'badge-warning', label: 'Full', bar: 'is-full' };
        return { cls: 'badge-success', label: `${limit.remainingCapacity} open`, bar: '' };
    }

    function capacityBar(assigned, max, extraClass = '') {
        const pct = max > 0 ? Math.min(100, Math.round((assigned / max) * 100)) : 100;
        return `<div class="capacity-bar ${extraClass}" role="progressbar" aria-valuemin="0" aria-valuemax="${max}" aria-valuenow="${assigned}"
                    aria-label="${assigned} of ${max} orders assigned"><span style="width:${pct}%"></span></div>`;
    }

    function filteredLimits() {
        const date = byId('limit-filter-date').value;
        const slot = byId('limit-filter-slot').value;
        const driverId = byId('limit-filter-driver').value;
        const today = UI.isoDate(0);
        return slotLimits.filter(l => (date ? l.scheduledDate === date : l.scheduledDate >= today)
            && (!slot || l.scheduledSlot === slot)
            && (!driverId || String(l.driverId) === driverId));
    }

    function renderSlotSummary(limits) {
        const summary = byId('slot-summary');
        const date = byId('limit-filter-date').value;
        if (!date) {
            summary.innerHTML = '';
            return;
        }
        summary.innerHTML = UI.SLOTS.map(([code, label]) => {
            const inSlot = limits.filter(l => l.scheduledSlot === code);
            const max = inSlot.reduce((sum, l) => sum + l.maxOrders, 0);
            const assigned = inSlot.reduce((sum, l) => sum + l.assignedCount, 0);
            const open = inSlot.reduce((sum, l) => sum + l.remainingCapacity, 0);
            const openDrivers = inSlot.filter(l => l.remainingCapacity > 0).length;
            return `<button type="button" class="slot-card ${inSlot.length ? '' : 'is-empty'}" data-slot-card="${code}" title="Show ${UI.escape(label)} only">
                <span class="slot-card-label">${UI.escape(label)}</span>
                ${inSlot.length ? `
                    <span class="slot-card-value">${assigned}<span class="muted"> / ${max}</span></span>
                    ${capacityBar(assigned, max, assigned >= max ? 'is-full' : '')}
                    <span class="muted small">${open} open · ${openDrivers} of ${inSlot.length} driver${inSlot.length === 1 ? '' : 's'} available</span>`
                : '<span class="muted small">No limits set — drivers cannot be assigned</span>'}
            </button>`;
        }).join('');
    }

    function renderSlotLimits() {
        const container = byId('slot-limits-container');
        const limits = filteredLimits();
        renderSlotSummary(limits);
        if (!limits.length) {
            const date = byId('limit-filter-date').value;
            container.innerHTML = `<p class="muted">${slotLimits.length
                ? `No limits match these filters${date ? ` for ${UI.escape(date)}` : ''}. Use the form above to add one.`
                : 'No time slot order limits configured yet. Use the form above to add the first one.'}</p>`;
            return;
        }
        container.innerHTML = `<table>
            <thead><tr><th>Date</th><th>Slot</th><th>Driver</th><th>Capacity</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>${limits.map(l => {
                const state = capacityState(l);
                return `<tr>
                    <td><strong>${UI.escape(l.scheduledDate)}</strong></td>
                    <td>${UI.escape(UI.slotLabel(l.scheduledSlot))}</td>
                    <td><strong>${UI.escape(l.driverName)}</strong><div class="muted small">${UI.escape(l.driverEmail)}</div></td>
                    <td class="capacity-cell">
                        <div class="small"><strong>${l.assignedCount}</strong> of ${l.maxOrders} assigned</div>
                        ${capacityBar(l.assignedCount, l.maxOrders, state.bar)}
                    </td>
                    <td><span class="badge ${state.cls}">${UI.escape(state.label)}</span></td>
                    <td><div class="row">
                        <button type="button" class="btn btn-secondary btn-sm" data-edit-limit="${l.id}">Edit</button>
                        <button type="button" class="btn btn-danger btn-sm" data-delete-limit="${l.id}">Remove</button>
                    </div></td>
                </tr>`;
            }).join('')}</tbody>
        </table>`;
    }

    async function loadSlotLimits() {
        const container = byId('slot-limits-container');
        if (!container) return;
        if (!slotLimits.length) UI.setLoading(container, 'Loading order slot limits…');
        try {
            slotLimits = await API.request('/api/v1/delivery-coordinator/slot-limits');
            renderSlotLimits();
        } catch (error) {
            UI.setError(container, error && error.status === 404
                ? 'Slot limits are unavailable: the server is running an older build. Restart RapidMart to load the latest code.'
                : 'Could not load slot limits.');
        }
    }
    window.loadSlotLimits = loadSlotLimits;

    function resetLimitForm() {
        const date = byId('limit-date').value;
        const slot = byId('limit-slot').value;
        byId('limit-config-form').reset();
        byId('limit-date').value = date || UI.isoDate(0);
        byId('limit-slot').innerHTML = UI.slotOptions(slot || UI.SLOTS[0][0]);
        byId('limit-driver').disabled = false;
        byId('limit-date').disabled = false;
        byId('limit-slot').disabled = false;
        byId('limit-form-title').textContent = 'Set Order Limit';
        byId('limit-save-btn').textContent = 'Save Limit';
        byId('limit-cancel-edit').hidden = true;
        updateLimitHint();
    }

    function editSlotLimit(limit) {
        if (!limit) return;
        byId('limit-date').value = limit.scheduledDate;
        byId('limit-slot').innerHTML = UI.slotOptions(limit.scheduledSlot);
        const driverSelect = byId('limit-driver');
        if (![...driverSelect.options].some(o => o.value === String(limit.driverId))) {
            driverSelect.insertAdjacentHTML('beforeend', `<option value="${limit.driverId}">${UI.escape(limit.driverName)}</option>`);
        }
        driverSelect.value = String(limit.driverId);
        byId('limit-max').value = limit.maxOrders;
        // The date/slot/driver identify the limit; only the number changes while editing.
        driverSelect.disabled = true;
        byId('limit-date').disabled = true;
        byId('limit-slot').disabled = true;
        byId('limit-form-title').textContent = `Edit limit — ${limit.driverName}`;
        byId('limit-save-btn').textContent = 'Update Limit';
        byId('limit-cancel-edit').hidden = false;
        updateLimitHint();
        byId('limit-config-form').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        byId('limit-max').focus();
        byId('limit-max').select();
    }

    function updateLimitHint() {
        const hint = byId('limit-form-hint');
        const driverId = byId('limit-driver').value;
        const date = byId('limit-date').value;
        const slot = byId('limit-slot').value;
        const max = parseInt(byId('limit-max').value, 10);
        const matching = slotLimits.filter(l => l.scheduledDate === date && l.scheduledSlot === slot
            && (driverId === ALL_DRIVERS || String(l.driverId) === driverId));
        if (driverId === ALL_DRIVERS) {
            hint.textContent = `Applies ${isNaN(max) ? '' : max + ' '}to ${limitDrivers.length} drivers; ${matching.length} already have a limit here and will be updated.`;
            return;
        }
        const existing = matching[0];
        if (!existing) {
            hint.textContent = driverId ? 'New limit for this driver and slot.' : '';
        } else if (!isNaN(max) && max < existing.assignedCount) {
            hint.innerHTML = `<span class="text-danger">${existing.assignedCount} orders are already assigned — a lower limit keeps them but blocks new assignments.</span>`;
        } else {
            hint.textContent = `Currently ${existing.maxOrders} (${existing.assignedCount} assigned).`;
        }
    }

    async function saveSlotLimit(event) {
        event.preventDefault();
        const driverId = byId('limit-driver').value;
        const scheduledDate = byId('limit-date').value;
        const scheduledSlot = byId('limit-slot').value;
        const rawMax = byId('limit-max').value.trim();
        const maxOrders = Number(rawMax);

        if (!driverId) return UI.showToast('Please select a driver.', 'warning');
        if (!scheduledDate) return UI.showToast('Please select a date.', 'warning');
        if (scheduledDate < UI.isoDate(0)) return UI.showToast('Choose today or a future date.', 'warning');
        if (!scheduledSlot) return UI.showToast('Please select a time slot.', 'warning');
        if (rawMax === '' || !Number.isInteger(maxOrders) || maxOrders < 0 || maxOrders > MAX_ORDERS_PER_SLOT) {
            return UI.showToast(`Max orders must be a whole number from 0 to ${MAX_ORDERS_PER_SLOT}.`, 'warning');
        }

        const driverIds = driverId === ALL_DRIVERS ? limitDrivers.map(d => d.id) : [Number(driverId)];
        await UI.withBusy(byId('limit-save-btn'), async () => {
            const results = await Promise.allSettled(driverIds.map(id => API.request('/api/v1/delivery-coordinator/slot-limits', {
                method: 'POST',
                body: { driverId: id, scheduledDate, scheduledSlot, maxOrders },
                silent: driverIds.length > 1
            })));
            const failed = results.filter(r => r.status === 'rejected');
            const saved = results.length - failed.length;
            if (driverIds.length > 1) {
                if (saved) UI.showToast(`Limit saved for ${saved} driver${saved === 1 ? '' : 's'}.`, failed.length ? 'warning' : 'success');
                if (failed.length) UI.showToast(`${failed.length} failed: ${failed[0].reason && failed[0].reason.message || 'Unknown error'}`, 'danger');
            } else if (saved) {
                UI.showToast('Driver slot order limit saved.', 'success');
            }
            if (saved) {
                // Show the date that was just configured so the change is visible.
                byId('limit-filter-date').value = scheduledDate;
                resetLimitForm();
                await Promise.all([loadSlotLimits(), loadDrivers(), window.loadData ? window.loadData() : Promise.resolve()]);
            }
        });
    }

    async function deleteSlotLimit(limit, button) {
        if (!limit) return;
        const warning = limit.assignedCount
            ? `\n\n${limit.assignedCount} assigned order(s) stay with the driver, but no new orders can be assigned in this slot until a limit is set again.`
            : '';
        if (!confirm(`Remove the ${UI.slotLabel(limit.scheduledSlot)} limit for ${limit.driverName} on ${limit.scheduledDate}?${warning}`)) return;
        await UI.withBusy(button, async () => {
            try {
                await API.request(`/api/v1/delivery-coordinator/slot-limits/${limit.id}`, { method: 'DELETE' });
                UI.showToast('Slot limit removed.', 'info');
                await Promise.all([loadSlotLimits(), loadDrivers()]);
            } catch (error) { /* API client shows toast */ }
        });
    }

    function initSlotLimits() {
        const today = UI.isoDate(0);
        byId('limit-date').min = today;
        byId('limit-date').value = today;
        byId('limit-slot').innerHTML = UI.slotOptions(UI.SLOTS[0][0]);
        byId('limit-filter-date').min = today;
        byId('limit-filter-date').value = today;
        byId('limit-filter-slot').innerHTML = '<option value="">All slots</option>' + UI.slotOptions('');

        const form = byId('limit-config-form');
        form.addEventListener('submit', saveSlotLimit);
        form.addEventListener('input', updateLimitHint);
        form.addEventListener('change', updateLimitHint);
        form.addEventListener('click', event => {
            const step = event.target.closest('[data-step]');
            if (!step) return;
            const input = byId('limit-max');
            const current = parseInt(input.value, 10) || 0;
            input.value = Math.min(MAX_ORDERS_PER_SLOT, Math.max(0, current + Number(step.dataset.step)));
            updateLimitHint();
        });
        byId('limit-cancel-edit').addEventListener('click', resetLimitForm);

        ['limit-filter-date', 'limit-filter-slot', 'limit-filter-driver'].forEach(id =>
            byId(id).addEventListener('change', renderSlotLimits));
        byId('limit-filter-upcoming').addEventListener('click', () => {
            byId('limit-filter-date').value = '';
            renderSlotLimits();
        });
        byId('slot-summary').addEventListener('click', event => {
            const card = event.target.closest('[data-slot-card]');
            if (!card) return;
            const select = byId('limit-filter-slot');
            select.value = select.value === card.dataset.slotCard ? '' : card.dataset.slotCard;
            // Pre-fill the form so a missing limit can be added in one step.
            byId('limit-slot').innerHTML = UI.slotOptions(card.dataset.slotCard);
            if (byId('limit-filter-date').value && !byId('limit-date').disabled) byId('limit-date').value = byId('limit-filter-date').value;
            renderSlotLimits();
            updateLimitHint();
        });
        byId('slot-limits-container').addEventListener('click', event => {
            const edit = event.target.closest('[data-edit-limit]');
            if (edit) return editSlotLimit(slotLimits.find(l => String(l.id) === edit.dataset.editLimit));
            const remove = event.target.closest('[data-delete-limit]');
            if (remove) return deleteSlotLimit(slotLimits.find(l => String(l.id) === remove.dataset.deleteLimit), remove);
        });
    }

    // --- Delayed Deliveries ---

    async function loadDelayedDeliveries() {
        const container = byId('delayed-deliveries-container');
        if (!container) return;
        UI.setLoading(container, 'Loading delayed deliveries report…');
        try {
            const delayed = await API.request('/api/v1/delivery-coordinator/delayed-deliveries');
            if (!delayed.length) {
                container.innerHTML = '<p class="muted">No delayed deliveries recorded.</p>';
                return;
            }
            container.innerHTML = `<table>
                <thead><tr><th>Order</th><th>Driver</th><th>Slot</th><th>Expected Completion</th><th>Actual Delivery</th><th>Delay Duration</th><th>Status</th></tr></thead>
                <tbody>${delayed.map(d => `<tr>
                    <td><strong>${UI.escape(d.orderNumber)}</strong><div class="muted small">${UI.formatCurrency(d.totalAmount)} · ${UI.escape(d.deliveryCity)}</div></td>
                    <td><strong>${UI.escape(d.assignedStaffName)}</strong></td>
                    <td class="small">${UI.escape(d.scheduledDate)}<br><span class="muted">${UI.escape(UI.slotLabel(d.scheduledSlot))}</span></td>
                    <td class="small">${UI.formatDateTime(d.expectedCompletionTime)}</td>
                    <td class="small">${UI.formatDateTime(d.actualDeliveryTime)}</td>
                    <td><span class="badge badge-danger">⏱ ${UI.escape(d.delayDurationFormatted)}</span></td>
                    <td><span class="badge badge-warning">${UI.escape(d.deliveryStatus)}</span></td>
                </tr>`).join('')}</tbody>
            </table>`;
        } catch (error) {
            UI.setError(container, 'Could not load delayed deliveries.');
        }
    }
    window.loadDelayedDeliveries = loadDelayedDeliveries;

    async function initDriverManagement() {
        byId('availability-date').min = UI.isoDate(0);
        byId('availability-date').value = UI.isoDate(0);
        byId('availability-slot').innerHTML = UI.slotOptions('MORNING_09_12');

        initSlotLimits();

        byId('new-driver').addEventListener('click', () => openDriverForm(null));
        byId('driver-cancel').addEventListener('click', () => { byId('driver-form').hidden = true; });
        byId('driver-form').addEventListener('submit', saveDriver);
        byId('driver-assignments-close').addEventListener('click', () => { byId('driver-assignments-panel').hidden = true; });

        byId('driver-filter-form').addEventListener('submit', async event => {
            event.preventDefault();
            await UI.withBusy(byId('driver-filter-button'), loadDrivers);
        });
        byId('driver-filter-reset').addEventListener('click', () => {
            byId('driver-filter-form').reset();
            byId('availability-date').value = UI.isoDate(0);
            byId('availability-slot').innerHTML = UI.slotOptions('MORNING_09_12');
            loadDrivers();
        });

        byId('drivers-container').addEventListener('click', event => {
            const editButton = event.target.closest('[data-edit-driver]');
            if (editButton) return openDriverForm(managedDrivers.find(driver => String(driver.id) === editButton.dataset.editDriver));

            const toggleButton = event.target.closest('[data-toggle-driver]');
            if (toggleButton) return toggleDriver(managedDrivers.find(driver => String(driver.id) === toggleButton.dataset.toggleDriver), toggleButton);

            const assignmentsButton = event.target.closest('[data-driver-assignments]');
            if (assignmentsButton) return showAssignments(managedDrivers.find(driver => String(driver.id) === assignmentsButton.dataset.driverAssignments));
        });

        await Promise.all([loadDrivers(), loadSlotLimits(), loadDelayedDeliveries()]);
        UI.liveRefresh(async () => {
            await Promise.all([loadDrivers(), loadSlotLimits(), loadDelayedDeliveries()]);
        });
    }

    document.addEventListener('DOMContentLoaded', async () => {
        await Auth.ready;
        if (Auth.hasAnyRole(['DELIVERY_COORDINATOR', 'MANAGER'])) await initDriverManagement();
    });
})();
