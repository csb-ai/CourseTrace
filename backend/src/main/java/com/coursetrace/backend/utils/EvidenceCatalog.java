package com.coursetrace.backend.utils;

import com.coursetrace.backend.dto.AnalysisResult;
import com.coursetrace.backend.dto.VideoContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The model selects source IDs; Java supplies the unchanged quote and its actual time window. */
public final class EvidenceCatalog {
    public record Entry(String id, long timestampMs, long endMs, String source, String content) { }
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public EvidenceCatalog(VideoContext context) {
        int sequence = 0;
        for (var segment : context.segments()) {
            add("E" + (++sequence), segment.startMs(), segment.endMs(), "ASR", segment.transcript());
            for (String text : segment.ocrTexts()) {
                add("E" + (++sequence), segment.startMs(), segment.endMs(), "OCR", text);
            }
        }
    }

    private void add(String prefix, long start, long end, String source, String text) {
        if (text == null || text.isBlank()) return;
        // Split only at sentence boundaries. Each quote remains a literal contiguous substring.
        var matcher = java.util.regex.Pattern.compile("[^。！？!?]+[。！？!?]*").matcher(text);
        List<String> sentences = new ArrayList<>();
        while (matcher.find()) sentences.add(matcher.group().trim());
        int index = 0;
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (sentence.isBlank()) continue;
            String id = prefix + "_" + (++index);
            entries.put(id, new Entry(id, start, end, source, sentence));
        }
    }

    public List<Entry> entries() { return List.copyOf(entries.values()); }

    public AnalysisResult resolve(AnalysisResult result) {
        var conclusions = result.conclusions().stream()
                .map(claim -> entries.containsKey(claim) ? entries.get(claim).content() : claim).toList();
        var evidence = result.evidence().stream().map(item -> {
            Entry entry = entries.get(item.content());
            String claim = entries.containsKey(item.claim()) ? entries.get(item.claim()).content() : item.claim();
            // An unknown ID stays invalid and will fail the existing verifier; never guess a quote.
            return entry == null ? item : new AnalysisResult.Evidence(
                    entry.timestampMs(), entry.source(), entry.content(), claim);
        }).toList();
        return new AnalysisResult(result.title(), conclusions, evidence,
                result.suggestions(), result.sections());
    }
}
