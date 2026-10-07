package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisMode;
import com.coursetrace.backend.dto.VideoEvidenceHit;
import com.coursetrace.backend.mapper.InteractiveCacheMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** 持久化已经真实生成的独立问答和检索；依赖指代/历史的追问不使用此缓存。 */
@Service
public class InteractiveCacheService {
    private static final Logger log = LoggerFactory.getLogger(InteractiveCacheService.class);
    private final InteractiveCacheMapper mapper;
    private final ObjectMapper json;

    public InteractiveCacheService(InteractiveCacheMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public String answer(Long mediaId, String goal, AnalysisMode mode, String question) {
        if (!standalone(question)) return null;
        String payload = mapper.find(mediaId, "answer-v2", key(goal, mode.name(), question));
        if (payload == null) return null;
        try { return json.readValue(payload, String.class); }
        catch (Exception error) { log.warn("answer_cache_invalid mediaId={}", mediaId); return null; }
    }

    public void saveAnswer(Long mediaId, String goal, AnalysisMode mode, String question, String answer) {
        if (!standalone(question)) return;
        try { mapper.save(mediaId, "answer-v2", key(goal, mode.name(), question), json.writeValueAsString(answer)); }
        catch (Exception error) { log.warn("answer_cache_write_failed mediaId={}", mediaId, error); }
    }

    public List<VideoEvidenceHit> evidence(Long mediaId, String query) {
        String payload = mapper.find(mediaId, "evidence-v2", key(query));
        if (payload == null) return null;
        try { return json.readValue(payload, new TypeReference<List<VideoEvidenceHit>>() { }); }
        catch (Exception error) { log.warn("evidence_cache_invalid mediaId={}", mediaId); return null; }
    }

    public void saveEvidence(Long mediaId, String query, List<VideoEvidenceHit> hits) {
        try { mapper.save(mediaId, "evidence-v2", key(query), json.writeValueAsString(hits)); }
        catch (Exception error) { log.warn("evidence_cache_write_failed mediaId={}", mediaId, error); }
    }

    boolean standalone(String question) {
        return question != null && question.trim().length() >= 16
                && !question.matches("(?s).*(刚才|上一个|上一条|前面|继续|它|再详细|再解释|第一条|第二条).*?");
    }

    private String key(String... parts) {
        try {
            byte[] raw = json.writeValueAsBytes(parts);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (Exception error) { throw new IllegalStateException("无法计算交互结果身份", error); }
    }
}
