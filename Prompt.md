# RapidMart — Master Implementation Prompt

Copy the prompt below into a coding AI or agent that can create and edit a complete Maven project.

---

## Prompt

You are a senior Java/Spring Boot architect and full-stack engineer. Build a complete, runnable supermarket ordering and delivery web application named **RapidMart**.

Do not only explain the solution. Create the actual project and implement every required source file, configuration file, database migration, frontend page, test, and document. The finished project must compile, run, and provide working end-to-end flows.

### 1. Technology and architecture

Use:

- Java 21
- Maven Wrapper
- Spring Boot 3.x using a current stable version compatible with Java 21
- Spring Web MVC and REST controllers
- Spring Data JPA
- Spring Security with server-side session authentication
- Bean Validation
- Microsoft SQL Server database (`RapidMartDB` / `RapidMartTestDB`)
- Canonical SSMS database setup script (`database/RapidMartDB_SSMS.sql`)
- JUnit 5, Spring Boot Test, Spring Security Test, MockMvc, and Awaitility or an equivalent approach where useful
- Plain HTML5, CSS3, and JavaScript served from `src/main/resources/static`
- Native ES modules and `fetch()` for REST requests

Do not use Thymeleaf, React, Angular, Vue, Lombok, an external database, or external payment, email, or SMS providers. Do not expose JPA entities directly when a DTO is safer. Use a feature-based package structure under `com.rapidmart`.

Implement a modular monolith with controllers kept thin, business rules in services, persistence in repositories, request/response DTOs at the API boundary, and transactional boundaries around business operations.

### 2. Required user roles

Implement these enum-backed roles and enforce them on both API endpoints and frontend navigation:

- `CUSTOMER`
- `CASHIER`
- `INVENTORY_STAFF`
- `DELIVERY_COORDINATOR`
- `DELIVERY_STAFF`
- `LOYALTY_OFFICER`
- `MANAGER`

Public users may browse active products and categories, register only as customers, log in, and access the CSRF token/session endpoints required by the frontend. Public registration must never accept or infer an employee or manager role.

### 3. Authentication and security

Implement secure customer registration, login, logout, and a current-user endpoint. Use BCrypt password hashing, session fixation protection, secure authorization rules, CSRF protection, and safe error handling. Return JSON responses for authentication success/failure instead of redirects when called through the API.

Only active users may authenticate. Deactivated users must lose access on their next authenticated request, not merely after their next login. Never return password hashes or sensitive internal fields.

Create the first manager only in the local `dev` or `demo` profile:

- Email: `manager@rapidmart.local`
- Password: `DemoManager21!`

Hash the password before storage. Make creation idempotent. Do not hardcode or create this account in production configuration. Explain the demo-only behavior in the README.

### 4. Customer and address management

Customers can view and update their own profiles and create, update, list, and delete only their own addresses. Ownership must always be derived from the authenticated principal; never trust a customer ID supplied by the browser. An address referenced by historical orders must not be hard-deleted in a way that damages history. Orders must store an immutable delivery-address snapshot.

Customers can also view their loyalty account, reward history, eligible offers, submitted feedback, and manager replies.

### 5. Product and category management

Allow public/customer users to:

- Browse active products with pagination
- Search by name
- Filter by active category
- View product details

Allow inventory staff and managers to create and update products and categories. Prefer deactivation over deletion when records may be referenced by transactions. Validate unique names or SKUs where appropriate, positive prices, and valid category relationships.

### 6. Cart

Maintain exactly one cart per customer. Customers can view their cart, add an active product, update quantities, and remove items. Calculate all prices and subtotal on the server from current product data; never trust totals sent by the client. Reject inactive products, non-positive quantities, and quantities above available stock.

Cart validation does not reserve inventory. Revalidate price, availability, and stock inside the checkout transaction.

### 7. Order and checkout

Implement one atomic checkout operation that:

1. Identifies the customer from the authenticated principal.
2. Locks or atomically updates inventory to prevent overselling.
3. Rejects an empty cart.
4. Validates that every cart item is active and sufficiently stocked.
5. Validates that the delivery address belongs to the customer.
6. Validates that the selected delivery date is in the future.
7. Validates the selected payment method.
8. Applies at most one currently eligible special offer.
9. Validates reward-point redemption.
10. Calculates subtotal, offer discount, redeemed-point discount, and final total on the server.
11. Ensures redemption leaves a strictly positive payable total.
12. Stores snapshots of product name, unit price, quantity, line total, and delivery address.
13. Creates the order, order items, payment, and pending delivery record.
14. Deducts inventory and records every stock movement.
15. Clears the cart only after the transaction succeeds.

