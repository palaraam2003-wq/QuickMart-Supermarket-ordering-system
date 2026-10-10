# RapidMart — API Endpoints Reference

All endpoints are under `http://localhost:8080`. State-changing requests (`POST`, `PUT`, `DELETE`) need the session cookie and an `X-XSRF-TOKEN` header whose value is the `XSRF-TOKEN` cookie (call `GET /api/v1/auth/csrf` first to get one). Request bodies are JSON unless the endpoint lists query parameters.

## Error format

Every error returns the same JSON shape:

```json
{ "timestamp": "2026-09-16T22:45:04+05:30", "status": 400, "errorCode": "INSUFFICIENT_STOCK",
  "message": "Insufficient stock for product 'Milk'. Requested: 5, Available: 2", "path": "/api/v1/customer/orders/checkout",
  "fieldErrors": null }
```

| Status | When |
|---|---|
| 400 | Validation (`VALIDATION_FAILED` + `fieldErrors`), malformed JSON, missing/invalid parameters, business-rule violations |
| 401 | Not signed in, bad credentials (`INVALID_CREDENTIALS`), deactivated account at login (`ACCOUNT_DISABLED`) |
| 403 | Wrong role, or the resource belongs to another user (`FORBIDDEN`); reviewing your own restock request (`SELF_APPROVAL_FORBIDDEN`) |
| 404 | Unknown record or path |
| 409 | Duplicates / conflicts (`SLOT_CONFLICT`, `DUPLICATE_REFUND`, `IDEMPOTENCY_KEY_CONFLICT`, `DUPLICATE_OPEN_REQUEST`, `INVALID_STATUS_TRANSITION`, `ALREADY_RECEIVED`, unique-constraint and concurrent-edit conflicts) |
| 400 | Also: `REASON_REQUIRED`, `REFUND_EXCEEDS_BILL`, `NO_CUSTOMER_EMAIL`, `PDF_GENERATION_FAILED`, `EMAIL_DELIVERY_FAILED` (billing/refund); `INVALID_QUANTITY`, `EXCEEDS_MAX_LEVEL` (restock) |
| 500 | Unexpected error (logged on the server; no stack trace is returned) |

## Authentication — `/api/v1/auth` (public)

| Method | Path | Body / params | Notes |
|---|---|---|---|
| POST | `/register` | `{email, password (≥6), fullName, phone?}` | Always creates a `CUSTOMER`. 201 |
| POST | `/login` | `{email, password}` | Starts a session (session id rotated). Returns user |
| POST | `/logout` | – | 204 |
| GET | `/me` | – | Current user; 401 when anonymous |
| GET | `/session` | – | `{authenticated, user}`; never 401 (used by the UI) |
| GET | `/csrf` | – | `{token, headerName, parameterName}` and sets the `XSRF-TOKEN` cookie |

## Public catalogue (no login)

| Method | Path | Params |
|---|---|---|
| GET | `/api/v1/categories/public` | Active categories |
| GET | `/api/v1/categories/public/{id}` | |
| GET | `/api/v1/products/public` | `categoryId?`, `search?` (or `name?`), `page=0`, `size=12` (max 100), `sortBy=name` |
| GET | `/api/v1/products/public/{id}` | Active products only |

## Customer — role `CUSTOMER`

| Method | Path | Body / params |
|---|---|---|
| GET / PUT | `/api/v1/customer/profile` | PUT `{fullName, phone?}` |
| GET | `/api/v1/customer/addresses` | |
| POST | `/api/v1/customer/addresses` | `{streetAddress, city, postalCode, phoneContact, isDefault}` |
| PUT | `/api/v1/customer/addresses/{id}` | same as POST |
| DELETE | `/api/v1/customer/addresses/{id}` | Soft delete (orders keep their address snapshot) |
| GET | `/api/v1/customer/cart` | |
| POST | `/api/v1/customer/cart/items` | `{productId, quantity}` |
| PUT | `/api/v1/customer/cart/items/{itemId}` | `{quantity}` |
| DELETE | `/api/v1/customer/cart/items/{itemId}` | |
| POST | `/api/v1/customer/orders/checkout` | `{addressId, deliveryDate (future), deliverySlot, paymentMethod: COD\|EFT\|CARD_SIMULATION\|PAYPAL_SIMULATION, offerCode?, redeemPoints?, idempotencyKey?}` → 201 `OrderDto` |
| GET | `/api/v1/customer/orders` | `page`, `size` |
| GET | `/api/v1/customer/orders/{id}` | |
| POST | `/api/v1/customer/orders/{id}/cancel` | `reason?` query param |
| POST | `/api/v1/customer/refunds` | `{orderId, reason}` → 201 |
| GET | `/api/v1/customer/refunds` | My refund requests |
| GET | `/api/v1/customer/loyalty/account` | `{currentPoints, lifetimePoints, tierName}` |
| GET | `/api/v1/customer/loyalty/history` | Reward transactions |
| GET | `/api/v1/customer/loyalty/offers` | Offers valid today for my tier |
| POST | `/api/v1/customer/feedback` | `{category, subject, message}` → 201 |
| GET | `/api/v1/customer/feedback` | Includes manager replies |

