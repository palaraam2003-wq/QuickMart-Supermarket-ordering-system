# RapidMart — Business Rules & State Transitions

## Order & Payment Status Matrix
The application keeps separate status fields for Orders, Payments, Deliveries, and Refunds.

### 1. Order Statuses
| From | To | Trigger |
|---|---|---|
| (checkout) | `CONFIRMED` | COD, CARD_SIMULATION or PAYPAL_SIMULATION checkout |
| (checkout) | `AWAITING_PAYMENT` | EFT checkout |
| `AWAITING_PAYMENT` | `CONFIRMED` | Cashier verifies the EFT payment |
| `CONFIRMED` | `OUT_FOR_DELIVERY` | Driver starts an ASSIGNED delivery |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Driver completes the delivery |
| `OUT_FOR_DELIVERY` | `CONFIRMED` | Driver records a failed delivery (awaiting reschedule or cancellation) |
| `AWAITING_PAYMENT` / `CONFIRMED` | `CANCELLED` | Customer cancellation, coordinator cancellation, or refund approval (see below) |

A COD order is `CONFIRMED` while its payment is still `PENDING`.

### 2. Payment Statuses
| From | To | Trigger |
|---|---|---|
| (checkout) | `PAID` | CARD/PayPal **local simulation** (no real money moves) |
| (checkout) | `PENDING` | COD and EFT |
| `PENDING` / `FAILED` | `PAID` | Cashier verifies EFT (retry is allowed from `FAILED`). A bill is issued once. `verifiedBy`/`verifiedAt` are stamped |
| `PENDING` | `FAILED` | Cashier rejects the EFT transfer, or the order is cancelled before payment |
| `PENDING` | `PAID` | COD: cashier confirms receipt of the driver's collected cash (`verifiedBy`/`verifiedAt` are stamped here, not when the driver collects it) |
| `PAID` | `REFUNDED` | Cashier approves the refund request |

Verifying an already `PAID` payment is a no-op. `PARTIALLY_REFUNDED` exists in the schema but full refunds only are supported.

**COD payment verification, precisely:** the driver completing a COD delivery (`DeliveryService.completeDelivery`) records the collected cash amount and creates one pending `CodSettlement`, but does **not** verify the payment — it stays `PENDING`. The payment only moves to `PAID` when a cashier confirms receipt of that physical cash (`CodSettlementService.confirmCashReceipt`, exposed as `POST /api/v1/cashier/cod-settlements/{id}/confirm`), which is the literal cashier "payment verification" step for COD orders. This means a refund cannot be requested on a COD order (refunds require `PAID`) until the cashier has confirmed the cash.

### 3. Delivery Statuses
| From | To | Trigger |
|---|---|---|
| (checkout) | `PENDING` | |
| `PENDING` / `ASSIGNED` / `FAILED` | `ASSIGNED` | Coordinator assigns or reschedules (unpaid non-COD orders are rejected; one delivery per driver per date+slot) |
| `ASSIGNED` | `OUT_FOR_DELIVERY` | Assigned driver only |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Assigned driver; COD needs the exact amount; completing twice is a no-op |
| `OUT_FOR_DELIVERY` | `FAILED` | Assigned driver; non-blank reason required |
| `FAILED` | `CANCELLED` | Coordinator, only after marking returned items as checked |
| any open status | `CANCELLED` | When the order itself is cancelled |

### 4. Refund Statuses
- `PENDING`: Customer submitted a refund request. Only one request per order; the payment must be `PAID` and the order must not be out for delivery.
- `REFUNDED`: Cashier approved ("Refunded" in the UI). This records that the money was refunded **outside** RapidMart; no funds are transferred by the application.
- `REJECTED`: Cashier rejected the request (labelled "Refused" in the UI/PDF; the underlying enum value stays `REJECTED`).

Processing a request that is no longer `PENDING` is a no-op. A non-blank reason/note is required to process a refund either way. On approval, the cashier records the actual `refundedAmount` (defaulting to the originally requested amount), which is validated against the order's active bill amount (`REFUND_EXCEEDS_BILL` if it's higher) and cannot exceed it. `refundStatus` (on the refund request) and `paymentStatus` (on the payment) remain separate fields — approving a refund sets both, but they are never merged into one status. Every processing call that actually changes state (not a no-op replay) appends one row to `refund_status_history` (previous status, new status, amount, reason, actor, timestamp) — this history is append-only and is never overwritten, unlike `RefundRequest.cashierNotes`/`processedBy`, which only reflect the latest action.

