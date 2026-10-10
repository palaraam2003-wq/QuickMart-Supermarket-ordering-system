-- ============================================================================
-- RAPIDMART SUPERMARKET ORDERING & DELIVERY APPLICATION
-- CANONICAL DATABASE CREATION & SETUP SCRIPT (Microsoft SQL Server / SSMS)
-- ============================================================================
--
-- This script is the single canonical source of truth for the RapidMartDB schema.
-- Flyway and H2 have been removed. Install this script in SSMS before starting the app.
--
-- SAFE TO RERUN: every table, column, constraint and index is created only when it is
-- missing, and every seed row is inserted only when it is missing. Existing data is never
-- dropped, truncated or overwritten, and the database itself is never dropped or recreated.
-- Rerunning it against an older RapidMartDB also upgrades it to the current schema.
--
-- INSTRUCTIONS FOR SSMS:
--   1. Open this file in SQL Server Management Studio (SSMS).
--   2. Review the settings below (plain T-SQL; SQLCMD Mode is not needed).
--   3. Click Execute (F5).
--
-- SETTINGS:
--   @DatabaseName  target database. To install the test database, set it to 'RapidMartTestDB'
--                  AND change the USE [RapidMartDB] line below to USE [RapidMartTestDB].
--   @SeedDemoData  1 adds the disposable demo staff/customers/orders (public passwords,
--                  never use in production). 0 installs schema + product catalogue only.

-- Clear NOEXEC left on by an earlier failed run in this same query window.
SET NOEXEC OFF;
GO

DECLARE @DatabaseName SYSNAME = N'RapidMartDB';
DECLARE @SeedDemoData BIT = 1;

-- Session context carries the settings across the GO batches below.
EXEC sys.sp_set_session_context @key = N'RapidMart.DatabaseName', @value = @DatabaseName;
EXEC sys.sp_set_session_context @key = N'RapidMart.SeedDemoData', @value = @SeedDemoData;

IF DB_ID(@DatabaseName) IS NULL
BEGIN
    DECLARE @CreateDatabaseSql NVARCHAR(500) = N'CREATE DATABASE ' + QUOTENAME(@DatabaseName);
    EXEC sys.sp_executesql @CreateDatabaseSql;
END;
GO

USE [RapidMartDB];
GO

-- Stop before touching anything if the USE line above does not match @DatabaseName.
IF DB_NAME() <> CAST(SESSION_CONTEXT(N'RapidMart.DatabaseName') AS SYSNAME)
BEGIN
    RAISERROR(N'The USE line does not match @DatabaseName at the top of the script. Make them the same and execute again. Nothing was changed.', 16, 1);
    SET NOEXEC ON;
END;
GO
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

-- ===== V1__init_users_roles.sql =====
IF OBJECT_ID(N'dbo.app_users', N'U') IS NULL
CREATE TABLE app_users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    active BIT NOT NULL DEFAULT 1,
    last_login_at DATETIMEOFFSET,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

IF OBJECT_ID(N'dbo.customer_password_resets', N'U') IS NULL
CREATE TABLE customer_password_resets (
    user_id BIGINT NOT NULL PRIMARY KEY,
    code_hash VARCHAR(100) NOT NULL,
    requested_at DATETIMEOFFSET NOT NULL,
    expires_at DATETIMEOFFSET NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT ck_password_reset_attempts CHECK (attempts >= 0)
);

IF OBJECT_ID(N'dbo.user_roles', N'U') IS NULL
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

GO

-- ===== V2__init_catalogue_inventory.sql =====
IF OBJECT_ID(N'dbo.categories', N'U') IS NULL
CREATE TABLE categories (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

IF OBJECT_ID(N'dbo.products', N'U') IS NULL
CREATE TABLE products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(MAX),
    price DECIMAL(12,2) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    image_url VARCHAR(500),
    category_id BIGINT NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);

IF OBJECT_ID(N'dbo.inventory', N'U') IS NULL
CREATE TABLE inventory (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    quantity INT NOT NULL DEFAULT 0,
    minimum_level INT NOT NULL DEFAULT 10,
    maximum_level INT NOT NULL DEFAULT 1000,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT ck_inventory_quantity_nonnegative CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_minimum_nonnegative CHECK (minimum_level >= 0),
    CONSTRAINT ck_inventory_maximum_valid CHECK (maximum_level >= minimum_level)
);

IF OBJECT_ID(N'dbo.stock_movement_history', N'U') IS NULL
CREATE TABLE stock_movement_history (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    quantity_delta INT NOT NULL,
    movement_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(255),
    actor_id BIGINT,
    actor_email VARCHAR(255),
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES products(id)
);

IF OBJECT_ID(N'dbo.restock_requests', N'U') IS NULL
CREATE TABLE restock_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    requested_quantity INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    requested_by_id BIGINT NOT NULL,
    approved_by_id BIGINT,
    received_quantity INT,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_restock_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_restock_requester FOREIGN KEY (requested_by_id) REFERENCES app_users(id),
    CONSTRAINT fk_restock_approver FOREIGN KEY (approved_by_id) REFERENCES app_users(id),
    CONSTRAINT ck_restock_requested_positive CHECK (requested_quantity > 0),
    CONSTRAINT ck_restock_received_nonnegative CHECK (received_quantity IS NULL OR received_quantity >= 0)
);

GO

-- ===== V3__init_cart_orders.sql =====
IF OBJECT_ID(N'dbo.customer_addresses', N'U') IS NULL
CREATE TABLE customer_addresses (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    phone_contact VARCHAR(50) NOT NULL,
    is_default BIT NOT NULL DEFAULT 0,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

IF OBJECT_ID(N'dbo.carts', N'U') IS NULL
CREATE TABLE carts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

IF OBJECT_ID(N'dbo.cart_items', N'U') IS NULL
CREATE TABLE cart_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uk_cart_product UNIQUE(cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity_positive CHECK (quantity > 0)
);

IF OBJECT_ID(N'dbo.orders', N'U') IS NULL
CREATE TABLE orders (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AWAITING_PAYMENT',
    delivery_date DATE NOT NULL,
    delivery_slot VARCHAR(50) NOT NULL,
    delivery_street VARCHAR(255) NOT NULL,
    delivery_city VARCHAR(100) NOT NULL,
    delivery_postal_code VARCHAR(20) NOT NULL,
    delivery_phone VARCHAR(50) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    offer_discount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    redeemed_points INT NOT NULL DEFAULT 0,
    point_discount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    tax_percentage DECIMAL(5,2) NULL DEFAULT 0.00,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    delivery_charge DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL,
    applied_offer_id BIGINT,
    idempotency_key VARCHAR(255),
    finalized_by_id BIGINT,
    finalized_at DATETIMEOFFSET,
    is_finalized BIT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES app_users(id),
    CONSTRAINT fk_orders_finalized_by FOREIGN KEY (finalized_by_id) REFERENCES app_users(id),
    CONSTRAINT ck_orders_subtotal_nonnegative CHECK (subtotal >= 0),
    CONSTRAINT ck_orders_offer_discount_nonnegative CHECK (offer_discount >= 0),
    CONSTRAINT ck_orders_redeemed_points_nonnegative CHECK (redeemed_points >= 0),
    CONSTRAINT ck_orders_point_discount_nonnegative CHECK (point_discount >= 0),
    CONSTRAINT ck_orders_tax_percentage CHECK (tax_percentage IS NULL OR (tax_percentage >= 0 AND tax_percentage <= 100)),
    CONSTRAINT ck_orders_tax_nonnegative CHECK (tax_amount >= 0),
    CONSTRAINT ck_orders_delivery_charge_nonnegative CHECK (delivery_charge >= 0),
    CONSTRAINT ck_orders_total_positive CHECK (total_amount > 0)
);

IF OBJECT_ID(N'dbo.order_items', N'U') IS NULL
CREATE TABLE order_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    unit_price_snapshot DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_nonnegative CHECK (unit_price_snapshot >= 0),
    CONSTRAINT ck_order_items_line_total_nonnegative CHECK (line_total >= 0)
);

GO

-- ===== V4__init_payments_bills_refunds.sql =====
IF OBJECT_ID(N'dbo.payments', N'U') IS NULL
CREATE TABLE payments (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    amount DECIMAL(12,2) NOT NULL,
    transaction_reference VARCHAR(255),
    notes VARCHAR(500),
    verified_by_id BIGINT,
    verified_at DATETIMEOFFSET,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_payments_verified_by FOREIGN KEY (verified_by_id) REFERENCES app_users(id),
    CONSTRAINT ck_payments_amount_nonnegative CHECK (amount >= 0)
);

IF OBJECT_ID(N'dbo.bills', N'U') IS NULL
CREATE TABLE bills (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    bill_number VARCHAR(100) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2),
    offer_discount DECIMAL(12,2),
    point_discount DECIMAL(12,2),
    tax_percentage DECIMAL(5,2),
    tax_amount DECIMAL(12,2),
    delivery_charge DECIMAL(12,2),
    issued_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    issued_by_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    voided_at DATETIMEOFFSET,
    voided_by_id BIGINT,
    void_reason VARCHAR(500),
    notes VARCHAR(500),
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_bills_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_bills_issued_by FOREIGN KEY (issued_by_id) REFERENCES app_users(id),
    CONSTRAINT fk_bills_voided_by FOREIGN KEY (voided_by_id) REFERENCES app_users(id),
    CONSTRAINT ck_bills_amount_nonnegative CHECK (amount >= 0),
    CONSTRAINT ck_bills_tax_percentage CHECK (tax_percentage IS NULL OR (tax_percentage >= 0 AND tax_percentage <= 100)),
    CONSTRAINT ck_bills_tax_amount_nonnegative CHECK (tax_amount IS NULL OR tax_amount >= 0),
    CONSTRAINT ck_bills_delivery_charge_nonnegative CHECK (delivery_charge IS NULL OR delivery_charge >= 0),
    CONSTRAINT ck_bills_status CHECK (status IN ('ACTIVE', 'VOIDED'))
);

IF OBJECT_ID(N'dbo.refund_requests', N'U') IS NULL
CREATE TABLE refund_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    refund_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    refund_amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(MAX) NOT NULL,
    cashier_notes VARCHAR(MAX),
    processed_by_id BIGINT,
    points_reversed INT NOT NULL DEFAULT 0,
    refunded_amount DECIMAL(12,2),
    processed_at DATETIMEOFFSET,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_refund_user FOREIGN KEY (user_id) REFERENCES app_users(id),
    CONSTRAINT fk_refund_processor FOREIGN KEY (processed_by_id) REFERENCES app_users(id),
    CONSTRAINT ck_refund_requested_amount_positive CHECK (refund_amount > 0),
    CONSTRAINT ck_refund_actual_amount_positive CHECK (refunded_amount IS NULL OR refunded_amount > 0),
    CONSTRAINT ck_refund_points_nonnegative CHECK (points_reversed >= 0)
);