`/api/v1/orders/**` is an alias of `/api/v1/customer/orders/**`.

## Cashier — roles `CASHIER`, `MANAGER`

| Method | Path | Params / Body |
|---|---|---|
| GET | `/api/v1/cashier/payments/pending` | EFT payments awaiting verification (PENDING or FAILED) |
| POST | `/api/v1/cashier/payments/{orderId}/verify-eft` | Body `{approve, notes}` — note/reason is required (max 500 characters); stamps `verifiedBy`/`verifiedAt` on approval and issues the bill |
| GET | `/api/v1/cashier/refunds` | `page`, `size` |
| POST | `/api/v1/cashier/refunds/{id}/process` | Body `{approve, reason (required, both outcomes), refundedAmount? (required when approve=true; defaults to the requested amount; must not exceed the active bill's amount)}` |
| GET | `/api/v1/cashier/refunds/{id}/history` | Append-only audit history for one refund request (newest first) |
| GET | `/api/v1/cashier/cod-settlements` | Unconfirmed COD cash hand-overs |
| POST | `/api/v1/cashier/cod-settlements/{id}/confirm` | Idempotent. Also the COD **payment verification** step: flips the payment PENDING→PAID and stamps `verifiedBy`/`verifiedAt` |
| GET | `/api/v1/cashier/bills` | Latest 200 bills (legacy shortcut; prefer `/api/v1/billing/search` for filtering/paging) |

### Billing — roles `CASHIER`, `MANAGER`

