package com.coursetrace.backend.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface InteractiveCacheMapper {
    @Select("SELECT payload FROM interactive_result_cache WHERE media_id=#{mediaId} AND kind=#{kind} AND cache_key=#{key}")
    String find(@Param("mediaId") Long mediaId, @Param("kind") String kind, @Param("key") String key);

    @Insert("""
            INSERT INTO interactive_result_cache(media_id,kind,cache_key,payload)
            VALUES(#{mediaId},#{kind},#{key},#{payload})
            ON DUPLICATE KEY UPDATE payload=VALUES(payload), created_at=CURRENT_TIMESTAMP(3)
            """)
    void save(@Param("mediaId") Long mediaId, @Param("kind") String kind,
              @Param("key") String key, @Param("payload") String payload);

    @Delete("DELETE FROM interactive_result_cache WHERE media_id=#{mediaId}")
    void deleteMedia(@Param("mediaId") Long mediaId);
}
