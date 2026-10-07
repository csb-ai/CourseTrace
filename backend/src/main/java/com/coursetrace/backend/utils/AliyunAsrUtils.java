package com.coursetrace.backend.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class AliyunAsrUtils {

    private static final Logger log = LoggerFactory.getLogger(AliyunAsrUtils.class);
    private static final int MAX_ATTEMPTS = 3;

    private final String apiKey;
    private final String transcriptionUrl;
    private final String model;
    private final String protocol;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES)
            .writeTimeout(3, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build();

    public AliyunAsrUtils(String apiKey, String transcriptionUrl, String model) {
        this(apiKey, transcriptionUrl, model, "multipart");
    }

    @Autowired
    public AliyunAsrUtils(@Value("${ai.deepseek.api-key}") String apiKey,
                          @Value("${ai.asr.url}") String transcriptionUrl,
                          @Value("${ai.asr.model}") String model,
                          @Value("${ai.asr.protocol:multipart}") String protocol) {
        this.apiKey = apiKey;
        this.transcriptionUrl = transcriptionUrl;
        this.model = model;
        this.protocol = protocol.trim().toLowerCase(Locale.ROOT);
        if (!this.protocol.equals("multipart") && !this.protocol.equals("chat-completions")) {
            throw new IllegalArgumentException("Unsupported ASR protocol");
        }
    }

    public String audioToText(String filePath) {
        File file = new File(filePath);
        // 这里的音频是本流水线上一步用 ffmpeg 切出来的，缺失属于「意外状态」而非「调用方参数错误」，
        // 重跑流水线可以重新生成，因此用 IllegalStateException 保持它可重试，
        // 不要和下面表示「请求本身不合法、重试无意义」的 IllegalArgumentException 混用。
        if (!file.isFile()) throw new IllegalStateException("ASR audio file does not exist");

        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                String text = execute(file);
                if (text == null || text.isBlank()) throw new IllegalStateException("ASR 返回空文本");
                return text.trim();
            } catch (IOException e) {
                lastError = e;
                log.warn("asr_attempt_failed attempt={} file={}", attempt + 1, file.getName(), e);
                if (attempt < MAX_ATTEMPTS - 1) waitBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("ASR 调用失败，已达到最大重试次数", lastError);
    }

    private String execute(File file) throws IOException {
        RequestBody requestBody = protocol.equals("chat-completions")
                ? chatAudioBody(file)
                : new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("application/octet-stream")))
                .addFormDataPart("model", model)
                .build();
        Request request = new Request.Builder()
                .url(transcriptionUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (response.isSuccessful()) {
                JSONObject json = JSON.parseObject(body);
                if (protocol.equals("chat-completions")) {
                    if (json.getJSONArray("choices") == null || json.getJSONArray("choices").isEmpty()) {
                        throw new IOException("ASR response has no choices");
                    }
                    JSONObject message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
                    if (message == null) throw new IOException("ASR response has no message");
                    return message.getString("content");
                }
                return json.getString("text");
            }
            if (response.code() == 429 || response.code() >= 500) {
                throw new RetryableAsrException("ASR transient HTTP " + response.code());
            }
            // 非 429/5xx 的失败源于请求本身（参数、鉴权、音频格式不支持），重投多少次都不会变好。
            // 用 IllegalArgumentException 让上层能识别为「不可重试」，避免整条流水线空转重试。
            throw new IllegalArgumentException("ASR request rejected with HTTP " + response.code());
        }
    }

    private RequestBody chatAudioBody(File file) throws IOException {
        // 百炼短音频以 Data URL 放进 chat/completions，不使用 multipart 转写接口。
        if (((file.length() + 2) / 3) * 4 > 10_000_000) {
            throw new IllegalArgumentException("ASR base64 audio exceeds 10MB");
        }
        String extension = file.getName().toLowerCase(Locale.ROOT);
        String mime;
        if (extension.endsWith(".mp3")) mime = "audio/mpeg";
        else if (extension.endsWith(".wav")) mime = "audio/wav";
        else throw new IllegalArgumentException("Chat ASR supports WAV or MP3 files");
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
        JSONObject audio = new JSONObject();
        audio.put("data", dataUrl);
        JSONObject part = new JSONObject();
        part.put("type", "input_audio");
        part.put("input_audio", audio);
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", java.util.List.of(part));
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("messages", java.util.List.of(message));
        payload.put("stream", false);
        return RequestBody.create(payload.toJSONString(), MediaType.parse("application/json; charset=utf-8"));
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(1_000L << attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ASR retry interrupted", e);
        }
    }

    private static class RetryableAsrException extends IOException {
        private RetryableAsrException(String message) {
            super(message);
        }
    }
}