IF OBJECT_ID(N'dbo.refund_status_history', N'U') IS NULL
CREATE TABLE refund_status_history (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    refund_request_id BIGINT NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    refund_amount DECIMAL(12,2),
    reason VARCHAR(MAX),
    changed_by_id BIGINT NOT NULL,
    changed_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_refund_history_request FOREIGN KEY (refund_request_id) REFERENCES refund_requests(id),
    CONSTRAINT fk_refund_history_actor FOREIGN KEY (changed_by_id) REFERENCES app_users(id),
    CONSTRAINT ck_refund_history_amount_positive CHECK (refund_amount IS NULL OR refund_amount > 0)
);

GO

-- ===== V5__init_delivery_settlements.sql =====
IF OBJECT_ID(N'dbo.deliveries', N'U') IS NULL
CREATE TABLE deliveries (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    assigned_staff_id BIGINT,
    delivery_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    scheduled_date DATE NOT NULL,
    scheduled_slot VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(MAX),
    returned_items_checked BIT NOT NULL DEFAULT 0,
    completed_at DATETIMEOFFSET,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_delivery_staff FOREIGN KEY (assigned_staff_id) REFERENCES app_users(id)
);

IF OBJECT_ID(N'dbo.delivery_attempts', N'U') IS NULL
CREATE TABLE delivery_attempts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    attempt_timestamp DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    status VARCHAR(50) NOT NULL,
    notes VARCHAR(MAX),
    CONSTRAINT fk_attempt_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries(id) ON DELETE CASCADE
);

IF OBJECT_ID(N'dbo.cod_settlements', N'U') IS NULL
CREATE TABLE cod_settlements (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    delivery_id BIGINT NOT NULL UNIQUE,
    delivery_staff_id BIGINT NOT NULL,
    collected_amount DECIMAL(12,2) NOT NULL,
    confirmed_by_cashier BIT NOT NULL DEFAULT 0,
    cashier_id BIGINT,
    settled_at DATETIMEOFFSET,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_settlement_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries(id),
    CONSTRAINT fk_settlement_staff FOREIGN KEY (delivery_staff_id) REFERENCES app_users(id),
    CONSTRAINT fk_settlement_cashier FOREIGN KEY (cashier_id) REFERENCES app_users(id),
    CONSTRAINT ck_cod_collected_nonnegative CHECK (collected_amount >= 0)
);

IF OBJECT_ID(N'dbo.driver_slot_limits', N'U') IS NULL
CREATE TABLE driver_slot_limits (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    scheduled_slot VARCHAR(50) NOT NULL,
    max_orders INT NOT NULL DEFAULT 1,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_driver_limit_user FOREIGN KEY (driver_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT uk_driver_slot_limit UNIQUE (driver_id, scheduled_date, scheduled_slot),
    CONSTRAINT ck_driver_limit_nonnegative CHECK (max_orders >= 0)
);

GO

-- ===== V6__init_loyalty_offers_feedback.sql =====
IF OBJECT_ID(N'dbo.loyalty_tiers', N'U') IS NULL
CREATE TABLE loyalty_tiers (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    tier_name VARCHAR(50) NOT NULL UNIQUE,
    minimum_points INT NOT NULL,
    earning_rate_multiplier DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    benefits_description VARCHAR(MAX),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT ck_loyalty_tier_minimum_nonnegative CHECK (minimum_points >= 0),
    CONSTRAINT ck_loyalty_tier_multiplier_nonnegative CHECK (earning_rate_multiplier >= 0)
);

IF OBJECT_ID(N'dbo.loyalty_accounts', N'U') IS NULL
CREATE TABLE loyalty_accounts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    current_points INT NOT NULL DEFAULT 0,
    lifetime_points INT NOT NULL DEFAULT 0,
    current_tier_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_loyalty_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_loyalty_tier FOREIGN KEY (current_tier_id) REFERENCES loyalty_tiers(id),
    CONSTRAINT ck_loyalty_current_points_nonnegative CHECK (current_points >= 0),
    CONSTRAINT ck_loyalty_lifetime_points_nonnegative CHECK (lifetime_points >= 0)
);

IF OBJECT_ID(N'dbo.reward_transactions', N'U') IS NULL
CREATE TABLE reward_transactions (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    loyalty_account_id BIGINT NOT NULL,
    points_delta INT NOT NULL,
    balance_after INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    reference_id VARCHAR(255),
    actor_id BIGINT,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_reward_account FOREIGN KEY (loyalty_account_id) REFERENCES loyalty_accounts(id) ON DELETE CASCADE,
    CONSTRAINT ck_reward_balance_nonnegative CHECK (balance_after >= 0)
);

IF OBJECT_ID(N'dbo.offers', N'U') IS NULL
CREATE TABLE offers (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_percentage DECIMAL(5,2) NOT NULL,
    min_order_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    eligible_tier_id BIGINT,
    active BIT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_offer_tier FOREIGN KEY (eligible_tier_id) REFERENCES loyalty_tiers(id),
    CONSTRAINT ck_offer_discount_percentage CHECK (discount_percentage > 0 AND discount_percentage <= 100),
    CONSTRAINT ck_offer_minimum_amount_nonnegative CHECK (min_order_amount >= 0),
    CONSTRAINT ck_offer_date_range CHECK (end_date >= start_date)
);

IF OBJECT_ID(N'dbo.feedbacks', N'U') IS NULL
CREATE TABLE feedbacks (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(100) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message VARCHAR(MAX) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

IF OBJECT_ID(N'dbo.feedback_replies', N'U') IS NULL
CREATE TABLE feedback_replies (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    feedback_id BIGINT NOT NULL,
    replied_by_id BIGINT NOT NULL,
    reply_message VARCHAR(MAX) NOT NULL,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_reply_feedback FOREIGN KEY (feedback_id) REFERENCES feedbacks(id) ON DELETE CASCADE,
    CONSTRAINT fk_reply_manager FOREIGN KEY (replied_by_id) REFERENCES app_users(id)
);

GO

-- ===== V7__init_audit_notifications.sql =====
IF OBJECT_ID(N'dbo.audit_logs', N'U') IS NULL
CREATE TABLE audit_logs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    target_reference VARCHAR(255),
    details VARCHAR(MAX),
    [timestamp] DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

IF OBJECT_ID(N'dbo.notification_logs', N'U') IS NULL
CREATE TABLE notification_logs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    subject VARCHAR(255),
    message VARCHAR(MAX) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_SENT',
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

-- ===== V10__manager_profile.sql =====
IF OBJECT_ID(N'dbo.email_change_requests', N'U') IS NULL
CREATE TABLE email_change_requests (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    new_email VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_email_change_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

GO

-- ===== UPGRADE EXISTING DATABASES =====
-- Columns that later migrations (V9, V10, V11, cashier-billing v6/v7) added to tables that
-- may already exist from an older install. Each one is added only when it is missing.
IF COL_LENGTH(N'dbo.app_users', N'last_login_at') IS NULL         ALTER TABLE app_users ADD last_login_at DATETIMEOFFSET NULL;
IF COL_LENGTH(N'dbo.loyalty_tiers', N'version') IS NULL           ALTER TABLE loyalty_tiers ADD version BIGINT NOT NULL DEFAULT 0;
IF COL_LENGTH(N'dbo.loyalty_accounts', N'version') IS NULL        ALTER TABLE loyalty_accounts ADD version BIGINT NOT NULL DEFAULT 0;
IF COL_LENGTH(N'dbo.offers', N'version') IS NULL                  ALTER TABLE offers ADD version BIGINT NOT NULL DEFAULT 0;

IF COL_LENGTH(N'dbo.orders', N'tax_percentage') IS NULL           ALTER TABLE orders ADD tax_percentage DECIMAL(5,2) NULL DEFAULT 0.00;
IF COL_LENGTH(N'dbo.orders', N'tax_amount') IS NULL               ALTER TABLE orders ADD tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;
IF COL_LENGTH(N'dbo.orders', N'delivery_charge') IS NULL          ALTER TABLE orders ADD delivery_charge DECIMAL(12,2) NOT NULL DEFAULT 0.00;
IF COL_LENGTH(N'dbo.orders', N'finalized_by_id') IS NULL          ALTER TABLE orders ADD finalized_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.orders', N'finalized_at') IS NULL             ALTER TABLE orders ADD finalized_at DATETIMEOFFSET NULL;
IF COL_LENGTH(N'dbo.orders', N'is_finalized') IS NULL             ALTER TABLE orders ADD is_finalized BIT NOT NULL DEFAULT 0;

IF COL_LENGTH(N'dbo.payments', N'notes') IS NULL                  ALTER TABLE payments ADD notes VARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.payments', N'verified_by_id') IS NULL         ALTER TABLE payments ADD verified_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.payments', N'verified_at') IS NULL            ALTER TABLE payments ADD verified_at DATETIMEOFFSET NULL;

IF COL_LENGTH(N'dbo.bills', N'subtotal') IS NULL                  ALTER TABLE bills ADD subtotal DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.bills', N'offer_discount') IS NULL            ALTER TABLE bills ADD offer_discount DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.bills', N'point_discount') IS NULL            ALTER TABLE bills ADD point_discount DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.bills', N'tax_percentage') IS NULL            ALTER TABLE bills ADD tax_percentage DECIMAL(5,2) NULL;
IF COL_LENGTH(N'dbo.bills', N'tax_amount') IS NULL                ALTER TABLE bills ADD tax_amount DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.bills', N'delivery_charge') IS NULL           ALTER TABLE bills ADD delivery_charge DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.bills', N'issued_by_id') IS NULL              ALTER TABLE bills ADD issued_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.bills', N'status') IS NULL                    ALTER TABLE bills ADD status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
IF COL_LENGTH(N'dbo.bills', N'voided_at') IS NULL                 ALTER TABLE bills ADD voided_at DATETIMEOFFSET NULL;
IF COL_LENGTH(N'dbo.bills', N'voided_by_id') IS NULL              ALTER TABLE bills ADD voided_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.bills', N'void_reason') IS NULL               ALTER TABLE bills ADD void_reason VARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.bills', N'notes') IS NULL                     ALTER TABLE bills ADD notes VARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.bills', N'created_at') IS NULL                ALTER TABLE bills ADD created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET();
IF COL_LENGTH(N'dbo.bills', N'updated_at') IS NULL                ALTER TABLE bills ADD updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET();

IF COL_LENGTH(N'dbo.refund_requests', N'cashier_notes') IS NULL   ALTER TABLE refund_requests ADD cashier_notes VARCHAR(MAX) NULL;
IF COL_LENGTH(N'dbo.refund_requests', N'processed_by_id') IS NULL ALTER TABLE refund_requests ADD processed_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.refund_requests', N'points_reversed') IS NULL ALTER TABLE refund_requests ADD points_reversed INT NOT NULL DEFAULT 0;
IF COL_LENGTH(N'dbo.refund_requests', N'refunded_amount') IS NULL ALTER TABLE refund_requests ADD refunded_amount DECIMAL(12,2) NULL;
IF COL_LENGTH(N'dbo.refund_requests', N'processed_at') IS NULL    ALTER TABLE refund_requests ADD processed_at DATETIMEOFFSET NULL;
GO

