-- PrimeCare Flyway migration
-- Purpose: allow all current enum UserRole values, especially SERVICE_TECHNICIAN.

ALTER TABLE users
  MODIFY COLUMN role VARCHAR(32) NOT NULL;
