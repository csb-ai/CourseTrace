package com.coursetrace.backend.utils;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AliyunAsrUtilsTest {
    @TempDir Path directory;

    private HttpServer server(int status, String response, AtomicReference<String> body, AtomicInteger calls) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/asr", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test void serializesChatAudioAndReadsTranscript() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(200, "{\"choices\":[{\"message\":{\"content\":\"测试转写\"}}]}", body, new AtomicInteger());
        try {
            Path audio = Files.write(directory.resolve("audio.wav"), new byte[]{1, 2, 3});
            AliyunAsrUtils asr = new AliyunAsrUtils("unused", "http://127.0.0.1:" + server.getAddress().getPort() + "/asr", "qwen3-asr-flash", "chat-completions");
            assertEquals("测试转写", asr.audioToText(audio.toString()));
            var request = JSON.parseObject(body.get());
            assertEquals("qwen3-asr-flash", request.getString("model"));
            assertFalse(request.getBooleanValue("stream"));
            var part = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0);
            assertEquals("input_audio", part.getString("type"));
            assertEquals("data:audio/wav;base64,AQID", part.getJSONObject("input_audio").getString("data"));
        } finally { server.stop(0); }
    }

    @Test void keepsOriginalMultipartProtocol() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(200, "{\"text\":\"legacy transcript\"}", body, new AtomicInteger());
        try {
            Path audio = Files.write(directory.resolve("audio.mp3"), new byte[]{1, 2, 3});
            AliyunAsrUtils asr = new AliyunAsrUtils("unused", "http://127.0.0.1:" + server.getAddress().getPort() + "/asr", "legacy-asr");
            assertEquals("legacy transcript", asr.audioToText(audio.toString()));
            assertTrue(body.get().contains("filename=\"audio.mp3\""));
            assertTrue(body.get().contains("legacy-asr"));
        } finally { server.stop(0); }
    }

    @Test void rejectsAuthenticationFailureWithoutRetrying() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(401, "{}", new AtomicReference<>(), calls);
        try {
            Path audio = Files.write(directory.resolve("audio.wav"), new byte[]{1, 2, 3});
            AliyunAsrUtils asr = new AliyunAsrUtils("unused", "http://127.0.0.1:" + server.getAddress().getPort() + "/asr", "qwen3-asr-flash", "chat-completions");
            assertThrows(IllegalArgumentException.class, () -> asr.audioToText(audio.toString()));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }
}