-- Constraints on the upgraded columns (separate batch: the columns must exist at compile time).
IF OBJECT_ID(N'dbo.fk_orders_finalized_by', N'F') IS NULL
    ALTER TABLE orders ADD CONSTRAINT fk_orders_finalized_by FOREIGN KEY (finalized_by_id) REFERENCES app_users(id);
IF OBJECT_ID(N'dbo.ck_orders_tax_percentage', N'C') IS NULL
    ALTER TABLE orders ADD CONSTRAINT ck_orders_tax_percentage CHECK (tax_percentage IS NULL OR (tax_percentage >= 0 AND tax_percentage <= 100));
IF OBJECT_ID(N'dbo.ck_orders_tax_nonnegative', N'C') IS NULL
    ALTER TABLE orders ADD CONSTRAINT ck_orders_tax_nonnegative CHECK (tax_amount >= 0);
IF OBJECT_ID(N'dbo.ck_orders_delivery_charge_nonnegative', N'C') IS NULL
    ALTER TABLE orders ADD CONSTRAINT ck_orders_delivery_charge_nonnegative CHECK (delivery_charge >= 0);
IF OBJECT_ID(N'dbo.fk_payments_verified_by', N'F') IS NULL
    ALTER TABLE payments ADD CONSTRAINT fk_payments_verified_by FOREIGN KEY (verified_by_id) REFERENCES app_users(id);
IF OBJECT_ID(N'dbo.fk_bills_issued_by', N'F') IS NULL
    ALTER TABLE bills ADD CONSTRAINT fk_bills_issued_by FOREIGN KEY (issued_by_id) REFERENCES app_users(id);
IF OBJECT_ID(N'dbo.fk_bills_voided_by', N'F') IS NULL
    ALTER TABLE bills ADD CONSTRAINT fk_bills_voided_by FOREIGN KEY (voided_by_id) REFERENCES app_users(id);
IF OBJECT_ID(N'dbo.ck_bills_status', N'C') IS NULL
    ALTER TABLE bills ADD CONSTRAINT ck_bills_status CHECK (status IN ('ACTIVE', 'VOIDED'));
IF OBJECT_ID(N'dbo.ck_bills_tax_percentage', N'C') IS NULL
    ALTER TABLE bills ADD CONSTRAINT ck_bills_tax_percentage CHECK (tax_percentage IS NULL OR (tax_percentage >= 0 AND tax_percentage <= 100));
IF OBJECT_ID(N'dbo.ck_bills_tax_amount_nonnegative', N'C') IS NULL
    ALTER TABLE bills ADD CONSTRAINT ck_bills_tax_amount_nonnegative CHECK (tax_amount IS NULL OR tax_amount >= 0);
IF OBJECT_ID(N'dbo.ck_bills_delivery_charge_nonnegative', N'C') IS NULL
    ALTER TABLE bills ADD CONSTRAINT ck_bills_delivery_charge_nonnegative CHECK (delivery_charge IS NULL OR delivery_charge >= 0);
IF OBJECT_ID(N'dbo.fk_refund_processor', N'F') IS NULL
    ALTER TABLE refund_requests ADD CONSTRAINT fk_refund_processor FOREIGN KEY (processed_by_id) REFERENCES app_users(id);
IF OBJECT_ID(N'dbo.ck_refund_actual_amount_positive', N'C') IS NULL
    ALTER TABLE refund_requests ADD CONSTRAINT ck_refund_actual_amount_positive CHECK (refunded_amount IS NULL OR refunded_amount > 0);
IF OBJECT_ID(N'dbo.ck_refund_points_nonnegative', N'C') IS NULL
    ALTER TABLE refund_requests ADD CONSTRAINT ck_refund_points_nonnegative CHECK (points_reversed >= 0);

-- V11 allows a voided bill and its reissue to share an order, so an old unconditional
-- UNIQUE(order_id) on bills is replaced by the filtered uk_bills_order_active index below.
DECLARE @OldBillOrderUnique SYSNAME = (
    SELECT TOP 1 kc.name FROM sys.key_constraints kc
    JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
    WHERE kc.parent_object_id = OBJECT_ID(N'dbo.bills') AND kc.type = 'UQ'
      AND COL_NAME(ic.object_id, ic.column_id) = 'order_id'
      AND (SELECT COUNT(*) FROM sys.index_columns x WHERE x.object_id = ic.object_id AND x.index_id = ic.index_id) = 1);
IF @OldBillOrderUnique IS NOT NULL
BEGIN
    DECLARE @DropUniqueSql NVARCHAR(400) = N'ALTER TABLE dbo.bills DROP CONSTRAINT ' + QUOTENAME(@OldBillOrderUnique);
    EXEC sys.sp_executesql @DropUniqueSql;
END;
GO

-- ===== INDEXES (created only when missing) =====
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_users_email' AND object_id = OBJECT_ID(N'dbo.app_users'))
    CREATE INDEX idx_users_email ON app_users(email);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_products_sku' AND object_id = OBJECT_ID(N'dbo.products'))
    CREATE INDEX idx_products_sku ON products(sku);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_products_category' AND object_id = OBJECT_ID(N'dbo.products'))
    CREATE INDEX idx_products_category ON products(category_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_products_active' AND object_id = OBJECT_ID(N'dbo.products'))
    CREATE INDEX idx_products_active ON products(active);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_orders_user' AND object_id = OBJECT_ID(N'dbo.orders'))
    CREATE INDEX idx_orders_user ON orders(user_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_orders_number' AND object_id = OBJECT_ID(N'dbo.orders'))
    CREATE INDEX idx_orders_number ON orders(order_number);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_orders_status' AND object_id = OBJECT_ID(N'dbo.orders'))
    CREATE INDEX idx_orders_status ON orders(status);
-- SQL Server treats NULLs as equal in UNIQUE constraints, so use a filtered index.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_orders_idempotency_key' AND object_id = OBJECT_ID(N'dbo.orders'))
    CREATE UNIQUE INDEX uk_orders_idempotency_key ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_payments_status' AND object_id = OBJECT_ID(N'dbo.payments'))
    CREATE INDEX idx_payments_status ON payments(payment_status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_bills_number' AND object_id = OBJECT_ID(N'dbo.bills'))
    CREATE INDEX idx_bills_number ON bills(bill_number);
-- Duplicate-bill prevention: at most one ACTIVE bill per order.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_bills_order_active' AND object_id = OBJECT_ID(N'dbo.bills'))
    CREATE UNIQUE INDEX uk_bills_order_active ON bills(order_id) WHERE status = 'ACTIVE';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_refund_history_request' AND object_id = OBJECT_ID(N'dbo.refund_status_history'))
    CREATE INDEX idx_refund_history_request ON refund_status_history(refund_request_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_deliveries_staff' AND object_id = OBJECT_ID(N'dbo.deliveries'))
    CREATE INDEX idx_deliveries_staff ON deliveries(assigned_staff_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_deliveries_status' AND object_id = OBJECT_ID(N'dbo.deliveries'))
    CREATE INDEX idx_deliveries_status ON deliveries(delivery_status);
-- A driver may have multiple deliveries in a slot, up to driver_slot_limits.max_orders.
-- Remove the obsolete unique index when upgrading an existing database, then retain a
-- non-unique composite index for slot-capacity lookups.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_staff_slot' AND object_id = OBJECT_ID(N'dbo.deliveries'))
    DROP INDEX uk_staff_slot ON dbo.deliveries;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_deliveries_staff_slot' AND object_id = OBJECT_ID(N'dbo.deliveries'))
    CREATE INDEX idx_deliveries_staff_slot ON dbo.deliveries(assigned_staff_id, scheduled_date, scheduled_slot) WHERE assigned_staff_id IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_offers_code' AND object_id = OBJECT_ID(N'dbo.offers'))
    CREATE INDEX idx_offers_code ON offers(code);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_feedbacks_user' AND object_id = OBJECT_ID(N'dbo.feedbacks'))
    CREATE INDEX idx_feedbacks_user ON feedbacks(user_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_audit_action' AND object_id = OBJECT_ID(N'dbo.audit_logs'))
    CREATE INDEX idx_audit_action ON audit_logs(action);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_audit_timestamp' AND object_id = OBJECT_ID(N'dbo.audit_logs'))
    CREATE INDEX idx_audit_timestamp ON audit_logs([timestamp]);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_email_change_user_status' AND object_id = OBJECT_ID(N'dbo.email_change_requests'))
    CREATE INDEX idx_email_change_user_status ON email_change_requests(user_id, status);

GO

-- ===== V8__seed_product_catalogue.sql =====
-- Keep the customer-facing catalogue in the database so every environment
-- serves the same items through /api/v1/products/public.
-- The NOT EXISTS guards preserve records that were already created through
-- the inventory portal or by an earlier development database initializer.

INSERT INTO categories (name, description, active)
SELECT 'Fruits & Vegetables', 'Fresh local produce delivered daily', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Fruits & Vegetables');

INSERT INTO categories (name, description, active)
SELECT 'Dairy & Eggs', 'Milk, cheese, yoghurt and farm eggs', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Dairy & Eggs');

INSERT INTO categories (name, description, active)
SELECT 'Bakery', 'Bread and pastries baked every morning', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Bakery');

INSERT INTO categories (name, description, active)
SELECT 'Rice & Grains', 'Rice, lentils and pantry staples', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Rice & Grains');

INSERT INTO categories (name, description, active)
SELECT 'Beverages', 'Tea, coffee, juices and water', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Beverages');

INSERT INTO categories (name, description, active)
SELECT 'Household Essentials', 'Cleaning and everyday home care', 1
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Household Essentials');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'FV-BAN-001', 'Ambul Bananas', 'Sweet local ambul bananas', 380.00, '1 kg', c.id, 1
FROM categories c
WHERE c.name = 'Fruits & Vegetables'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'FV-BAN-001');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'FV-CAR-002', 'Carrots', 'Fresh upcountry carrots', 290.00, '500 g', c.id, 1
FROM categories c
WHERE c.name = 'Fruits & Vegetables'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'FV-CAR-002');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'FV-ONI-003', 'Big Onions', 'Dry big onions', 420.00, '1 kg', c.id, 1
FROM categories c
WHERE c.name = 'Fruits & Vegetables'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'FV-ONI-003');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'FV-TOM-004', 'Tomatoes', 'Ripe red tomatoes', 350.00, '500 g', c.id, 1
FROM categories c
WHERE c.name = 'Fruits & Vegetables'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'FV-TOM-004');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'FV-APP-005', 'Red Apples', 'Imported crisp red apples', 1450.00, '1 kg', c.id, 1
FROM categories c
WHERE c.name = 'Fruits & Vegetables'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'FV-APP-005');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'DE-MLK-001', 'Fresh Milk', 'Pasteurised full cream milk', 520.00, '1 L', c.id, 1
FROM categories c
WHERE c.name = 'Dairy & Eggs'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'DE-MLK-001');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'DE-YOG-002', 'Set Yoghurt', 'Creamy set yoghurt cup', 95.00, '80 g', c.id, 1
FROM categories c
WHERE c.name = 'Dairy & Eggs'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'DE-YOG-002');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'DE-CHD-003', 'Cheddar Cheese', 'Mature cheddar block', 1350.00, '200 g', c.id, 1
FROM categories c
WHERE c.name = 'Dairy & Eggs'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'DE-CHD-003');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'DE-EGG-004', 'Farm Eggs', 'Brown farm eggs', 590.00, '10 pack', c.id, 1
FROM categories c
WHERE c.name = 'Dairy & Eggs'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'DE-EGG-004');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'DE-BUT-005', 'Salted Butter', 'Creamery salted butter', 1150.00, '200 g', c.id, 1
FROM categories c
WHERE c.name = 'Dairy & Eggs'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'DE-BUT-005');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'BK-BRD-001', 'Sandwich Bread', 'Soft white sandwich loaf', 220.00, '450 g', c.id, 1
FROM categories c
WHERE c.name = 'Bakery'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'BK-BRD-001');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'BK-CRO-002', 'Butter Croissant', 'Flaky all-butter croissant', 260.00, '1 pc', c.id, 1
FROM categories c
WHERE c.name = 'Bakery'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'BK-CRO-002');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'BK-MUF-003', 'Chocolate Muffin', 'Double chocolate muffin', 240.00, '1 pc', c.id, 1
FROM categories c
WHERE c.name = 'Bakery'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'BK-MUF-003');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'RG-RRC-001', 'Red Raw Rice', 'Local red raw rice', 1250.00, '5 kg', c.id, 1
FROM categories c
WHERE c.name = 'Rice & Grains'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'RG-RRC-001');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'RG-BAS-002', 'Basmati Rice', 'Long grain basmati', 980.00, '1 kg', c.id, 1
FROM categories c
WHERE c.name = 'Rice & Grains'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'RG-BAS-002');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'RG-DHL-003', 'Red Lentils (Dhal)', 'Split red lentils', 460.00, '1 kg', c.id, 1
FROM categories c
WHERE c.name = 'Rice & Grains'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'RG-DHL-003');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'BV-TEA-001', 'Ceylon Black Tea', 'Premium BOP Ceylon tea', 1100.00, '400 g', c.id, 1
FROM categories c
WHERE c.name = 'Beverages'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'BV-TEA-001');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'BV-OJ-002', 'Orange Juice', '100% orange juice, no added sugar', 780.00, '1 L', c.id, 1
FROM categories c
WHERE c.name = 'Beverages'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'BV-OJ-002');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'BV-WAT-003', 'Mineral Water', 'Natural mineral water', 150.00, '1.5 L', c.id, 1
FROM categories c
WHERE c.name = 'Beverages'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'BV-WAT-003');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'BV-COF-004', 'Instant Coffee', 'Rich instant coffee jar', 1650.00, '100 g', c.id, 1
FROM categories c
WHERE c.name = 'Beverages'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'BV-COF-004');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'HE-DSH-001', 'Dishwash Liquid', 'Lime dishwash liquid', 390.00, '500 ml', c.id, 1
FROM categories c
WHERE c.name = 'Household Essentials'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'HE-DSH-001');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'HE-LDY-002', 'Laundry Powder', 'Concentrated laundry powder', 690.00, '1 kg', c.id, 1
FROM categories c
WHERE c.name = 'Household Essentials'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'HE-LDY-002');