One reward point equals LKR 1. Use `BigDecimal` for money with an explicit two-decimal scale and consistent rounding. Add a database uniqueness/idempotency safeguard so repeated checkout requests cannot create duplicate orders from the same cart attempt. At minimum, an empty cart must prevent a second checkout.

Preserve these independent order statuses:

- `AWAITING_PAYMENT`
- `CONFIRMED`
- `OUT_FOR_DELIVERY`
- `DELIVERED`
- `CANCELLED`

Define and enforce allowed transitions. A COD order may be `CONFIRMED` while its payment remains `PENDING`.

### 8. Payment

Support these payment methods:

- `COD`
- `EFT`
- `CARD_SIMULATION`
- `PAYPAL_SIMULATION`

CARD and PayPal must be visibly labelled as local simulations in code, API responses, UI, and documentation. Never claim a real card or PayPal transaction occurred, and never store card or PayPal credentials.

Use these payment statuses independently from order and delivery status:

- `PENDING`
- `PAID`
- `FAILED`
- `PARTIALLY_REFUNDED`
- `REFUNDED`

EFT remains pending until a cashier verifies it. COD remains pending until delivery staff records collection of the exact amount. A failed payment must not confirm an order. Retry is allowed only from `FAILED`. Payment confirmation operations must be idempotent and protected against duplicate confirmation.

Do not create a fake external webhook. If a simulation callback endpoint exists, restrict it to the local demo profile, label it clearly, and make it idempotent.

### 9. Billing, refunds, and cancellation

Generate a bill only for a paid order. Each bill must have a unique, immutable bill number, preserve historical monetary values, and remain available as history.

Permit one full-refund request per order. A refund requires an actual paid payment. The cashier verifies eligibility and records a decision. Approval means the money was refunded externally; the application only records that fact and must not claim it transferred funds.

Refund statuses:

- `PENDING`
- `REFUNDED`
- `REJECTED`

Prevent duplicate refund requests and approvals using service validation and database constraints. Reverse only the reward points actually earned from the refunded order, exactly once, without creating a negative balance. If points already spent make a full reversal impossible, reject the refund with a clear business error for manual resolution rather than corrupting the balance.

Implement cancellation with explicit rules. A customer may cancel only before delivery assignment and subject to the documented business rules. A delivery coordinator may cancel after returned items have been checked following a failed delivery. Restore stock and record stock movements exactly once. Repeating cancellation must be idempotent and must not restore stock twice. Document all cancellation rules.

### 10. Inventory and restocking

Maintain exactly one inventory record per product with current quantity, minimum level, maximum level, and optimistic version. Reject negative values, invalid limits, and receipts that would exceed the configured maximum.

Provide low-stock information, update limits, create restock requests, approve/reject or track them as required, receive stock, and show stock history. Requesting stock and receiving stock are separate actions. Normal inventory must not automatically enter the receive-stock workflow.

Record every movement with product, quantity delta, movement type, related business reference where applicable, actor, and timestamp. Include movement types for initial/received stock, checkout deduction, cancellation restoration, and any authorized manual adjustment.

Prevent overselling under concurrent checkout using pessimistic locking, a correct conditional update, or another demonstrably safe strategy. Add a concurrency integration test in which two customers attempt to purchase the last unit and exactly one succeeds.

### 11. Delivery coordination and delivery staff

Delivery coordinators can view orders ready for delivery, view active delivery staff, assign staff, schedule/reschedule delivery, see status, record failed-delivery outcomes, and cancel only after returned items have been checked.

Do not allow unpaid non-COD orders to be assigned. Enforce exact delivery-slot conflict checks for the same staff member, using both service validation and a suitable database uniqueness constraint where possible.

Delivery staff can access only deliveries assigned to them. They can view the snapshotted address and order details, mark a delivery out for delivery, record failed delivery with a required reason, record attempts, and complete delivery.

Use these delivery statuses independently:

- `PENDING`
- `ASSIGNED`
- `OUT_FOR_DELIVERY`
- `DELIVERED`
- `FAILED`
- `CANCELLED`

