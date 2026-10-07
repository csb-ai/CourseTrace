package com.coursetrace.backend.dto;

import java.time.Instant;

/** A stage actually published by the backend, never a reconstructed animation frame. */
public record AnalysisProcessRecord(Long id, Long mediaId, String goalHash, String mode,
                                    String state, String stage, String message, Instant createdAt) {
}
