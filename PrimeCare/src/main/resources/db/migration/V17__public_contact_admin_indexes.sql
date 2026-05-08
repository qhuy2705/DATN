CREATE INDEX idx_public_contact_status_created_at
    ON public_contact_submissions(status, created_at);