For COD, delivery staff must record collection of the exact amount before completion. Delivery becomes `DELIVERED` only after payment is `PAID`. On successful completion, create an application receipt record and add loyalty points exactly once. Failed delivery requires a nonblank reason. Keep coordinator and delivery-staff permissions separate.

### 12. COD settlement

When delivery staff collects COD cash, create one pending settlement linked to the payment and delivery. The cashier separately confirms physical receipt of that cash. Delivery staff can view only their unsettled and daily collected totals. Cashier confirmation must be idempotent; a settlement cannot be confirmed twice.

### 13. Employee management

Managers can create employee accounts, update employee details and permitted employee roles, and activate/deactivate access. Revalidate corrected information on update. Preserve employee records referenced by history.

Prevent the authenticated manager from removing their own `MANAGER` role or deactivating their own account. Record employee creation, deactivation, and manager-access changes in the audit log.

### 14. Feedback

Customers can submit and view their own feedback and replies. Managers can list feedback, categorize it, reply, and close it.

Feedback statuses:

- `PENDING`
- `RESPONDED`
- `CLOSED`

Enforce valid state transitions and ownership restrictions.

### 15. Loyalty and offers

Maintain one loyalty account per customer. Loyalty officers and managers can create/update loyalty tiers, required-point thresholds, earning rates, and benefit descriptions. They can also make audited manual reward adjustments and create offers for all customers or eligible tiers.

Record every points change in an immutable reward transaction history with reason, delta, balance-after, actor/reference, and timestamp. Never allow a negative balance. Earn points only once, after successful delivery. Validate tier thresholds, nonnegative earning rates, offer start/end dates, and discount percentages greater than 0 and at most 100. Revalidate all corrected data on update.

### 16. Notifications and audit

Define email and SMS channel abstractions. When no provider is configured, return a truthful `NOT_SENT` result and never log or display a false success message.

Audit at least:

- Manager-role/access changes
- Employee creation and deactivation
- Manual reward-point adjustments
- Externally completed refunds

Audit records must include action, actor, target/reference, safe metadata, and timestamp. Never store passwords, password hashes, authentication secrets, payment credentials, or other sensitive details in audit data.

### 17. Reports

Provide manager-accessible reports for:

- Sales summary
- Inventory summary
- Delivery summary
- CSV sales export

Support validated date ranges. Clearly document whether dates are inclusive, which timestamp and timezone are used, which order/payment states count as sales, and how refunded totals and net sales are calculated. Set correct CSV content type and download headers and prevent CSV formula injection. Do not claim PDF or Excel export exists.

### 18. Frontend

Serve all pages through Spring Boot; users must not open HTML files directly. Build responsive, accessible pages with role-aware navigation and understandable loading, success, validation, authorization, and API error states.

Frontend rules:

- Use semantic HTML and visible labels for every input.
- Use `fetch()` through a shared API module.
- Include session credentials and CSRF tokens correctly for state-changing requests.
- Disable submit/action buttons while requests are running.
- Add directionally correct symbols to sequential navigation where appropriate: use `← Previous` for backward actions and `Next →` for forward actions, including pagination and multi-step flows. Keep the visible text (or an equivalent accessible name); do not use unexplained icon-only controls.
- Use `textContent`, DOM creation, or safe attributes for user-controlled values; do not inject them through unsafe `innerHTML`.
- Do not store passwords, roles, or authoritative access decisions in local storage.
- Redirect unauthenticated users to login and show a clear access-denied page for unauthorized roles.
- Keep server-side authorization authoritative even when UI elements are hidden.

Create and wire these pages:

- Public/customer: home, login, registration, catalogue, product details, cart, checkout, order confirmation, order list, order details, profile/addresses, rewards/offers, feedback
- Cashier: dashboard, bills, EFT/payment verification, refunds, COD settlements
- Inventory: dashboard, products, categories, stock levels, restock requests, receive stock, stock history
- Delivery coordinator: dashboard, assignments, schedule/reschedule, tracking, failed deliveries
- Delivery staff: dashboard, assigned deliveries, delivery details, daily COD summary
- Manager: dashboard, employees, access control, reports, customer feedback
- Loyalty officer: dashboard, loyalty tiers, reward adjustments/history, special offers

Every page must make real REST calls and support its main workflow. Do not create decorative pages with nonfunctional buttons.

### 19. Data model and persistence rules