INSERT INTO products (sku, name, description, price, unit, category_id, active)
SELECT 'HE-TPR-003', 'Toilet Paper', 'Soft 2-ply rolls', 520.00, '4 rolls', c.id, 1
FROM categories c
WHERE c.name = 'Household Essentials'
  AND NOT EXISTS (SELECT 1 FROM products WHERE sku = 'HE-TPR-003');

-- Product photos ship with the app under /images/products. Only fills a missing image_url, so images
-- chosen by staff in the Inventory portal are never replaced.
UPDATE p SET p.image_url = img.image_url
FROM products p
JOIN (
    VALUES
        ('FV-BAN-001', '/images/products/ambul-bananas.webp'),
        ('FV-CAR-002', '/images/products/carrots.webp'),
        ('FV-ONI-003', '/images/products/big-onions.webp'),
        ('FV-TOM-004', '/images/products/tomatoes.webp'),
        ('FV-APP-005', '/images/products/red-apples.webp'),
        ('DE-YOG-002', '/images/products/kotmale-set-yoghurt.webp'),
        ('DE-CHD-003', '/images/products/richlife-cheddar.webp'),
        ('DE-EGG-004', '/images/products/farm-eggs.webp'),
        ('DE-BUT-005', '/images/products/ambewela-butter.webp'),
        ('BK-BRD-001', '/images/products/sandwich-bread.webp'),
        ('BK-MUF-003', '/images/products/chocolate-muffin.webp'),
        ('RG-RRC-001', '/images/products/maharaja-red-raw-rice.webp'),
        ('RG-BAS-002', '/images/products/daawat-basmati-rice.webp'),
        ('RG-DHL-003', '/images/products/trs-red-lentils.webp'),
        ('BV-TEA-001', '/images/products/dilmah-ceylon-tea.webp'),
        ('BV-OJ-002', '/images/products/rita-orange-juice.webp'),
        ('BV-WAT-003', '/images/products/refresh-mineral-water.webp'),
        ('BV-COF-004', '/images/products/nescafe-classic.webp'),
        ('HE-DSH-001', '/images/products/dishwash-liquid.webp'),
        ('HE-LDY-002', '/images/products/sunlight-detergent.webp'),
        ('HE-TPR-003', '/images/products/scott-toilet-paper.webp')
) img(sku, image_url) ON img.sku = p.sku
WHERE p.image_url IS NULL OR p.image_url = '';

-- Stock belongs in inventory, not in the browser. Existing stock is never
-- overwritten, which keeps orders and staff adjustments authoritative.
INSERT INTO inventory (product_id, quantity, minimum_level, maximum_level)
SELECT p.id, seed.quantity, 10, 1000
FROM products p
JOIN (
    VALUES
        ('FV-BAN-001', 120),
        ('FV-CAR-002', 90),
        ('FV-ONI-003', 150),
        ('FV-TOM-004', 70),
        ('FV-APP-005', 40),
        ('DE-MLK-001', 80),
        ('DE-YOG-002', 200),
        ('DE-CHD-003', 35),
        ('DE-EGG-004', 60),
        ('DE-BUT-005', 30),
        ('BK-BRD-001', 50),
        ('BK-CRO-002', 40),
        ('BK-MUF-003', 25),
        ('RG-RRC-001', 60),
        ('RG-BAS-002', 45),
        ('RG-DHL-003', 100),
        ('BV-TEA-001', 55),
        ('BV-OJ-002', 40),
        ('BV-WAT-003', 240),
        ('BV-COF-004', 0),
        ('HE-DSH-001', 70),
        ('HE-LDY-002', 50),
        ('HE-TPR-003', 65)
) seed(sku, quantity) ON seed.sku = p.sku
WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.product_id = p.id);

GO

-- ===== V10__manager_profile.sql (data rule) =====
-- Managers are staff only: they do not shop, so drop the customer role from any manager account.
DELETE FROM user_roles
WHERE role = 'CUSTOMER'
  AND user_id IN (SELECT user_id FROM user_roles WHERE role = 'MANAGER');

GO

-- ===== V11__cashier_billing_workflow.sql =====
-- V11 is merged into the fresh CREATE TABLE definitions and the upgrade section above. It supplies
-- payment verification, order tax/delivery charges, bill lifecycle fields, multiple voided bills per
-- order, refund processing fields, and refund_status_history.

-- ===== SAMPLE-DATA CORRECTIONS (safe to re-run; only touch rows written by older demo seeds) =====
-- Earlier versions of the demo seed wrote values the application rejects or never reads.
-- Each statement matches those exact demo values, so real records are never touched.

-- 1. Customer logins must end in ".com" (LoginEmailRules); "@rapidmart.local" is the staff-only
--    domain, so the old demo customers failed every login with INVALID_EMAIL. Rename them in place
--    (same id, orders, cart, points) unless the new address is already taken.
UPDATE u SET u.email = v.new_email, u.updated_at = SYSDATETIMEOFFSET()
FROM app_users u
JOIN (VALUES ('customer1@rapidmart.local', 'customer1@example.com'),
             ('customer2@rapidmart.local', 'customer2@example.com')) v(old_email, new_email) ON u.email = v.old_email
WHERE NOT EXISTS (SELECT 1 FROM app_users x WHERE x.email = v.new_email)
  AND NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role <> 'CUSTOMER');

-- 2. Reward history must use the reasons LoyaltyService reads: EARNED_ORDER_COMPLETED with an
--    ORDER_<orderNumber> reference (refund reversal looks these up) and "MANUAL_ADJUSTMENT: ..."
--    (the loyalty officer's manual-adjustment list filters on that prefix).
UPDATE reward_transactions SET reason = 'EARNED_ORDER_COMPLETED', reference_id = 'ORDER_' + reference_id
WHERE reason = 'Order points' AND reference_id IN ('RM-HIST-0001', 'RM-HIST-0002');
UPDATE reward_transactions SET reason = 'MANUAL_ADJUSTMENT: Birthday gift'
WHERE reason = 'Manual bonus: birthday gift' AND reference_id = 'BONUS-0001';

-- 3. Checkout records the plain order number as the stock movement reference.
UPDATE stock_movement_history SET reference_id = SUBSTRING(reference_id, 7, 255)
WHERE movement_type = 'CHECKOUT_DEDUCTION' AND reference_id LIKE 'ORDER[_]RM-DEMO-%';

-- 4. Every order gets a delivery row at checkout. The demo EFT order was seeded without one, so once
--    its payment was approved the coordinator could never schedule it.
INSERT INTO deliveries (order_id, delivery_status, scheduled_date, scheduled_slot)
SELECT o.id, 'PENDING', o.delivery_date, o.delivery_slot
FROM orders o
WHERE o.order_number = 'RM-DEMO-0002' AND o.status <> 'CANCELLED'
  AND NOT EXISTS (SELECT 1 FROM deliveries d WHERE d.order_id = o.id);

-- 5. Email changes are a manager self-service feature; a request on a customer account can never
--    be processed, so the stale demo request is cancelled (kept for history, not deleted).
UPDATE e SET e.status = 'CANCELLED', e.updated_at = SYSDATETIMEOFFSET()
FROM email_change_requests e
JOIN app_users u ON u.id = e.user_id
WHERE e.new_email = 'sithara.f@example.com' AND e.status = 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = 'MANAGER');

