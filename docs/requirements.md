# RapidMart — System Requirements

## Executive Summary
RapidMart is a complete, monolithic e-commerce and delivery application tailored for modern supermarket chains. The platform facilitates end-to-end user workflows ranging from public product catalog browsing, customer registration, address management, atomic cart checkout, payment handling (COD, EFT, simulated Card/PayPal), delivery assignment, order tracking, COD settlement, loyalty rewards, customer feedback, employee access management, and reporting.

## User Roles & Key Permissions
1. **CUSTOMER**: Browse active catalog, manage personal addresses/profile, manage cart, checkout, view orders, request refunds, earn & redeem loyalty points, submit feedback.
2. **CASHIER**: View dashboard & orders, verify EFT payments, generate bills, process refund requests, confirm COD cash settlements from delivery staff.
3. **INVENTORY_STAFF** (shown as **Inventory Officer**): View stock levels, manage products & categories (create, update, soft-deactivate), create restock requests, receive stock for manager-approved requests, view request and stock movement history. Cannot approve or reject restock requests.
4. **DELIVERY_COORDINATOR**: Create and maintain delivery-staff-only accounts, search/filter drivers, view availability and assignment history, schedule/assign delivery slots, track delivery status, and record failed delivery outcomes.
5. **DELIVERY_STAFF**: View assigned deliveries, record delivery attempts, mark orders out for delivery, collect COD payment, record failed reasons, complete delivery.
6. **LOYALTY_OFFICER**: Manage loyalty tiers, threshold rules, reward points configuration, manual audited reward adjustments, and special promotional offers.
7. **MANAGER**: Comprehensive access including employee creation & role configuration, access activation/deactivation, audit log inspection, management reports (sales summary, inventory status, delivery summary, CSV exports), customer feedback replies, and approving or rejecting restock requests (never their own). Managers do not create restock requests or receive stock.

## Architecture Specifications
- **Technology Stack**: Java 21, Spring Boot 3.x, Spring Web MVC, Spring Data JPA, Spring Security (Server-side Session Auth), Microsoft SQL Server (managed with SSMS via `database/RapidMartDB_SSMS.sql`).
- **Frontend Architecture**: Pure HTML5, CSS3, Vanilla JS ES modules served statically via `src/main/resources/static`.
- **Database Strategy**: One shared Microsoft SQL Server database `RapidMartDB` for all roles (`dev` and `prod` profiles); the `test` profile uses the separate `RapidMartTestDB`. Schema installed manually via canonical script `database/RapidMartDB_SSMS.sql`.
