-- Run against RapidMartDB (and RapidMartTestDB for tests). Safe to run again.
IF OBJECT_ID(N'dbo.customer_password_resets', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.customer_password_resets (
        user_id BIGINT NOT NULL PRIMARY KEY,
        code_hash VARCHAR(100) NOT NULL,
        requested_at DATETIMEOFFSET NOT NULL,
        expires_at DATETIMEOFFSET NOT NULL,
        attempts INT NOT NULL DEFAULT 0,
        CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES dbo.app_users(id) ON DELETE CASCADE,
        CONSTRAINT ck_password_reset_attempts CHECK (attempts >= 0)
    );
END;