-- 6. Demo bills carry the same charge snapshot the application stores when it issues a bill.
UPDATE b SET b.subtotal = o.subtotal, b.offer_discount = o.offer_discount, b.point_discount = o.point_discount,
             b.tax_percentage = o.tax_percentage, b.tax_amount = o.tax_amount, b.delivery_charge = o.delivery_charge
FROM bills b JOIN orders o ON o.id = b.order_id
WHERE b.bill_number LIKE 'BILL-DEMO-%' AND b.subtotal IS NULL;

GO

-- ===== OPTIONAL DEMO SEED DATA =====
-- Controlled by the @SeedDemoData setting at the top of this file (default 0 = skipped).
-- Passwords: manager 'DemoManager21!', staff 'DemoStaff21!', customers 'DemoCustomer21!'.
-- Staff sign in on the staff portal (@rapidmart.local); customers on the customer portal (.com).
-- Public demo passwords: never load this section into production. Existing accounts keep their
-- current passwords; only missing rows are inserted. Runs in one transaction (all or nothing).
DECLARE @SeedDemoData BIT = CAST(SESSION_CONTEXT(N'RapidMart.SeedDemoData') AS BIT);

IF @SeedDemoData = 1
BEGIN
BEGIN TRANSACTION;

-- Loyalty tiers (rewards.html, loyalty/tiers.html)
INSERT INTO loyalty_tiers (tier_name, minimum_points, earning_rate_multiplier, benefits_description)
SELECT v.n, v.p, v.m, v.b FROM (VALUES
    ('BRONZE',       0, 1.00, 'Entry tier: earn 1x points on every order'),
    ('SILVER',     500, 1.25, 'Earn 1.25x points and access to Silver offers'),
    ('GOLD',      2000, 1.50, 'Earn 1.5x points, Gold offers and priority delivery slots'),
    ('PLATINUM',  5000, 2.00, 'Earn 2x points, exclusive offers and free priority support')
) v(n, p, m, b)
WHERE NOT EXISTS (SELECT 1 FROM loyalty_tiers t WHERE t.tier_name = v.n);

-- Offers (loyalty/offers.html, checkout.html)
INSERT INTO offers (title, code, discount_percentage, min_order_amount, start_date, end_date, eligible_tier_id, active)
SELECT v.t, v.c, v.d, v.m, CAST(SYSDATETIMEOFFSET() AS DATE), DATEADD(YEAR, 1, CAST(SYSDATETIMEOFFSET() AS DATE)),
       (SELECT id FROM loyalty_tiers WHERE tier_name = v.tier), 1
FROM (VALUES
    ('Welcome 5% Off',        'WELCOME5', 5.00,  1000.00, NULL),
    ('Weekend Fresh 10% Off', 'FRESH10', 10.00,  3000.00, 'SILVER'),
    ('Gold Member 15% Off',   'GOLD15',  15.00,  5000.00, 'GOLD')
) v(t, c, d, m, tier)
WHERE NOT EXISTS (SELECT 1 FROM offers o WHERE o.code = v.c);

-- Users (login.html / staff-login.html; one account per portal role)
INSERT INTO app_users (email, password_hash, full_name, phone, active)
SELECT v.e, v.h, v.n, v.p, 1 FROM (VALUES
    ('manager@rapidmart.local',     '$2a$10$hwLZxk3WaZG.Z1sfaPNHjOB/PYGb9ewg4KOxPNS99tU.RdAiawfGO', 'System Manager',     '+94770000000'),
    ('cashier@rapidmart.local',     '$2a$10$.1fkX0pfSDayT74VaGNhZegXhSDGgEbcypkSbz78w66PPkqlEEHBi', 'Nimali Cashier',     '+94770000001'),
    ('inventory@rapidmart.local',   '$2a$10$.1fkX0pfSDayT74VaGNhZegXhSDGgEbcypkSbz78w66PPkqlEEHBi', 'Kasun Inventory',    '+94770000002'),
    ('coordinator@rapidmart.local', '$2a$10$.1fkX0pfSDayT74VaGNhZegXhSDGgEbcypkSbz78w66PPkqlEEHBi', 'Dilani Coordinator', '+94770000003'),
    ('driver@rapidmart.local',      '$2a$10$.1fkX0pfSDayT74VaGNhZegXhSDGgEbcypkSbz78w66PPkqlEEHBi', 'Ruwan Driver',       '+94770000004'),
    ('loyalty@rapidmart.local',     '$2a$10$.1fkX0pfSDayT74VaGNhZegXhSDGgEbcypkSbz78w66PPkqlEEHBi', 'Sanduni Loyalty',    '+94770000005'),
    ('customer1@example.com',       '$2a$10$CjBzY7Q0DSjj.4/gwY3aVOagL0WABIM.QaTc2QLRet.sRXtu4zaTq', 'Amal Perera',        '+94771111111'),
    ('customer2@example.com',       '$2a$10$CjBzY7Q0DSjj.4/gwY3aVOagL0WABIM.QaTc2QLRet.sRXtu4zaTq', 'Sithara Fernando',   '+94772222222')
) v(e, h, n, p)
WHERE NOT EXISTS (SELECT 1 FROM app_users u WHERE u.email = v.e);

INSERT INTO user_roles (user_id, role)
SELECT u.id, v.r FROM (VALUES
    ('manager@rapidmart.local',     'MANAGER'),
    ('cashier@rapidmart.local',     'CASHIER'),
    ('inventory@rapidmart.local',   'INVENTORY_STAFF'),
    ('coordinator@rapidmart.local', 'DELIVERY_COORDINATOR'),
    ('driver@rapidmart.local',      'DELIVERY_STAFF'),
    ('loyalty@rapidmart.local',     'LOYALTY_OFFICER'),
    ('customer1@example.com',       'CUSTOMER'),
    ('customer2@example.com',       'CUSTOMER')
) v(e, r)
JOIN app_users u ON u.email = v.e
WHERE NOT EXISTS (SELECT 1 FROM user_roles x WHERE x.user_id = u.id AND x.role = v.r);

-- Customer addresses, carts and loyalty accounts (checkout.html, cart.html, rewards.html)
INSERT INTO customer_addresses (user_id, street_address, city, postal_code, phone_contact, is_default, active)
SELECT u.id, v.s, v.c, v.p, u.phone, 1, 1 FROM (VALUES
    ('customer1@example.com', '12 Galle Road',  'Colombo', '00300'),
    ('customer2@example.com', '45 Temple Road', 'Kandy',   '20000')
) v(e, s, c, p)
JOIN app_users u ON u.email = v.e
WHERE NOT EXISTS (SELECT 1 FROM customer_addresses a WHERE a.user_id = u.id);

INSERT INTO carts (user_id)
SELECT u.id FROM app_users u JOIN user_roles r ON r.user_id = u.id AND r.role = 'CUSTOMER'
WHERE NOT EXISTS (SELECT 1 FROM carts c WHERE c.user_id = u.id);

INSERT INTO loyalty_accounts (user_id, current_points, lifetime_points, current_tier_id)
SELECT u.id, 0, 0, (SELECT id FROM loyalty_tiers WHERE tier_name = 'BRONZE')
FROM app_users u JOIN user_roles r ON r.user_id = u.id AND r.role = 'CUSTOMER'
WHERE NOT EXISTS (SELECT 1 FROM loyalty_accounts l WHERE l.user_id = u.id);

-- Opening stock history for the inventory portal (inventory/stock-operations.html)
INSERT INTO stock_movement_history (product_id, quantity_delta, movement_type, reference_id, actor_email)
SELECT i.product_id, i.quantity, 'INITIAL_STOCK', 'SEED', 'inventory@rapidmart.local'
FROM inventory i
WHERE i.quantity > 0
  AND NOT EXISTS (SELECT 1 FROM stock_movement_history h WHERE h.product_id = i.product_id);

-- Restock request for the out-of-stock coffee (inventory/dashboard.html)
INSERT INTO restock_requests (product_id, requested_quantity, status, requested_by_id)
SELECT p.id, 50, 'PENDING', (SELECT id FROM app_users WHERE email = 'inventory@rapidmart.local')
FROM products p
WHERE p.sku = 'BV-COF-004'
  AND NOT EXISTS (SELECT 1 FROM restock_requests r WHERE r.product_id = p.id);

-- One delivered sample order with payment, bill and delivery (orders.html, cashier and delivery dashboards)
DECLARE @cust BIGINT = (SELECT id FROM app_users WHERE email = 'customer1@example.com');
DECLARE @driver BIGINT = (SELECT id FROM app_users WHERE email = 'driver@rapidmart.local');
DECLARE @cashier BIGINT = (SELECT id FROM app_users WHERE email = 'cashier@rapidmart.local');
DECLARE @today DATE = CAST(SYSDATETIMEOFFSET() AS DATE);
IF @cust IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'RM-DEMO-0001')
BEGIN
    INSERT INTO orders (order_number, user_id, status, delivery_date, delivery_slot, delivery_street, delivery_city,
                        delivery_postal_code, delivery_phone, subtotal, total_amount)
    VALUES ('RM-DEMO-0001', @cust, 'DELIVERED', @today, 'MORNING_09_12', '12 Galle Road', 'Colombo', '00300',
            '+94771111111', 1260.00, 1260.00);
    DECLARE @oid BIGINT = SCOPE_IDENTITY();

    INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity, line_total)
    SELECT @oid, p.id, p.name, p.price, v.q, p.price * v.q
    FROM (VALUES ('DE-MLK-001', 2), ('BK-BRD-001', 1)) v(sku, q)
    JOIN products p ON p.sku = v.sku;

    UPDATE i
    SET i.quantity = i.quantity - v.q, i.updated_at = SYSDATETIMEOFFSET(), i.version = i.version + 1
    FROM inventory i
    JOIN products p ON p.id = i.product_id
    JOIN (VALUES ('DE-MLK-001', 2), ('BK-BRD-001', 1)) v(sku, q) ON v.sku = p.sku;

    INSERT INTO stock_movement_history
        (product_id, quantity_delta, movement_type, reference_id, actor_id, actor_email)
    SELECT p.id, -v.q, 'CHECKOUT_DEDUCTION', 'RM-DEMO-0001', @cust, 'customer1@example.com'
    FROM (VALUES ('DE-MLK-001', 2), ('BK-BRD-001', 1)) v(sku, q)
    JOIN products p ON p.sku = v.sku;

    INSERT INTO payments
        (order_id, payment_method, payment_status, amount, transaction_reference, verified_by_id, verified_at)
    VALUES (@oid, 'COD', 'PAID', 1260.00, 'DEMO-COD-0001', @cashier, SYSDATETIMEOFFSET());
    INSERT INTO bills (bill_number, order_id, amount, subtotal, offer_discount, point_discount,
                       tax_percentage, tax_amount, delivery_charge, issued_by_id)
    VALUES ('BILL-DEMO-0001', @oid, 1260.00, 1260.00, 0.00, 0.00, 0.00, 0.00, 0.00, @cashier);

    INSERT INTO deliveries (order_id, assigned_staff_id, delivery_status, scheduled_date, scheduled_slot, completed_at)
    VALUES (@oid, @driver, 'DELIVERED', @today, 'MORNING_09_12', SYSDATETIMEOFFSET());
    DECLARE @did BIGINT = SCOPE_IDENTITY();
    INSERT INTO delivery_attempts (delivery_id, status, notes) VALUES (@did, 'DELIVERED', 'Delivered to customer');
    -- Cash already handed over and confirmed (the payment above is PAID/verified), so the settlement is confirmed too.
    INSERT INTO cod_settlements
        (delivery_id, delivery_staff_id, collected_amount, confirmed_by_cashier, cashier_id, settled_at)
    VALUES (@did, @driver, 1260.00, 1, @cashier, SYSDATETIMEOFFSET());

    UPDATE loyalty_accounts SET current_points = 12, lifetime_points = 12 WHERE user_id = @cust;
    INSERT INTO reward_transactions (loyalty_account_id, points_delta, balance_after, reason, reference_id)
    SELECT id, 12, 12, 'EARNED_ORDER_COMPLETED', 'ORDER_RM-DEMO-0001'
    FROM loyalty_accounts WHERE user_id = @cust;
