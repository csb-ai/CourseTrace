package com.coursetrace.backend.dto;

import com.coursetrace.backend.entity.MediaFile;

import java.time.LocalDateTime;

public record MediaSummary(
        Long id,
        String filename,
        String status,
        String coverUrl,
        LocalDateTime uploadTime,
        boolean reused
) {
    public static MediaSummary from(MediaFile mediaFile) {
        return new MediaSummary(
                mediaFile.getId(),
                mediaFile.getFilename(),
                mediaFile.getStatus(),
                mediaFile.getCoverUrl(),
                mediaFile.getUploadTime(),
                mediaFile.isReused());
    }
}
