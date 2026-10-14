-- ===== V13__restock_separation_of_duties.sql =====
-- Restock workflow with separation of duties:
--   Inventory Officer (INVENTORY_STAFF) creates -> Manager approves/rejects -> Inventory Officer receives.
--   Status flow: PENDING -> APPROVED -> RECEIVED, PENDING -> REJECTED.
-- Adds the audit-trail columns, backfills them from what older rows recorded, and enforces at most one
-- open (PENDING/APPROVED) request per product and at most one RECEIVED_STOCK movement per request.
-- SAFE TO RERUN: additive only; no row is deleted, and a guard that existing data would violate is
-- skipped with a message instead of failing (the application enforces the same rules).
-- Standalone use: run against RapidMartDB (and RapidMartTestDB). The canonical RapidMartDB_SSMS.sql
-- already contains this block.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
GO

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
