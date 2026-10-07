package com.coursetrace.backend.controller;

import com.coursetrace.backend.entity.MediaFile;
import com.coursetrace.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MediaControllerTest {
    @Test void uploadReceiptExposesReuseWithoutLeakingStoragePath() throws Exception {
        var upload = mock(ChunkUploadService.class);
        var media = new MediaFile();
        media.setId(7L); media.setFilename("course.mp4"); media.setStatus("COMPLETED");
        media.setReused(true); media.setFilePath("private-storage-location");
        when(upload.complete("upload-id", 3L)).thenReturn(media);
        var mvc = MockMvcBuilders.standaloneSetup(new MediaController(upload,
                mock(MediaIngestService.class), mock(MediaService.class))).build();
        mvc.perform(post("/media/complete-upload").param("uploadId", "upload-id")
                        .requestAttr(AuthService.REQUEST_USER_ID, 3L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.reused").value(true))
                .andExpect(jsonPath("$.data.filePath").doesNotExist());
    }
}
