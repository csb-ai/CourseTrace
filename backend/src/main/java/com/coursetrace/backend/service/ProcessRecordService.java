package com.coursetrace.backend.service;

import com.coursetrace.backend.dto.AnalysisMode;
import com.coursetrace.backend.dto.AnalysisProcessRecord;
import com.coursetrace.backend.dto.TaskEvent;
import com.coursetrace.backend.dto.TaskStage;
import com.coursetrace.backend.dto.TaskStatus;
import com.coursetrace.backend.mapper.AnalysisProcessRecordMapper;
import com.coursetrace.backend.utils.AnalysisTaskKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Store only real stage notifications, not reports, audio, credentials, or imagined intermediate steps. */
@Service
public class ProcessRecordService {
    private static final Logger log = LoggerFactory.getLogger(ProcessRecordService.class);
    private final AnalysisProcessRecordMapper mapper;

    public ProcessRecordService(AnalysisProcessRecordMapper mapper) {
        this.mapper = mapper;
    }

    public List<AnalysisProcessRecord> list(Long mediaId, String goal, AnalysisMode mode) {
        AnalysisMode resolved = mode == null ? AnalysisMode.GENERAL : mode;
        // Do not hide query failures behind an empty list: the UI must distinguish unavailable from no history.
        return mapper.list(mediaId, AnalysisTaskKeys.goalDigest(goal, resolved), resolved.name());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean record(Long mediaId, String goal, AnalysisMode mode, TaskEvent event) {
        AnalysisMode resolved = mode == null ? AnalysisMode.GENERAL : mode;
        try {
            // Autocommit separately from any business transaction so a later task failure keeps its actual history.
            mapper.insert(mediaId, AnalysisTaskKeys.goalDigest(goal, resolved), resolved.name(),
                    event.state().name(), event.stage() == null ? null : event.stage().name(),
                    safeMessage(event.message()));
            return true;
        } catch (RuntimeException error) {
            // Persisting a timeline is useful, but it must not break the real job or its live SSE notifications.
            log.warn("analysis_process_record_persist_failed mediaId={} mode={} stage={}",
                    mediaId, resolved, event.stage(), error);
            return false;
        }
    }

    /** Call only after the server has actually found a reusable completed result. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean recordReuse(Long mediaId, String goal, AnalysisMode mode) {
        return record(mediaId, goal, mode, new TaskEvent(TaskStatus.State.COMPLETED, null,
                "同一视频、目标和模式已有完成结果，本次直接复用，未重新调用模型。", TaskStage.COMPLETED_REUSED));
    }

    private static String safeMessage(String message) {
        if (message == null) return null;
        String redacted = message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [redacted]")
                .replaceAll("(?i)sk-[a-z0-9_-]{12,}", "[redacted]");
        return redacted.length() > 1000 ? redacted.substring(0, 1000) : redacted;
    }
}
