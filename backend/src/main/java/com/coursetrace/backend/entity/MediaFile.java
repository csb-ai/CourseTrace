package com.coursetrace.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("media_files")
public class MediaFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String filename;
    private String status;
    private String filePath;
    private String contentHash;

    /** 仅描述这次上传响应；不存入媒体行，不能从文件名猜测是否复用。 */
    @TableField(exist = false)
    private boolean reused;

    private String aiSummary;
    private String transcriptText;
    private String coverUrl;

    private LocalDateTime uploadTime;
}
