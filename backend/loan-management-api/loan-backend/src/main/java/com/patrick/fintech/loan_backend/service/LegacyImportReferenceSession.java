package com.patrick.fintech.loan_backend.service;

import java.util.HashSet;
import java.util.Set;

/** Per-file duplicate tracking and an efficient, read-only preview cursor. */
public final class LegacyImportReferenceSession {
    private final Set<String> seenFingerprints = new HashSet<>();
    private final Set<String> seenSourceReferences = new HashSet<>();
    private long previewCursor;

    public boolean alreadySeen(String fingerprint, String sourceReference) {
        return seenFingerprints.contains(fingerprint)
                || (sourceReference != null && seenSourceReferences.contains(sourceReference.toUpperCase(java.util.Locale.ROOT)));
    }

    public void recordSuccess(String fingerprint, String sourceReference) {
        seenFingerprints.add(fingerprint);
        if (sourceReference != null) {
            seenSourceReferences.add(sourceReference.toUpperCase(java.util.Locale.ROOT));
        }
    }

    public String previewReference(LoanReferenceService references) {
        previewCursor = Math.max(previewCursor, references.firstAvailableForPreview());
        return references.format(previewCursor++);
    }
}
