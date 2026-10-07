package com.coursetrace.backend.controller;

import com.coursetrace.backend.common.Result;
import com.coursetrace.backend.dto.AuthData;
import com.coursetrace.backend.dto.AuthResponse;
import com.coursetrace.backend.entity.User;
import com.coursetrace.backend.mapper.UserMapper;
import com.coursetrace.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本机单人工作台自动建立会话；公共部署默认没有这个入口。 */
@RestController
@ConditionalOnProperty(name = "app.local-session.enabled", havingValue = "true")
public class LocalSessionController {
    private final UserMapper users;
    private final AuthService auth;
    private final Long userId;

    public LocalSessionController(UserMapper users, AuthService auth,
                                  @Value("${app.local-session.user-id:0}") Long userId) {
        this.users = users;
        this.auth = auth;
        this.userId = userId;
    }

    @PostMapping("/user/local-session")
    public Result<AuthData> open(HttpServletRequest request) {
        if (!loopback(request.getRemoteAddr()) || !localOrigin(request.getHeader("Origin"))) {
            throw new SecurityException("本机工作台仅允许本机连接");
        }
        User user = users.selectById(userId);
        if (user == null || userId <= 0) throw new IllegalStateException("本机工作台身份尚未配置");
        AuthResponse.UserInfo view = new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), "本机工作台", user.getAvatar(), user.getRole());
        return Result.ok(new AuthData(view, auth.createSession(user.getId())));
    }

    private boolean loopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }

    private boolean localOrigin(String origin) {
        if (origin == null || origin.isBlank()) return true;
        try {
            java.net.URI uri = java.net.URI.create(origin);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