| Method | Path | Params / Body |
|---|---|---|
| GET | `/api/v1/billing` | `page`, `size` — all bills |
| GET | `/api/v1/billing/search` | `query?` (bill #, order #, or customer email), `status?` (`ACTIVE`\|`VOIDED`), `page`, `size` |
| GET | `/api/v1/billing/pending` | Non-cancelled orders across all payment methods that are awaiting their first bill |
| POST | `/api/v1/billing/orders/{orderId}/preview` | Body `{taxPercentage, deliveryCharge}` — Preview calculated tax and net amount without persisting |
| POST | `/api/v1/billing/orders/{orderId}/finalize` | Body `{taxPercentage (0-100, ≤2 dp), deliveryCharge (≥0, ≤2 dp)}` — Saves the charges and generates the bill atomically for any payment method. Identical replay returns the same bill; different charges after generation → `409 BILL_ALREADY_GENERATED` |
| POST | `/api/v1/billing/generate/{orderId}` | Required body `{taxPercentage, deliveryCharge}`; idempotent (returns the existing active bill if one exists) |
| POST | `/api/v1/billing/reissue/{orderId}` | Body `{reason}` — voids the current active bill (if any) and issues a fresh one |
| GET | `/api/v1/billing/{billNumber}` | Rich detail: customer, line items, totals (incl. tax/delivery charge), and **live** payment/refund status |
| GET | `/api/v1/billing/order/{orderId}` | The order's current active bill (summary shape) |
| PATCH | `/api/v1/billing/{billNumber}` | Body `{notes}` — the only editable field; amount/order/billNumber are immutable |
| POST | `/api/v1/billing/{billNumber}/void` | Body `{reason}` — ACTIVE→VOIDED, idempotent |
| GET | `/api/v1/billing/{billNumber}/pdf` | `download=true\|false` (default false = inline preview) — PDF bytes |
| POST | `/api/v1/billing/{billNumber}/email` | Emails the PDF to the customer's email on file; `NO_CUSTOMER_EMAIL` if blank. Returns `{status: SENT\|NOT_SENT\|FAILED, message}` — `NOT_SENT` when no SMTP server is configured (see README mail env vars) |

## Inventory — roles `INVENTORY_STAFF` (shown as "Inventory Officer"), `MANAGER`

| Method | Path | Body / params |
|---|---|---|
| GET / POST | `/api/v1/inventory/categories` | `{name, description?, active}` |
| PUT | `/api/v1/inventory/categories/{id}` | same |
| GET | `/api/v1/inventory/products` | `page`, `size` (max 200), `sortBy=id` |
| POST | `/api/v1/inventory/products` | `{sku, name, description?, price (>0), unit, imageUrl?, categoryId, active, initialStock (0-1000)}` |
| PUT | `/api/v1/inventory/products/{id}` | same (initial stock ignored) |
| PUT | `/api/v1/inventory/products/{id}/deactivate` · DELETE `/{id}` | Soft deactivation |
| GET | `/api/v1/inventory/low-stock` | Quantity ≤ minimum level |
| GET | `/api/v1/inventory/products/{productId}` | Stock record |
| PUT | `/api/v1/inventory/products/{productId}/limits` | `{minimumLevel, maximumLevel}` |
| GET | `/api/v1/inventory/movements` | `productId?`, `page`, `size` |

### Restock requests (separation of duties)

An Inventory Officer requests stock, a **different** Manager approves or rejects it, and an Inventory Officer receives it. Status flow: `PENDING → APPROVED → RECEIVED` and `PENDING → REJECTED`; nothing else. Each rule is enforced by URL security **and** `@PreAuthorize` on the controller and service.

| Method | Path | Role | Body / params | Result |
|---|---|---|---|---|
| GET | `/api/v1/inventory/restock-requests` | Officer, Manager | `status?` (`PENDING`, `APPROVED`, `REJECTED`, `RECEIVED`), `page`, `size` | Page of requests, newest first |
| POST | `/api/v1/inventory/restock-requests` | Officer | `{productId, requestedQuantity (≥1)}` | 201, `PENDING` |
| PUT | `/api/v1/inventory/restock-requests/{id}/approve` | Manager | Optional `{approvedQuantity}`, 1 to `requestedQuantity` (default: all of it) | `APPROVED`; inventory is **not** changed |
| PUT | `/api/v1/inventory/restock-requests/{id}/reject` | Manager | `{reason}`, required, max 500 characters | `REJECTED` |
| POST | `/api/v1/inventory/restock-requests/{id}/receive` | Officer | `quantityReceived?`, 1 to the approved quantity (default: the approved quantity) | `RECEIVED`; stock increases and one `RECEIVED_STOCK` movement is written |

Each request in a response has these fields: `id`, `productId`, `productName`, `productSku`, `currentStock`, `minimumLevel`, `maximumLevel`, `status`, `requestedQuantity`, `approvedQuantity`, `receivedQuantity`, `requestedById`, `requestedByEmail`, `requestedByName`, `requestedAt`, `approvedByEmail`, `approvedAt`, `rejectedByEmail`, `rejectedAt`, `rejectionReason`, `receivedByEmail`, `receivedAt`, `createdAt`.

| Status | errorCode | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` / `INVALID_QUANTITY` | Requested, approved or received quantity out of range (≤ 0, approved > requested, received > approved) |
| 400 | `EXCEEDS_MAX_LEVEL` | The receipt would take stock above the product's maximum level |
| 400 | `VALIDATION_FAILED` | Rejection without a reason |
| 403 | `FORBIDDEN` | Wrong role: an officer approving/rejecting, a manager creating/receiving, or any other role |
| 403 | `SELF_APPROVAL_FORBIDDEN` | The requester tries to approve or reject their own request (even when they also hold `MANAGER`) |
| 404 | `RESOURCE_NOT_FOUND` | Unknown request, product or inventory record |
| 409 | `DUPLICATE_OPEN_REQUEST` | The product already has a `PENDING` or `APPROVED` request |
| 409 | `INVALID_STATUS_TRANSITION` | Any transition outside the flow above, for example receiving a `PENDING`/`REJECTED` request or approving twice |
| 409 | `ALREADY_RECEIVED` | The stock for this request was already received (repeated or concurrent receipt) |

## Delivery coordinator — roles `DELIVERY_COORDINATOR`, `MANAGER`

| Method | Path | Params |
|---|---|---|
| GET | `/api/v1/delivery-coordinator/deliveries` | `status?` |
| POST | `/api/v1/delivery-coordinator/deliveries/{id}/assign` | `staffId`, `scheduledDate`, `scheduledSlot` — assign or reschedule |
| GET | `/api/v1/delivery-coordinator/staff` | Optional `search`, `active`, `availabilityDate`, `availabilitySlot`, `available` filters |
| POST | `/api/v1/delivery-coordinator/staff` | `{email, password, fullName, phone?}` — creates a delivery-staff-only account |
| PUT | `/api/v1/delivery-coordinator/staff/{id}` | `{fullName, phone?, active}` — role changes are not allowed here |
| GET | `/api/v1/delivery-coordinator/staff/{id}/assignments` | Driver's current and historical delivery assignments |
| POST | `/api/v1/delivery-coordinator/deliveries/{id}/returned-items-checked` | FAILED deliveries |
| POST | `/api/v1/delivery-coordinator/deliveries/{id}/cancel` | `reason?` — FAILED + returned items checked |

## Delivery staff — role `DELIVERY_STAFF` (own deliveries only)

| Method | Path | Params |
|---|---|---|
| GET | `/api/v1/delivery-staff/my-deliveries` | |
| GET | `/api/v1/delivery-staff/cod-summary` | Today's collections and unsettled cash |
| POST | `/api/v1/delivery-staff/deliveries/{id}/out-for-delivery` | ASSIGNED → OUT_FOR_DELIVERY |
| POST | `/api/v1/delivery-staff/deliveries/{id}/failed` | `reason` (required) |
| POST | `/api/v1/delivery-staff/deliveries/{id}/complete` | `collectedAmount` (required for COD, must equal order total) |

## Loyalty officer — roles `LOYALTY_OFFICER`, `MANAGER`

| Method | Path | Body |
|---|---|---|
| GET / POST | `/api/v1/loyalty-officer/tiers` | Create `{tierName, minimumPoints (≥0), earningRateMultiplier (≥0), benefitsDescription?}`; responses include `version` |
| PUT | `/api/v1/loyalty-officer/tiers/{id}` | Same fields plus required current `version`; stale edits return HTTP 409 |
| GET / POST | `/api/v1/loyalty-officer/offers` | Create `{title, code, discountPercentage (0 < x ≤ 100), minOrderAmount, startDate, endDate (≥ start), eligibleTierId?, active?}`; responses include `version` |
| PUT | `/api/v1/loyalty-officer/offers/{id}` | Same fields plus required current `version`; stale edits return HTTP 409 |
| POST | `/api/v1/loyalty-officer/adjust-points` | `{userId, pointsDelta (≠0), reason}` — audited, never negative |
| GET | `/api/v1/loyalty-officer/adjustments` | Manual reward adjustment history with customer, balance, reason, and actor |

## Manager — role `MANAGER`

| Method | Path | Body / params |
|---|---|---|
| GET | `/api/v1/manager/profile` | Own profile incl. `lastLoginAt` |
| PUT | `/api/v1/manager/profile` | `{fullName, phone?}` — email cannot be changed here |
| GET | `/api/v1/manager/profile/email-change` | Pending email-change request, or 204 |
| POST | `/api/v1/manager/profile/email-change` | `{newEmail}` → 201; recorded as PENDING, replaces an earlier pending request; login email is unchanged until approved |
| POST | `/api/v1/manager/profile/password` | `{currentPassword, newPassword (≥8), confirmPassword}` → 204 |
| GET | `/api/v1/manager/employees` | Users with at least one staff role |
| POST | `/api/v1/manager/employees` | `{email, password (≥8), fullName, phone?, roles: [...]}` → 201 |
| PUT | `/api/v1/manager/employees/{id}` | `{fullName, phone?, roles: [...], active}` — cannot remove own MANAGER role or deactivate self |
| GET | `/api/v1/manager/reports/sales` | `startDate?`, `endDate?` → `{totalOrders, totalRevenue, refundedTotal, netRevenue, basis}` |
| GET | `/api/v1/manager/reports/sales/csv` | `startDate?`, `endDate?` — `text/csv` attachment |
| GET | `/api/v1/manager/reports/inventory` | |
| GET | `/api/v1/manager/reports/delivery` | Counts per delivery status |
| GET | `/api/v1/manager/audit-logs` | `page`, `size` |
| GET | `/api/v1/manager/feedback` | `page`, `size` |
| POST | `/api/v1/manager/feedback/{id}/reply` | `{replyMessage, category?}` — not allowed once CLOSED |
| POST | `/api/v1/manager/feedback/{id}/close` | Idempotent |