All persistent entities should inherit suitable audit timestamps from `BaseEntity`. Use Java time classes (`LocalDate`, `LocalDateTime`, or `Instant`) consistently and inject a `Clock` into time-sensitive services for deterministic testing. Document the application timezone.

Use:

- `BigDecimal` for money and rates
- String persistence for enums
- `@Version` where concurrent updates are possible
- Explicit unique constraints and foreign keys
- Bean Validation plus database constraints as a second layer
- Purposeful lazy-loading and DTO mapping inside transactions
- Pagination for potentially large lists

Avoid unsafe cascade deletes. Preserve product, price, address, payment, bill, refund, delivery, and reward history. Add indexes for common lookup fields such as normalized email, status, order number, product/category, delivery staff/date/slot, and timestamps used in reports.

### 20. Error contract and API quality

Use a consistent JSON error response containing timestamp, HTTP status, stable error code, user-safe message, field errors when applicable, and request path. Map validation, authentication, authorization, not-found, conflict/idempotency, and business-rule failures to appropriate HTTP statuses. Do not expose stack traces or database messages.

Document all REST endpoints, request/response DTOs, role permissions, important status transitions, and example payloads in `docs/api-endpoints.md`.

### 21. Configuration profiles

Default configuration must not silently use production settings.

Development profile:

- Persistent file-based H2 data under `./data/`
- H2 Console enabled only for local development
- Flyway enabled
- Demo manager initializer enabled

Test profile:

- Fresh in-memory H2 database with a unique test URL
- H2 Console disabled
- Never connects to development data
- If Hibernate generates the test schema, disable Flyway for tests; otherwise use Flyway consistently, but do not mix competing schema owners

Production profile:

- No demo accounts
- H2 Console disabled
- No simulated callback endpoints
- Secrets supplied externally
- Safe schema-validation/migration behavior

Do not commit generated H2 database files, logs, build output, or secrets.

### 22. Required project structure

Use the supplied `rapid-mart` structure as the baseline. Keep its feature packages:

`config`, `common`, `auth`, `user`, `customer`, `product`, `cart`, `order`, `payment`, `billing`, `refund`, `inventory`, `delivery`, `employee`, `feedback`, `report`, `loyalty`, `notification`, and `audit`.

Within each feature, use the necessary `controller`, `service`, `repository`, `entity`, `dto`, `enums`, and mapper/validation subpackages. Keep the listed static role-based page folders and shared JavaScript/CSS asset folders. Add any missing files required for correctness, including—but not limited to—security handlers, CSRF response DTO/controller, receipt support, COD settlement DTOs/controllers, status enums, audit DTOs, mappers, initializers, projections, and extra tests.

Required root/resource files include:

- `pom.xml`, Maven wrapper files, `.gitignore`, and `README.md`
- `application.properties`, `application-dev.properties`, `application-test.properties`, and `application-prod.properties`
- Ordered Flyway migrations for users/profiles, catalogue/inventory, carts/orders, payments/bills/refunds, deliveries/settlements/receipts, loyalty/offers, and feedback/audit
- Documentation under `docs/`: requirements, business rules, API endpoints, team responsibilities, diagrams placeholders/source files, and manual test cases

Do not commit `target/`; it is generated output.

### 23. Minimum automated tests

Write meaningful unit/service and integration tests. Test behavior and authorization, not only happy paths. Include at least:

- Duplicate email registration
- Public registration cannot create a manager or employee
- Login/current-user/logout and CSRF behavior
- Disabled-account access denial
- Endpoint access for every role, including forbidden cross-role attempts
- Customer profile/address/order/feedback ownership restrictions
- Empty-cart checkout
- Inactive/unavailable product and insufficient inventory
- Concurrent purchase of the last unit: one success only
- Duplicate checkout prevention
- Server-side recalculation rather than trusting client totals
- Invalid address ownership and non-future delivery date
- Invalid or expired offer and excessive point redemption
- EFT/COD payment state rules and duplicate confirmation
- Payment retry allowed only after failure
- Bill generation only for paid orders and unique bill number
- Unpaid non-COD order cannot be assigned
- Delivery-slot conflict
- Delivery-staff assignment ownership
- Failed delivery requires a reason
- COD exact payment required before delivery completion
- Loyalty points earned once after delivery
- Cancellation restores stock exactly once, including repeated cancellation
- Refund eligibility, status transitions, duplicate prevention, and safe reward reversal
- Stock receipt limits and movement history
- Employee self-demotion/self-deactivation prevention
- Truthful notification fallback
- Report date/refund calculations and CSV output safety

