package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisResult;
import com.coursetrace.backend.dto.VideoContext;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class EvidenceVerificationService {

    public boolean timestampCovered(VideoContext context, AnalysisResult.Evidence evidence) {
        return context != null && evidence != null && context.segments().stream()
                .anyMatch(segment -> containsTimestamp(segment, evidence.timestampMs()));
    }

    public boolean supported(VideoContext context, AnalysisResult.Evidence evidence) {
        if (context == null || evidence == null || evidence.content().isBlank()) return false;
        // 省略号引用可能把两句拼成不存在的连续原文，不能在去标点后被误接纳。
        if (evidence.content().contains("...") || evidence.content().contains("…")) return false;
        String source = evidence.source().toUpperCase(Locale.ROOT);
        if (!source.equals("ASR") && !source.equals("OCR") && !source.equals("ASR+OCR")) return false;

        return context.segments().stream()
                .filter(segment -> containsTimestamp(segment, evidence.timestampMs()))
                .map(segment -> sourceText(segment, source))
                .anyMatch(candidate -> textMatches(evidence.content(), candidate));
    }

    public boolean supportsClaim(VideoContext context,
                                 String claim,
                                 AnalysisResult.Evidence evidence) {
        return evidence != null
                && claim != null && !claim.isBlank()
                && claim.trim().equals(evidence.claim())
                && supported(context, evidence);
    }

    private boolean containsTimestamp(VideoContext.VideoSegment segment, long timestampMs) {
        return timestampMs >= segment.startMs() && timestampMs < segment.endMs();
    }

    private String sourceText(VideoContext.VideoSegment segment, String source) {
        if (source.contains("ASR") && source.contains("OCR")) {
            return segment.transcript() + " " + String.join(" ", segment.ocrTexts());
        }
        if (source.contains("ASR")) return segment.transcript();
        return String.join(" ", segment.ocrTexts());
    }

    private boolean textMatches(String evidence, String candidate) {
        String normalizedEvidence = normalize(evidence);
        String normalizedCandidate = normalize(candidate);
        return !normalizedEvidence.isEmpty()
                && !normalizedCandidate.isEmpty()
                && normalizedCandidate.contains(normalizedEvidence);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }
}