END

-- Sample feedback (feedback.html, manager dashboard)
INSERT INTO feedbacks (user_id, category, subject, message, status)
SELECT u.id, 'DELIVERY', 'Great delivery service', 'The driver arrived on time and everything was fresh.', 'PENDING'
FROM app_users u
WHERE u.email = 'customer1@example.com'
  AND NOT EXISTS (SELECT 1 FROM feedbacks f WHERE f.user_id = u.id);

-- ===== ROLE-ALIGNED WORKFLOW DATA (one open work item per staff portal; safe to re-run) =====
-- CASHIER              : EFT payment awaiting verification (RM-DEMO-0002), orders awaiting bill generation
--                        (RM-DEMO-0004, RM-DEMO-0005), pending refund (RM-DEMO-0001)
-- DELIVERY_COORDINATOR : confirmed order with unassigned delivery (RM-DEMO-0003)
-- DELIVERY_STAFF       : one ASSIGNED (RM-DEMO-0004) and one OUT_FOR_DELIVERY (RM-DEMO-0005) delivery for driver@
-- INVENTORY_STAFF      : pending restock request (above) and low-stock items
-- LOYALTY_OFFICER      : Silver-tier customer (customer2) with a points history and a manual adjustment,
--                        Silver-only offer FRESH10 applicable
-- MANAGER              : audit logs, notification logs, feedback reply, pending email-change request (own profile)
-- CUSTOMER             : orders in every state, cart contents, feedback with reply

-- Second driver so the coordinator can compare and reassign
INSERT INTO app_users (email, password_hash, full_name, phone, active)
SELECT 'driver2@rapidmart.local', '$2a$10$.1fkX0pfSDayT74VaGNhZegXhSDGgEbcypkSbz78w66PPkqlEEHBi', 'Chamara Driver', '+94770000006', 1
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'driver2@rapidmart.local');

INSERT INTO user_roles (user_id, role)
SELECT u.id, 'DELIVERY_STAFF' FROM app_users u
WHERE u.email = 'driver2@rapidmart.local'
  AND NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = 'DELIVERY_STAFF');

-- Customer2 is a Silver member with a points history (LOYALTY_OFFICER portal, rewards.html, FRESH10 offer)
UPDATE loyalty_accounts
SET current_points = 640, lifetime_points = 640,
    current_tier_id = (SELECT id FROM loyalty_tiers WHERE tier_name = 'SILVER')
WHERE user_id = (SELECT id FROM app_users WHERE email = 'customer2@example.com')
  AND lifetime_points = 0;

-- Reasons match LoyaltyService: EARNED_ORDER_COMPLETED + ORDER_<orderNumber>, and "MANUAL_ADJUSTMENT: <reason>".
INSERT INTO reward_transactions (loyalty_account_id, points_delta, balance_after, reason, reference_id, actor_id)
SELECT la.id, v.d, v.b, v.r, v.ref,
       CASE WHEN v.adj = 1 THEN (SELECT id FROM app_users WHERE email = 'loyalty@rapidmart.local') END
FROM (VALUES
    (500, 500, 'EARNED_ORDER_COMPLETED',           'ORDER_RM-HIST-0001', 0),
    (100, 600, 'EARNED_ORDER_COMPLETED',           'ORDER_RM-HIST-0002', 0),
    ( 40, 640, 'MANUAL_ADJUSTMENT: Birthday gift', 'BONUS-0001',         1)
) v(d, b, r, ref, adj)
JOIN loyalty_accounts la ON la.user_id = (SELECT id FROM app_users WHERE email = 'customer2@example.com')
WHERE NOT EXISTS (SELECT 1 FROM reward_transactions t WHERE t.loyalty_account_id = la.id);

-- Cart contents for customer2 (cart.html)
INSERT INTO cart_items (cart_id, product_id, quantity)
SELECT c.id, p.id, v.q
FROM (VALUES ('FV-BAN-001', 2), ('DE-EGG-004', 1), ('BV-WAT-003', 6)) v(sku, q)
JOIN products p ON p.sku = v.sku
JOIN carts c ON c.user_id = (SELECT id FROM app_users WHERE email = 'customer2@example.com')
WHERE NOT EXISTS (SELECT 1 FROM cart_items ci WHERE ci.cart_id = c.id AND ci.product_id = p.id);

DECLARE @c1 BIGINT = (SELECT id FROM app_users WHERE email = 'customer1@example.com');
DECLARE @c2 BIGINT = (SELECT id FROM app_users WHERE email = 'customer2@example.com');
DECLARE @drv BIGINT = (SELECT id FROM app_users WHERE email = 'driver@rapidmart.local');
DECLARE @d0 DATE = CAST(SYSDATETIMEOFFSET() AS DATE);
DECLARE @d1 DATE = DATEADD(DAY, 1, @d0);
DECLARE @o BIGINT, @dl BIGINT;

-- RM-DEMO-0002: EFT order awaiting cashier verification (CASHIER payments queue)
-- items: RG-RRC-001 x2 @ 1250.00 = 2500.00
IF @c2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'RM-DEMO-0002')
BEGIN
    INSERT INTO orders (order_number, user_id, status, delivery_date, delivery_slot, delivery_street, delivery_city,
                        delivery_postal_code, delivery_phone, subtotal, total_amount)
    VALUES ('RM-DEMO-0002', @c2, 'AWAITING_PAYMENT', @d1, 'MORNING_09_12', '45 Temple Road', 'Kandy', '20000',
            '+94772222222', 2500.00, 2500.00);
    SET @o = SCOPE_IDENTITY();
    INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity, line_total)
    SELECT @o, p.id, p.name, p.price, v.q, p.price * v.q
    FROM (VALUES ('RG-RRC-001', 2)) v(sku, q) JOIN products p ON p.sku = v.sku;
    UPDATE i SET i.quantity = i.quantity - 2, i.updated_at = SYSDATETIMEOFFSET(), i.version = i.version + 1
    FROM inventory i JOIN products p ON p.id = i.product_id WHERE p.sku = 'RG-RRC-001';
    INSERT INTO stock_movement_history (product_id, quantity_delta, movement_type, reference_id, actor_id, actor_email)
    SELECT p.id, -2, 'CHECKOUT_DEDUCTION', 'RM-DEMO-0002', @c2, 'customer2@example.com'
    FROM products p WHERE p.sku = 'RG-RRC-001';
    INSERT INTO payments (order_id, payment_method, payment_status, amount, transaction_reference, notes)
    VALUES (@o, 'EFT', 'PENDING', 2500.00, 'EFT-DEMO-0002', 'Bank transfer slip uploaded, awaiting verification');
    -- Checkout always creates the (unassigned) delivery alongside the payment.
    INSERT INTO deliveries (order_id, delivery_status, scheduled_date, scheduled_slot)
    VALUES (@o, 'PENDING', @d1, 'MORNING_09_12');
END

-- RM-DEMO-0003: paid card order, delivery unassigned (COORDINATOR assignment queue)
-- items: DE-BUT-005 x1 @ 1150.00 + DE-CHD-003 x1 @ 1350.00 + BK-CRO-002 x1 @ 260.00 = 2760.00
IF @c1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'RM-DEMO-0003')
BEGIN
    INSERT INTO orders (order_number, user_id, status, delivery_date, delivery_slot, delivery_street, delivery_city,
                        delivery_postal_code, delivery_phone, subtotal, total_amount)
    VALUES ('RM-DEMO-0003', @c1, 'CONFIRMED', @d1, 'EVENING_15_18', '12 Galle Road', 'Colombo', '00300',
            '+94771111111', 2760.00, 2760.00);
    SET @o = SCOPE_IDENTITY();
    INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity, line_total)
    SELECT @o, p.id, p.name, p.price, v.q, p.price * v.q
    FROM (VALUES ('DE-BUT-005', 1), ('DE-CHD-003', 1), ('BK-CRO-002', 1)) v(sku, q) JOIN products p ON p.sku = v.sku;
    UPDATE i SET i.quantity = i.quantity - v.q, i.updated_at = SYSDATETIMEOFFSET(), i.version = i.version + 1
    FROM inventory i JOIN products p ON p.id = i.product_id
    JOIN (VALUES ('DE-BUT-005', 1), ('DE-CHD-003', 1), ('BK-CRO-002', 1)) v(sku, q) ON v.sku = p.sku;
    INSERT INTO stock_movement_history (product_id, quantity_delta, movement_type, reference_id, actor_id, actor_email)
    SELECT p.id, -v.q, 'CHECKOUT_DEDUCTION', 'RM-DEMO-0003', @c1, 'customer1@example.com'
    FROM (VALUES ('DE-BUT-005', 1), ('DE-CHD-003', 1), ('BK-CRO-002', 1)) v(sku, q)
    JOIN products p ON p.sku = v.sku;
    INSERT INTO payments (order_id, payment_method, payment_status, amount, transaction_reference)
    VALUES (@o, 'CARD_SIMULATION', 'PAID', 2760.00, 'CARD-DEMO-0003');
    INSERT INTO bills (bill_number, order_id, amount, subtotal, offer_discount, point_discount,
                       tax_percentage, tax_amount, delivery_charge, issued_by_id)
    VALUES ('BILL-DEMO-0003', @o, 2760.00, 2760.00, 0.00, 0.00, 0.00, 0.00, 0.00, @cashier);
    INSERT INTO deliveries (order_id, delivery_status, scheduled_date, scheduled_slot)
    VALUES (@o, 'PENDING', @d1, 'EVENING_15_18');
END

