package com.coursetrace.backend.controller;

import com.coursetrace.backend.entity.User;
import com.coursetrace.backend.mapper.UserMapper;
import com.coursetrace.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalSessionControllerTest {
    @Test
    void localSessionUsesConfiguredOwnerAndRejectsRemoteOrForeignOrigin() {
        var users = mock(UserMapper.class);
        var auth = mock(AuthService.class);
        var user = new User();
        user.setId(7L);
        when(users.selectById(7L)).thenReturn(user);
        when(auth.createSession(7L)).thenReturn("test-session");
        var controller = new LocalSessionController(users, auth, 7L);
        var local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        local.addHeader("Origin", "http://127.0.0.1:5178");
        assertNotNull(controller.open(local));
        var foreign = new MockHttpServletRequest();
        foreign.setRemoteAddr("127.0.0.1");
        foreign.addHeader("Origin", "https://external.example");
        assertThrows(SecurityException.class, () -> controller.open(foreign));
        var remote = new MockHttpServletRequest();
        remote.setRemoteAddr("192.168.1.8");
        assertThrows(SecurityException.class, () -> controller.open(remote));
        verify(auth, times(1)).createSession(7L);
    }
}