Use deterministic clocks and isolated test data. The full suite must run with `./mvnw test`.

### 24. Documentation

The README must include prerequisites, profile selection, how to run on Windows/Linux/macOS, application URL, H2 Console details for development, demo credentials, how to run tests, security limitations of local simulations, and troubleshooting.

Document:

- Role/permission matrix
- Entity relationships and important uniqueness rules
- Order/payment/delivery/refund state transitions
- Checkout transaction and inventory-locking strategy
- Cancellation, refunds, points, stock, COD collection/settlement, and report calculations
- Which notification and payment behaviors are simulations
- Manual end-to-end test cases

Use Mermaid or PlantUML source files for the use-case, activity, class, and ER diagrams when practical.

### 25. Implementation quality rules

- No placeholder methods, TODO-only implementations, fake successful responses, empty HTML shells, or pseudocode.
- Do not omit imports or shorten code with comments such as “remaining methods here.”
- Prefer simple, maintainable code appropriate for a university team project.
- Use constructor injection.
- Keep controllers thin and avoid circular dependencies.
- Add comments only where they explain non-obvious business rules or concurrency decisions.
- Do not expose secrets or sensitive data.
- Avoid N+1 queries in major list/detail flows.
- Make business actions idempotent where repetition could duplicate money, stock, rewards, refunds, settlements, or status changes.
- If a requirement is ambiguous, choose the safest reasonable behavior, implement it consistently, and document the decision instead of silently skipping it.

### 26. Required execution sequence

Work in verifiable phases, but continue until the full project is complete:

1. Inspect the existing repository, preserve useful files, and present a concise implementation checklist.
2. Finalize the domain model, permission matrix, status-transition tables, API contract, and database constraints.
3. Create the Maven/configuration/security foundation and migrations.
4. Implement backend features in dependency order: identity/customer, catalogue/inventory, cart/checkout/order, payment/billing/refund, delivery/COD settlement, loyalty, feedback, employees/audit, reports/notifications.
5. Implement and connect all frontend pages.
6. Add tests as each feature is implemented.
7. Run formatting if configured, then run `./mvnw clean test`.
8. Start the app with the development profile and perform a smoke test of registration/login, catalogue/cart/checkout, employee flows, and role navigation.
9. Fix all compilation, migration, test, browser-console, and runtime errors.
10. Update documentation to match the final implementation.

After each phase, state which files were created or changed and report verification results. Do not stop after scaffolding. If output limits prevent completion in one response, continue from the exact next unfinished file without regenerating or contradicting completed work.

### 27. Final acceptance criteria

The task is complete only when:

- The project builds with Java 21 using the Maven Wrapper.
- All automated tests pass on a clean test database.
- The development application starts with persistent file-based H2.
- The demo manager exists only in the development/demo profile.
- Every listed role has working authorized pages and APIs for its responsibilities.
- Core customer checkout, inventory, payment, delivery, refund, loyalty, feedback, employee, reporting, audit, and COD settlement flows work end to end.
- Concurrent checkout cannot oversell stock.
- Duplicate requests cannot duplicate stock changes, payment confirmation, refunds, cancellations, rewards, bills, receipts, or settlements.
- Historical transactional values remain correct after product, address, employee, tier, or offer changes.
- The UI contains no knowingly nonfunctional primary actions.
- The README and docs accurately describe the implemented behavior and any explicitly labelled local simulations.

At the end, provide:

1. A concise architecture summary.
2. The final project tree.
3. Commands to run the app and tests.
4. Demo login details with a development-only warning.
5. Test results and smoke-test results.
6. Any remaining limitations. Do not claim a feature is complete if it is not implemented and verified.

---

## Short analysis of the source specification

The source defines a substantial role-based e-commerce and delivery system with strong business rules. Its best features are the explicit separation of order, payment, and delivery states; truthful treatment of simulated integrations; atomic stock requirements; and a broad testing checklist.

The original text was closer to a requirements list than an implementation prompt. This version resolves that by adding a role model, access-control expectations, valid transition enforcement, idempotency requirements, a consistent API error contract, a safe session/CSRF approach, missing receipt and COD-settlement artifacts, database constraints, clear configuration ownership, a phased execution plan, and measurable acceptance criteria. The supplied package/page structure remains the baseline, but the coding agent is explicitly allowed to add files required for a correct implementation.