-- RM-DEMO-0004: COD order assigned to driver, not yet dispatched (DELIVERY_STAFF "assigned" list)
-- items: BV-OJ-002 x1 @ 780.00 + BV-TEA-001 x1 @ 1100.00 = 1880.00
IF @c2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'RM-DEMO-0004')
BEGIN
    INSERT INTO orders (order_number, user_id, status, delivery_date, delivery_slot, delivery_street, delivery_city,
                        delivery_postal_code, delivery_phone, subtotal, total_amount)
    VALUES ('RM-DEMO-0004', @c2, 'CONFIRMED', @d1, 'AFTERNOON_12_15', '45 Temple Road', 'Kandy', '20000',
            '+94772222222', 1880.00, 1880.00);
    SET @o = SCOPE_IDENTITY();
    INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity, line_total)
    SELECT @o, p.id, p.name, p.price, v.q, p.price * v.q
    FROM (VALUES ('BV-OJ-002', 1), ('BV-TEA-001', 1)) v(sku, q) JOIN products p ON p.sku = v.sku;
    UPDATE i SET i.quantity = i.quantity - v.q, i.updated_at = SYSDATETIMEOFFSET(), i.version = i.version + 1
    FROM inventory i JOIN products p ON p.id = i.product_id
    JOIN (VALUES ('BV-OJ-002', 1), ('BV-TEA-001', 1)) v(sku, q) ON v.sku = p.sku;
    INSERT INTO stock_movement_history (product_id, quantity_delta, movement_type, reference_id, actor_id, actor_email)
    SELECT p.id, -v.q, 'CHECKOUT_DEDUCTION', 'RM-DEMO-0004', @c2, 'customer2@example.com'
    FROM (VALUES ('BV-OJ-002', 1), ('BV-TEA-001', 1)) v(sku, q) JOIN products p ON p.sku = v.sku;
    INSERT INTO payments (order_id, payment_method, payment_status, amount)
    VALUES (@o, 'COD', 'PENDING', 1880.00);
    INSERT INTO deliveries (order_id, assigned_staff_id, delivery_status, scheduled_date, scheduled_slot)
    VALUES (@o, @drv, 'ASSIGNED', @d1, 'AFTERNOON_12_15');
    SET @dl = SCOPE_IDENTITY();
    INSERT INTO delivery_attempts (delivery_id, status, notes) VALUES (@dl, 'ASSIGNED', 'Assigned to Ruwan Driver');
END

-- RM-DEMO-0005: COD order out for delivery today (DELIVERY_STAFF active run)
-- items: HE-LDY-002 x1 @ 690.00 + HE-DSH-001 x1 @ 390.00 = 1080.00
IF @c1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'RM-DEMO-0005')
BEGIN
    INSERT INTO orders (order_number, user_id, status, delivery_date, delivery_slot, delivery_street, delivery_city,
                        delivery_postal_code, delivery_phone, subtotal, total_amount)
    VALUES ('RM-DEMO-0005', @c1, 'OUT_FOR_DELIVERY', @d0, 'EVENING_15_18', '12 Galle Road', 'Colombo', '00300',
            '+94771111111', 1080.00, 1080.00);
    SET @o = SCOPE_IDENTITY();
    INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity, line_total)
    SELECT @o, p.id, p.name, p.price, v.q, p.price * v.q
    FROM (VALUES ('HE-LDY-002', 1), ('HE-DSH-001', 1)) v(sku, q) JOIN products p ON p.sku = v.sku;
    UPDATE i SET i.quantity = i.quantity - v.q, i.updated_at = SYSDATETIMEOFFSET(), i.version = i.version + 1
    FROM inventory i JOIN products p ON p.id = i.product_id
    JOIN (VALUES ('HE-LDY-002', 1), ('HE-DSH-001', 1)) v(sku, q) ON v.sku = p.sku;
    INSERT INTO stock_movement_history (product_id, quantity_delta, movement_type, reference_id, actor_id, actor_email)
    SELECT p.id, -v.q, 'CHECKOUT_DEDUCTION', 'RM-DEMO-0005', @c1, 'customer1@example.com'
    FROM (VALUES ('HE-LDY-002', 1), ('HE-DSH-001', 1)) v(sku, q) JOIN products p ON p.sku = v.sku;
    INSERT INTO payments (order_id, payment_method, payment_status, amount)
    VALUES (@o, 'COD', 'PENDING', 1080.00);
    INSERT INTO deliveries (order_id, assigned_staff_id, delivery_status, scheduled_date, scheduled_slot)
    VALUES (@o, @drv, 'OUT_FOR_DELIVERY', @d0, 'EVENING_15_18');
    SET @dl = SCOPE_IDENTITY();
    INSERT INTO delivery_attempts (delivery_id, status, notes) VALUES (@dl, 'ASSIGNED', 'Assigned to Ruwan Driver');
    INSERT INTO delivery_attempts (delivery_id, status, notes) VALUES (@dl, 'OUT_FOR_DELIVERY', 'Left the store');
END

-- Pending refund on the delivered order (CASHIER refunds queue, customer orders.html)
INSERT INTO refund_requests (order_id, user_id, refund_status, refund_amount, reason)
SELECT o.id, o.user_id, 'PENDING', 220.00, 'Sandwich bread arrived squashed and was not edible.'
FROM orders o
WHERE o.order_number = 'RM-DEMO-0001'
  AND NOT EXISTS (SELECT 1 FROM refund_requests r WHERE r.order_id = o.id);

-- Answered feedback thread (MANAGER feedback inbox, customer feedback.html)
INSERT INTO feedbacks (user_id, category, subject, message, status)
SELECT u.id, 'PRODUCT_QUALITY', 'Bananas were overripe', 'The bananas in my last order were too soft.', 'RESPONDED'
FROM app_users u
WHERE u.email = 'customer2@example.com'
  AND NOT EXISTS (SELECT 1 FROM feedbacks f WHERE f.user_id = u.id);

INSERT INTO feedback_replies (feedback_id, replied_by_id, reply_message)
SELECT f.id, (SELECT id FROM app_users WHERE email = 'manager@rapidmart.local'),
       'Sorry about that. We have flagged the batch and added a credit to your account.'
FROM feedbacks f
WHERE f.subject = 'Bananas were overripe'
  AND NOT EXISTS (SELECT 1 FROM feedback_replies r WHERE r.feedback_id = f.id);

-- Pending email change on the manager's own profile (manager/dashboard.html profile page).
-- Email changes are manager self-service only (ManagerProfileService).
INSERT INTO email_change_requests (user_id, new_email, status)
SELECT u.id, 'store.manager@rapidmart.local', 'PENDING'
FROM app_users u
WHERE u.email = 'manager@rapidmart.local'
  AND NOT EXISTS (SELECT 1 FROM email_change_requests e WHERE e.user_id = u.id);

-- Audit trail (MANAGER audit log / reports). Action names match the literal
-- strings the app's AuditService callers actually log (see AuditService usages).
INSERT INTO audit_logs (action, actor_email, target_reference, details)
SELECT v.a, v.e, v.t, v.d FROM (VALUES
    ('EMPLOYEE_CREATED',           'manager@rapidmart.local', 'cashier@rapidmart.local',       'Created employee with role CASHIER'),
    ('EMPLOYEE_CREATED',           'manager@rapidmart.local', 'driver@rapidmart.local',        'Created employee with role DELIVERY_STAFF'),
    ('MANUAL_REWARD_ADJUSTMENT',   'loyalty@rapidmart.local', 'customer2@example.com',         'Adjusted points by 40 for reason: Birthday gift'),
    ('EMAIL_CHANGE_REQUESTED',     'manager@rapidmart.local', 'manager@rapidmart.local',       'Requested change to store.manager@rapidmart.local')
) v(a, e, t, d)
WHERE NOT EXISTS (SELECT 1 FROM audit_logs);

-- Notification log (MANAGER notifications view). Channel/subject pattern
-- matches NotificationService: EMAIL always carries a subject, SMS never does.
INSERT INTO notification_logs (recipient, channel, subject, message, status)
SELECT v.r, v.c, v.s, v.m, v.st FROM (VALUES
    ('customer1@example.com', 'EMAIL', 'Order delivered', 'Your order RM-DEMO-0001 has been delivered.',           'NOT_SENT'),
    ('customer2@example.com', 'EMAIL', 'Payment pending', 'We are verifying your bank transfer for RM-DEMO-0002.', 'NOT_SENT'),
    ('+94771111111',          'SMS',   NULL,              'Order RM-DEMO-0005 is on its way.',                     'NOT_SENT')
) v(r, c, s, m, st)
WHERE NOT EXISTS (SELECT 1 FROM notification_logs);

COMMIT TRANSACTION;
END;
GO

-- ===== V12__delivery_status_history.sql =====
-- Customer delivery tracking: one row per delivery status change (DeliveryStatusHistory entity).
IF OBJECT_ID(N'dbo.delivery_status_history', N'U') IS NULL
CREATE TABLE delivery_status_history (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    status_message VARCHAR(500),
    updated_by VARCHAR(255),
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT fk_dsh_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries(id),
    CONSTRAINT fk_dsh_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- Upgrade a table created by an earlier draft of this block (NVARCHAR text, no updated_at).
IF COL_LENGTH(N'dbo.delivery_status_history', N'updated_at') IS NULL
    ALTER TABLE delivery_status_history ADD updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET();
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'delivery_status_history'
           AND COLUMN_NAME = 'status_message' AND DATA_TYPE = 'nvarchar')
    ALTER TABLE delivery_status_history ALTER COLUMN status_message VARCHAR(500) NULL;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'delivery_status_history'
           AND COLUMN_NAME = 'updated_by' AND DATA_TYPE = 'nvarchar')
    ALTER TABLE delivery_status_history ALTER COLUMN updated_by VARCHAR(255) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_dsh_delivery_id' AND object_id = OBJECT_ID(N'dbo.delivery_status_history'))
    CREATE INDEX idx_dsh_delivery_id ON delivery_status_history(delivery_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_dsh_order_id' AND object_id = OBJECT_ID(N'dbo.delivery_status_history'))
    CREATE INDEX idx_dsh_order_id ON delivery_status_history(order_id);
GO

-- Backfill: deliveries created before tracking existed get a timeline rebuilt from what was recorded -
-- the order placement, every delivery attempt, and the current status if no attempt covers it.
-- Deliveries that already have history are never touched, so this is safe to re-run.
-- All-or-nothing, so a failure can never leave a delivery with a partial timeline.
-- CAST (not LEFT) also works on databases whose notes/failure_reason columns are the legacy TEXT type.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @NoHistory TABLE (delivery_id BIGINT PRIMARY KEY);
INSERT INTO @NoHistory (delivery_id)
SELECT d.id FROM deliveries d
WHERE NOT EXISTS (SELECT 1 FROM delivery_status_history h WHERE h.delivery_id = d.id);

INSERT INTO delivery_status_history (delivery_id, order_id, status, status_message, updated_by, created_at, updated_at)
SELECT d.id, d.order_id, 'PENDING', 'Order placed', 'System', o.created_at, o.created_at
FROM @NoHistory n JOIN deliveries d ON d.id = n.delivery_id JOIN orders o ON o.id = d.order_id;

INSERT INTO delivery_status_history (delivery_id, order_id, status, status_message, updated_by, created_at, updated_at)
SELECT d.id, d.order_id, a.status, CAST(a.notes AS VARCHAR(500)), 'System', a.attempt_timestamp, a.attempt_timestamp
FROM @NoHistory n JOIN deliveries d ON d.id = n.delivery_id JOIN delivery_attempts a ON a.delivery_id = d.id;

INSERT INTO delivery_status_history (delivery_id, order_id, status, status_message, updated_by, created_at, updated_at)
SELECT d.id, d.order_id, d.delivery_status,
       CASE d.delivery_status
           WHEN 'CANCELLED' THEN 'Order cancelled'
           WHEN 'ASSIGNED' THEN 'Delivery driver assigned'
           WHEN 'OUT_FOR_DELIVERY' THEN 'Package is out for delivery'
           WHEN 'DELIVERED' THEN 'Delivered to customer'
           WHEN 'FAILED' THEN ISNULL(CAST(d.failure_reason AS VARCHAR(500)), 'Delivery attempt failed')
           ELSE 'Status updated' END,
       'System', d.updated_at, d.updated_at
FROM @NoHistory n JOIN deliveries d ON d.id = n.delivery_id
WHERE d.delivery_status <> 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM delivery_attempts a WHERE a.delivery_id = d.id AND a.status = d.delivery_status);
COMMIT TRANSACTION;
GO

