package com.patrick.fintech.loan_backend.service;

import java.util.HashSet;
import java.util.Set;

/** Per-file duplicate tracking and an efficient, read-only preview cursor. */
public final class LegacyImportReferenceSession {
    private final Set<String> seenFingerprints = new HashSet<>();
    private final Set<String> seenSourceReferences = new HashSet<>();
    private final Set<String> seenIdentities = new HashSet<>();
    private long previewCursor;

    public boolean alreadySeen(String fingerprint, String sourceReference) {
        return seenFingerprints.contains(fingerprint)
                || (sourceReference != null
                        && seenSourceReferences.contains(sourceReference.toUpperCase(java.util.Locale.ROOT)));
    }

    public boolean alreadySeenIdentity(String identity) {
        return identity != null && seenIdentities.contains(identity);
    }

    public void recordSuccess(String fingerprint, String sourceReference, String identity) {
        seenFingerprints.add(fingerprint);
        if (identity != null)
            seenIdentities.add(identity);
        if (sourceReference != null) {
            seenSourceReferences.add(sourceReference.toUpperCase(java.util.Locale.ROOT));
        }
    }

    public String previewReference(LoanReferenceService references) {
        previewCursor = Math.max(previewCursor, references.firstAvailableForPreview());
        return references.format(previewCursor++);
    }
}