## Cancellation Rules
- **Customer**: allowed only while the order is `AWAITING_PAYMENT` or `CONFIRMED`, the delivery is still `PENDING` (no driver assigned), and nothing has been paid. Paid orders must use a refund request instead.
- **Delivery coordinator**: allowed only for a `FAILED` delivery after the returned items were checked.
- **Refund approval** for an order that was not delivered cancels the order.
- Every cancellation restores stock with `CANCELLATION_RESTORATION` movements, returns points redeemed at checkout, sets the delivery to `CANCELLED`, and marks a still-pending payment `FAILED`. Cancelling an already cancelled order does nothing, so stock is never restored twice.

## Inventory & Stock Movement Rules
- **Pessimistic Locking**: checkout locks inventory rows (`findByProductIdWithLock`) in product-id order, re-validates stock inside the transaction, then deducts. This prevents overselling and lock-order deadlocks.
- **Optimistic Locking**: `@Version` on inventory, products, loyalty tiers, offers, and loyalty accounts rejects conflicting concurrent edits with HTTP 409. Tier/offer update requests also carry their loaded version so stale browser forms cannot overwrite a newer Manager or Loyalty Officer change.
- **Movement Audit**: every stock change creates a `StockMovementHistory` record: `INITIAL_STOCK` (product creation), `RECEIVED_STOCK`, `CHECKOUT_DEDUCTION`, `CANCELLATION_RESTORATION`, `MANUAL_ADJUSTMENT`.
- **Restocking (separation of duties)**: no one person controls a restock from start to finish.
  - **Status flow**: `PENDING → APPROVED → RECEIVED` or `PENDING → REJECTED`. Every other transition is refused with HTTP 409 `INVALID_STATUS_TRANSITION`. `REJECTED` and `RECEIVED` are final.
  - **Inventory Officer** (role code `INVENTORY_STAFF`, kept for compatibility): views stock and the request history, creates requests (quantity ≥ 1), and receives stock **only** for `APPROVED` requests. Cannot approve or reject.
  - **Manager**: views all requests and approves or rejects `PENDING` ones. May approve fewer units than requested (1 to the requested quantity). Rejection needs a reason. Cannot create requests or receive stock.
  - **No self-review**: the user who created a request can never approve or reject it, whatever roles they hold (HTTP 403 `SELF_APPROVAL_FORBIDDEN`).
  - **One open request per product**: a product can have at most one `PENDING` or `APPROVED` request (HTTP 409 `DUPLICATE_OPEN_REQUEST`). A service check and the filtered unique index `uk_restock_open_product` both enforce this, so it also holds under concurrent creates.
  - **Approval does not change stock**. Stock increases only when the approved goods are received. The received quantity must be ≥ 1, at most the approved quantity, and must not take stock above the maximum level. A request is received once; a partial receipt still completes it.
  - **Atomic, once-only receipt**: one transaction locks the request row, then the inventory row (`PESSIMISTIC_WRITE`). Inside it, the inventory increase, the change to `RECEIVED` and exactly one `RECEIVED_STOCK` movement are written. The movement has the product, a positive delta, reference `RESTOCK_REQ_<id>`, the receiving officer and a timestamp. A repeated or concurrent receipt waits for the lock, sees `RECEIVED` and gets HTTP 409 `ALREADY_RECEIVED`, so stock never increases twice. The unique index `uk_movement_restock_receipt` guards the movement at database level.
  - **Repeated approvals and rejections** are refused with HTTP 409, so they never change state twice.
  - **Audit trail**: each request stores `requestedBy/At`, `approvedBy/At`, `rejectedBy/At` with `rejectionReason`, `receivedBy/At`, and `requestedQuantity`, `approvedQuantity`, `receivedQuantity`. Every create, approval, rejection and receipt is also written to the audit log (`RESTOCK_REQUESTED`, `RESTOCK_APPROVED`, `RESTOCK_REJECTED`, `RESTOCK_RECEIVED`).
- **Soft Deactivation**: products are deactivated (`active = false`) to preserve historical order references.

## Loyalty Points & Offer Rules
- **Concurrent Access**: Managers and Loyalty Officers share the same version-aware APIs. Simultaneous point changes are transactional; one conflicting update is rolled back instead of losing points.
- **Earning**: 1 point per LKR 100 of the order total × tier multiplier, awarded once when the order is `DELIVERED` (guarded by the order reference in the reward history).
- **Redemption**: 1 point = LKR 1. Discounts (offer + points) must leave a payable total above zero.
- **Offers**: at most one per order; must be active, within its start/end dates, meet the minimum order amount, and match the customer's tier when the offer is tier-restricted. Discount percentage must be > 0 and ≤ 100.
- **Refund reversal**: approving a refund of a delivered order reverses exactly the points earned from it, once. If the customer already spent them, the approval is rejected with `NEGATIVE_BALANCE_PREVENTED` for manual resolution.
- **Manual adjustments**: loyalty officers/managers can add or remove points with a reason; balances never go negative; every adjustment is audited.