-- ===== V13__restock_separation_of_duties.sql =====
-- Restock workflow with separation of duties:
--   Inventory Officer (INVENTORY_STAFF) creates -> Manager approves/rejects -> Inventory Officer receives.
--   Status flow: PENDING -> APPROVED -> RECEIVED, PENDING -> REJECTED.
-- Adds the audit-trail columns, backfills them from what older rows recorded, and enforces at most one
-- open (PENDING/APPROVED) request per product and at most one RECEIVED_STOCK movement per request.
-- SAFE TO RERUN: additive only; no row is deleted, and a guard that existing data would violate is
-- skipped with a message instead of failing (the application enforces the same rules).
IF COL_LENGTH(N'dbo.restock_requests', N'requested_at') IS NULL      ALTER TABLE restock_requests ADD requested_at DATETIMEOFFSET NULL;
IF COL_LENGTH(N'dbo.restock_requests', N'approved_at') IS NULL       ALTER TABLE restock_requests ADD approved_at DATETIMEOFFSET NULL;
IF COL_LENGTH(N'dbo.restock_requests', N'approved_quantity') IS NULL ALTER TABLE restock_requests ADD approved_quantity INT NULL;
IF COL_LENGTH(N'dbo.restock_requests', N'rejected_by_id') IS NULL    ALTER TABLE restock_requests ADD rejected_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.restock_requests', N'rejected_at') IS NULL       ALTER TABLE restock_requests ADD rejected_at DATETIMEOFFSET NULL;
IF COL_LENGTH(N'dbo.restock_requests', N'rejection_reason') IS NULL  ALTER TABLE restock_requests ADD rejection_reason VARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.restock_requests', N'received_by_id') IS NULL    ALTER TABLE restock_requests ADD received_by_id BIGINT NULL;
IF COL_LENGTH(N'dbo.restock_requests', N'received_at') IS NULL       ALTER TABLE restock_requests ADD received_at DATETIMEOFFSET NULL;
GO

-- Backfill older rows from the data they already hold (only NULL audit fields are filled).
SET XACT_ABORT ON;
BEGIN TRANSACTION;

UPDATE restock_requests SET requested_at = created_at WHERE requested_at IS NULL;

-- The old workflow approved the full requested quantity.
UPDATE restock_requests SET approved_quantity = requested_quantity
WHERE approved_quantity IS NULL AND status IN ('APPROVED', 'RECEIVED');

-- An APPROVED row's last update was its approval. (A RECEIVED row's approval time was not recorded; left NULL.)
UPDATE restock_requests SET approved_at = updated_at
WHERE approved_at IS NULL AND status = 'APPROVED' AND approved_by_id IS NOT NULL;

-- The old workflow stored the rejecting user in approved_by_id; move it to rejected_by_id.
UPDATE restock_requests
SET rejected_by_id = approved_by_id, approved_by_id = NULL, rejected_at = COALESCE(rejected_at, updated_at),
    rejection_reason = COALESCE(rejection_reason, 'Rejected before rejection reasons were recorded')
WHERE status = 'REJECTED' AND rejected_by_id IS NULL AND approved_by_id IS NOT NULL;
UPDATE restock_requests SET rejection_reason = 'Rejected before rejection reasons were recorded'
WHERE status = 'REJECTED' AND rejection_reason IS NULL;

-- Receipt actor and time come from the RECEIVED_STOCK movement written at receipt.
UPDATE r
SET r.received_by_id = COALESCE(r.received_by_id, m.actor_id), r.received_at = COALESCE(r.received_at, m.created_at)
FROM restock_requests r
CROSS APPLY (SELECT TOP 1 h.actor_id, h.created_at FROM stock_movement_history h
             WHERE h.movement_type = 'RECEIVED_STOCK' AND h.reference_id = 'RESTOCK_REQ_' + CAST(r.id AS VARCHAR(20))
             ORDER BY h.created_at, h.id) m
WHERE r.status = 'RECEIVED' AND (r.received_by_id IS NULL OR r.received_at IS NULL)
  AND (m.actor_id IS NULL OR EXISTS (SELECT 1 FROM app_users u WHERE u.id = m.actor_id));

COMMIT TRANSACTION;
GO

-- requested_at is always known from here on.
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.restock_requests') AND name = 'requested_at' AND is_nullable = 1)
    ALTER TABLE restock_requests ALTER COLUMN requested_at DATETIMEOFFSET NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'dbo.restock_requests')
               AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'dbo.restock_requests'), 'requested_at', 'ColumnId'))
    ALTER TABLE restock_requests ADD CONSTRAINT df_restock_requested_at DEFAULT SYSDATETIMEOFFSET() FOR requested_at;

IF OBJECT_ID(N'dbo.fk_restock_rejecter', N'F') IS NULL
    ALTER TABLE restock_requests ADD CONSTRAINT fk_restock_rejecter FOREIGN KEY (rejected_by_id) REFERENCES app_users(id);
IF OBJECT_ID(N'dbo.fk_restock_receiver', N'F') IS NULL
    ALTER TABLE restock_requests ADD CONSTRAINT fk_restock_receiver FOREIGN KEY (received_by_id) REFERENCES app_users(id);

IF OBJECT_ID(N'dbo.ck_restock_status', N'C') IS NULL
   AND NOT EXISTS (SELECT 1 FROM restock_requests WHERE status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'RECEIVED'))
    ALTER TABLE restock_requests ADD CONSTRAINT ck_restock_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'RECEIVED'));

IF OBJECT_ID(N'dbo.ck_restock_approved_quantity', N'C') IS NULL
   AND NOT EXISTS (SELECT 1 FROM restock_requests WHERE approved_quantity <= 0 OR approved_quantity > requested_quantity)
    ALTER TABLE restock_requests ADD CONSTRAINT ck_restock_approved_quantity
        CHECK (approved_quantity IS NULL OR (approved_quantity > 0 AND approved_quantity <= requested_quantity));

IF OBJECT_ID(N'dbo.ck_restock_received_within_approved', N'C') IS NULL
   AND NOT EXISTS (SELECT 1 FROM restock_requests WHERE received_quantity <= 0
                   OR received_quantity > COALESCE(approved_quantity, requested_quantity))
    ALTER TABLE restock_requests ADD CONSTRAINT ck_restock_received_within_approved
        CHECK (received_quantity IS NULL OR (received_quantity > 0 AND received_quantity <= COALESCE(approved_quantity, requested_quantity)));

IF OBJECT_ID(N'dbo.ck_restock_rejection_reason', N'C') IS NULL
   AND NOT EXISTS (SELECT 1 FROM restock_requests WHERE status = 'REJECTED' AND rejection_reason IS NULL)
    ALTER TABLE restock_requests ADD CONSTRAINT ck_restock_rejection_reason
        CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL);
GO

-- At most one open (PENDING or APPROVED) request per product.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_restock_open_product' AND object_id = OBJECT_ID(N'dbo.restock_requests'))
BEGIN
    IF EXISTS (SELECT product_id FROM restock_requests WHERE status IN ('PENDING', 'APPROVED') GROUP BY product_id HAVING COUNT(*) > 1)
        PRINT 'WARNING: some products have more than one PENDING/APPROVED restock request. Resolve them, then rerun to create uk_restock_open_product.';
    ELSE
        CREATE UNIQUE INDEX uk_restock_open_product ON restock_requests(product_id) WHERE status IN ('PENDING', 'APPROVED');
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_restock_status' AND object_id = OBJECT_ID(N'dbo.restock_requests'))
    CREATE INDEX idx_restock_status ON restock_requests(status, created_at);

-- A restock request is received at most once, so it has at most one RECEIVED_STOCK movement.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_movement_restock_receipt' AND object_id = OBJECT_ID(N'dbo.stock_movement_history'))
BEGIN
    IF EXISTS (SELECT reference_id FROM stock_movement_history WHERE movement_type = 'RECEIVED_STOCK' AND reference_id IS NOT NULL
               GROUP BY reference_id HAVING COUNT(*) > 1)
        PRINT 'WARNING: duplicate RECEIVED_STOCK movements exist for one reference. Review them, then rerun to create uk_movement_restock_receipt.';
    ELSE
        CREATE UNIQUE INDEX uk_movement_restock_receipt ON stock_movement_history(reference_id)
            WHERE movement_type = 'RECEIVED_STOCK' AND reference_id IS NOT NULL;
END;
GO

SET NOEXEC OFF;
GO

USE RapidMartDB
Select * FROM INFORMATION_SCHEMA.TABLES

SELECT * FROM user_roles

SELECT * FROM customer_addresses
 
SELECT * FROM app_users
SELECT
    @@SERVERNAME AS ServerName,
    DB_NAME() AS DatabaseName,
    SERVERPROPERTY('InstanceName') AS InstanceName,
    CONNECTIONPROPERTY('local_net_address') AS ServerAddress,
    CONNECTIONPROPERTY('local_tcp_port') AS ServerPort;
    USE RapidMartDB;
GO

USE RapidMartDB;
GO

USE RapidMartDB;
GO

SELECT order_number, status
FROM dbo.orders
WHERE order_number IN
(
    'RM-DEMO-0001',
    'RM-DEMO-0002',
    'RM-DEMO-0003',
    'RM-DEMO-0004',
    'RM-DEMO-0005'
)
ORDER BY order_number;
BEGIN TRANSACTION;
