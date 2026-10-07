package com.coursetrace.backend.mapper;

import com.coursetrace.backend.dto.AnalysisProcessRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AnalysisProcessRecordMapper {
    @Insert("""
            INSERT INTO analysis_process_records(media_id, goal_hash, mode, state, stage, message)
            VALUES(#{mediaId}, #{goalHash}, #{mode}, #{state}, #{stage}, #{message})
            """)
    void insert(@Param("mediaId") Long mediaId, @Param("goalHash") String goalHash,
                @Param("mode") String mode, @Param("state") String state,
                @Param("stage") String stage, @Param("message") String message);

    /** Keep the most recent 500 actual events, and present them in their insertion order. */
    @Select("""
            SELECT id, media_id, goal_hash, mode, state, stage, message, created_at
            FROM (
                SELECT id, media_id, goal_hash, mode, state, stage, message, created_at
                FROM analysis_process_records
                WHERE media_id = #{mediaId} AND goal_hash = #{goalHash} AND mode = #{mode}
                ORDER BY id DESC LIMIT 500
            ) recent ORDER BY id ASC
            """)
    List<AnalysisProcessRecord> list(@Param("mediaId") Long mediaId,
                                     @Param("goalHash") String goalHash, @Param("mode") String mode);
}
