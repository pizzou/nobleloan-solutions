CREATE TABLE borrower_document_review_notices (
    borrower_id bigint NOT NULL REFERENCES borrowers(id) ON DELETE CASCADE,
    fingerprint varchar(36) NOT NULL,
    PRIMARY KEY (borrower_id, fingerprint)
);
