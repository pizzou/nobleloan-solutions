-- Capture original public form responses separately from the financial Loan aggregate.
-- The Java entity uses the existing AES-GCM CryptoConverter; this column
-- contains encrypted ciphertext and must never be filled with plaintext JSON.
CREATE TABLE IF NOT EXISTS loan_application_snapshots (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL UNIQUE,
    encrypted_answers TEXT NOT NULL,
    CONSTRAINT fk_loan_application_snapshot_loan
        FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
);
COMMENT ON TABLE loan_application_snapshots IS
    'Original public loan application answers, encrypted at rest; no financial state.';
