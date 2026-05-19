CREATE TABLE rate_limit_rules (
    id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    limit_count BIGINT NOT NULL,
    window_seconds BIGINT NOT NULL,
    bucket_seconds BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL,
    priority INT NOT NULL,
    default_limit_count BIGINT NOT NULL,
    default_window_seconds BIGINT NOT NULL,
    default_bucket_seconds BIGINT NOT NULL,
    default_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_rate_limit_rules_code UNIQUE (code),
    CONSTRAINT fk_rate_limit_rules_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rate_limit_rules_enabled_priority
    ON rate_limit_rules (enabled, priority);

CREATE INDEX idx_rate_limit_rules_path_method
    ON rate_limit_rules (path_pattern, http_method);

CREATE INDEX idx_rate_limit_rules_event_type
    ON rate_limit_rules (event_type);

INSERT INTO rate_limit_rules (
    id,
    code,
    name,
    description,
    path_pattern,
    http_method,
    event_type,
    limit_count,
    window_seconds,
    bucket_seconds,
    enabled,
    priority,
    default_limit_count,
    default_window_seconds,
    default_bucket_seconds,
    default_enabled
) VALUES
    (1, 'AUTH_LOGIN', 'Authentication login', 'Rate limit for public login attempts.', '/api/auth/login', 'POST', 'LOGIN', 5, 300, 30, TRUE, 10, 5, 300, 30, TRUE),
    (2, 'AUTH_REFRESH', 'Authentication refresh', 'Rate limit for token refresh attempts.', '/api/auth/refresh', 'POST', 'REFRESH', 10, 60, 6, TRUE, 20, 10, 60, 6, TRUE),
    (3, 'PATIENT_REGISTER', 'Patient registration', 'Rate limit for patient self-registration.', '/api/auth/patient/register', 'POST', 'PATIENT_REGISTER', 10, 60, 6, TRUE, 30, 10, 60, 6, TRUE),
    (4, 'FORGOT_PASSWORD', 'Forgot password', 'Rate limit for forgot password requests.', '/api/auth/password/forgot', 'POST', 'FORGOT_PASSWORD', 10, 60, 6, TRUE, 40, 10, 60, 6, TRUE),
    (5, 'PUBLIC_CREATE_APPOINTMENT', 'Public appointment creation', 'Rate limit for public appointment creation.', '/api/public/appointments', 'POST', 'CREATE_APPOINTMENT', 20, 60, 6, TRUE, 50, 20, 60, 6, TRUE),
    (6, 'PUBLIC_CONTACT', 'Public contact submission', 'Rate limit for public contact submissions.', '/api/public/contact', 'POST', 'PUBLIC_CONTACT', 10, 60, 6, TRUE, 60, 10, 60, 6, TRUE),
    (7, 'PUBLIC_ASSISTANT_ASK', 'Public assistant ask', 'Rate limit for public assistant ask requests.', '/api/public/assistant/ask', 'POST', 'PUBLIC_ASSISTANT_ASK', 10, 60, 6, TRUE, 70, 10, 60, 6, TRUE),
    (8, 'PUBLIC_ASSISTANT_CHAT', 'Public assistant chat', 'Rate limit for public assistant chat requests.', '/api/public/assistant/chat', 'POST', 'PUBLIC_ASSISTANT_CHAT', 10, 60, 6, TRUE, 80, 10, 60, 6, TRUE),
    (9, 'PUBLIC_ASSISTANT_SELECT_SLOT', 'Public assistant select slot', 'Rate limit for public assistant slot selection.', '/api/public/assistant/select-slot', 'POST', 'PUBLIC_ASSISTANT_SELECT_SLOT', 20, 60, 6, TRUE, 90, 20, 60, 6, TRUE),
    (10, 'AI_BOOKING_CHAT', 'Legacy AI booking chat', 'Rate limit for legacy AI booking chat compatibility route.', '/api/ai/chat', 'POST', 'AI_BOOKING_CHAT', 10, 60, 6, TRUE, 100, 10, 60, 6, TRUE),
    (11, 'AI_BOOKING_SELECT_SLOT', 'Legacy AI booking select slot', 'Rate limit for legacy AI booking slot selection route.', '/api/ai/booking/select-slot', 'POST', 'AI_BOOKING_SELECT_SLOT', 20, 60, 6, TRUE, 110, 20, 60, 6, TRUE),
    (12, 'BOOKING_EMAIL_OTP_REQUEST', 'Booking email OTP request', 'Rate limit for booking email OTP request.', '/api/public/booking-email-otp/request', 'POST', 'BOOKING_EMAIL_OTP_REQUEST', 10, 60, 6, TRUE, 120, 10, 60, 6, TRUE),
    (13, 'BOOKING_EMAIL_OTP_VERIFY', 'Booking email OTP verify', 'Rate limit for booking email OTP verification.', '/api/public/booking-email-otp/verify', 'POST', 'BOOKING_EMAIL_OTP_VERIFY', 20, 60, 6, TRUE, 130, 20, 60, 6, TRUE),
    (14, 'PUBLIC_LOOKUP_APPOINTMENT_REQUEST_OTP', 'Public appointment lookup OTP request', 'Rate limit for public appointment lookup OTP request.', '/api/public/lookup/appointments/request-otp', 'POST', 'PUBLIC_LOOKUP_APPOINTMENT_REQUEST_OTP', 10, 60, 6, TRUE, 140, 10, 60, 6, TRUE),
    (15, 'PUBLIC_LOOKUP_APPOINTMENT_VERIFY_OTP', 'Public appointment lookup OTP verify', 'Rate limit for public appointment lookup OTP verification.', '/api/public/lookup/appointments/verify-otp', 'POST', 'PUBLIC_LOOKUP_APPOINTMENT_VERIFY_OTP', 10, 60, 6, TRUE, 150, 10, 60, 6, TRUE),
    (16, 'PUBLIC_LOOKUP_RESULT_REQUEST_OTP', 'Public result lookup OTP request', 'Rate limit for public result lookup OTP request.', '/api/public/lookup/results/request-otp', 'POST', 'PUBLIC_LOOKUP_RESULT_REQUEST_OTP', 10, 60, 6, TRUE, 160, 10, 60, 6, TRUE),
    (17, 'PUBLIC_LOOKUP_RESULT_VERIFY_OTP', 'Public result lookup OTP verify', 'Rate limit for public result lookup OTP verification.', '/api/public/lookup/results/verify-otp', 'POST', 'PUBLIC_LOOKUP_RESULT_VERIFY_OTP', 10, 60, 6, TRUE, 170, 10, 60, 6, TRUE),
    (18, 'RECEPTION_CHECKIN_QR', 'Reception QR check-in', 'Rate limit for reception QR check-in.', '/api/reception/appointments/check-in/qr', 'POST', 'CHECKIN', 30, 60, 6, TRUE, 180, 30, 60, 6, TRUE),
    (19, 'ADMIN_APPOINTMENT_CHECKIN', 'Admin appointment check-in', 'Rate limit for admin appointment check-in.', '/api/admin/appointments/check-in', 'POST', 'CHECKIN', 30, 60, 6, TRUE, 190, 30, 60, 6, TRUE),
    (20, 'CASHIER_INVOICE_CREATE', 'Cashier invoice creation', 'Rate limit for cashier service order invoice creation.', '/api/cashier/service-orders/', 'POST', 'INVOICE_CREATE', 20, 60, 6, TRUE, 200, 20, 60, 6, TRUE);
