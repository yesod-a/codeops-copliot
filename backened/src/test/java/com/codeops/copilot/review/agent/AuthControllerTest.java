package com.codeops.copilot.review.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(AuthController.class)
class AuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AgentAccessService accessService;

    @Test
    void logsInAndReturnsSessionUser() throws Exception {
        UserEntity user = new UserEntity("alice", "Alice", AgentRole.USER, PasswordService.hash("password-1"));
        when(accessService.authenticatePassword("alice", "password-1")).thenReturn(user);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void rejectsInvalidLogin() throws Exception {
        when(accessService.authenticatePassword(anyString(), anyString())).thenReturn(null);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsCurrentSessionUser() throws Exception {
        UserEntity user = new UserEntity("alice", "Alice", AgentRole.USER, PasswordService.hash("password-1"));
        when(accessService.findActiveUser(user.getId())).thenReturn(Optional.of(user));
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Alice"));
    }
}
