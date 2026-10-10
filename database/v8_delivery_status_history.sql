-- Migration Script: v8_delivery_status_history.sql
-- Description: Creates delivery_status_history table and populates initial history entries for existing deliveries.

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'delivery_status_history')
BEGIN
    CREATE TABLE delivery_status_history (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        delivery_id BIGINT NOT NULL,
        order_id BIGINT NOT NULL,
        status VARCHAR(50) NOT NULL,
        status_message NVARCHAR(500) NULL,
        updated_by NVARCHAR(255) NULL,
        created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        CONSTRAINT fk_dsh_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries(id) ON DELETE CASCADE,
        CONSTRAINT fk_dsh_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
    );

    CREATE INDEX idx_dsh_delivery_id ON delivery_status_history(delivery_id);
    CREATE INDEX idx_dsh_order_id ON delivery_status_history(order_id);
END
GO

-- Backfill initial history record for any existing deliveries that don't have one yet
INSERT INTO delivery_status_history (delivery_id, order_id, status, status_message, updated_by, created_at)
SELECT d.id, d.order_id, 
       CASE 
           WHEN d.delivery_status = 'PENDING' THEN 'ORDER_CONFIRMED'
           WHEN d.delivery_status = 'FAILED' THEN 'DELIVERY_FAILED'
           ELSE d.delivery_status
       END,
       N'Initial status record', N'System', COALESCE(d.created_at, SYSDATETIMEOFFSET())
FROM deliveries d
WHERE NOT EXISTS (
    SELECT 1 FROM delivery_status_history dsh WHERE dsh.delivery_id = d.id
);
GO
