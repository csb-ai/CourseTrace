package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.VideoContext;

final class VideoMaterialNormalizer {
    private VideoMaterialNormalizer() { }

    static VideoContext normalize(VideoContext context, long durationMs) {
        var segments = context.segments().stream()
                .filter(segment -> segment.startMs() < durationMs)
                .map(segment -> new VideoContext.VideoSegment(segment.startMs(),
                        Math.min(segment.endMs(), durationMs), segment.transcript(),
                        segment.ocrTexts().stream()
                                .map(text -> text.replaceAll("(?m)^\\s*Estimating resolution as \\d+\\s*$", "").trim())
                                .filter(text -> !text.isBlank()).toList(), segment.evidenceFrames()))
                .toList();
        return new VideoContext(context.source(), context.userGoal(), segments);
    }
}