## COD Settlement
When a driver completes a COD delivery with the exact amount, one pending settlement is created. A cashier confirms receipt of the physical cash; confirming twice does nothing. This confirmation is also the COD payment-verification step (see Payment Statuses above): it flips the payment `PENDING` → `PAID` and stamps who verified it and when. Drivers see today's collections and their unsettled total.

## Bills & Cashier Finalization Workflow
- **Cashier Bill Generation** (Cashier → Billing → Awaiting Bill Generation): the cashier/manager opens "Generate bill" for an order and enters tax percentage (0–100%, max 2 decimal places) and delivery charge (LKR, non-negative, max 2 decimal places). Both fields start empty; the calculated tax and final net amount are shown live, and **Generate Bill** stays disabled until both values are valid (and, for paid orders, the net amount equals the verified payment).
  - Clicking **Generate Bill** (`POST /api/v1/billing/orders/{orderId}/finalize`) saves the charges on the order and generates the bill in one transaction, for every payment method. The order then leaves *Awaiting Bill Generation* and appears under *Generated Bills*; the PDF download starts automatically.
  - The awaiting list shows unbilled COD, EFT, card-simulation, and PayPal-simulation orders together. No payment method may create its first bill at checkout or bypass these two required fields. Cancelled orders cannot be billed (`ORDER_CANCELLED`).
- **Calculation Formula**:
  - `discountedSubtotal = subtotal - offerDiscount - pointDiscount`
  - `taxAmount = round(discountedSubtotal × taxPercentage / 100, 2)` (using BigDecimal and HALF_UP rounding in Java)
  - `netAmount = discountedSubtotal + taxAmount + deliveryCharge`
- **Payment Consistency & Reconciliation**:
  - Finalizing charges on a `PAID` order verifies that `netAmount == payment.amount`. If they differ, paid-bill generation is blocked with `PAYMENT_RECONCILIATION_REQUIRED` to force payment reconciliation. Payment amounts are never silently overwritten.
  - Generating the bill for a `PENDING`/`FAILED` order sets the expected payment amount to the billed net amount, so EFT verification and COD collection confirm exactly the billed total.
- **COD Restrictions**: COD bills should be generated before collection. If COD cash was collected before the bill was generated, the cashier may only confirm the already stored tax and delivery values; any change is blocked with `COD_ALREADY_SETTLED`.
- **Historical Accuracy**: Issued bills preserve snapshot values (`subtotal`, `offerDiscount`, `pointDiscount`, `taxPercentage`, `taxAmount`, `deliveryCharge`, `amount`). Later order edits do not change previously issued bill details.
- **Duplicate-bill prevention**: bill generation locks the order row; replaying identical charges (double click, network retry) returns the existing bill, while different charges after generation are rejected with `409 BILL_ALREADY_GENERATED` and change nothing. Orders with only voided bills must use **Reissue** (`BILL_REISSUE_REQUIRED`). The database's filtered unique index is the final guard.
  - Simulated card/PayPal payments remain paid at checkout, but their bills stay in the cashier queue until tax and delivery charge are explicitly submitted.
  - Bill views show payment status live, so a bill generated before payment shows `PENDING` until EFT verification or COD cash confirmation marks it `PAID` (`BillingService.createBillForOrder` then returns the existing bill).
  - Cancelling an order whose payment is still `PENDING`/`FAILED` voids its bill ("Order cancelled before payment"). Bills of paid/refunded orders are kept for the refund trail.
- A bill has a lifecycle status, `ACTIVE` or `VOIDED`. At most one `ACTIVE` bill exists per order at the database level (a filtered unique index). Bill charge snapshots are also constrained in the database (`tax_percentage` 0–100, `tax_amount` and `delivery_charge` ≥ 0; see `database/v7_bill_charge_constraints.sql`).
- **Reissue** voids the current active bill (if any) and issues a fresh one in a single operation.
- Only `notes` is ever editable on an existing bill; `billNumber`, `order`, and financial totals are immutable once issued.
- Payment and refund statuses on bill views are read live from Order/Payment/RefundRequest.
- PDF rendering and email attachments include subtotal, offer/point discounts, tax percentage, tax amount, delivery charge, and final net amount.

## Reports
- Date ranges are **inclusive** and use the order creation date in the server's local time zone.
- Sales include every order except `CANCELLED` and `AWAITING_PAYMENT`.
- Refunded total = sum of `REFUNDED` refund requests for those orders; net sales = gross sales − refunded total.
- CSV exports neutralise formula injection (cells starting with `=`, `+`, `-`, `@`, tab or CR are prefixed with `'`) and quote fields containing commas or quotes.
