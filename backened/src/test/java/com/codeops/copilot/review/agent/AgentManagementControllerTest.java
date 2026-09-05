package com.codeops.copilot.review.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentManagementController.class)
@Import(AgentManagementController.class)
class AgentManagementControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AgentAccessService accessService;

    @Test
    void listsUsersWithoutExposingTokenSecrets() throws Exception {
        when(accessService.listUsers()).thenReturn(List.of(new AgentAccessService.UserSummary(
                "user-1", "alice", "Alice", AgentRole.USER, true, LocalDateTime.of(2026, 9, 5, 10, 0),
                List.of(new AgentAccessService.AgentSummary("agent-1", "alice-laptop", true,
                        LocalDateTime.of(2026, 9, 5, 10, 0), LocalDateTime.of(2026, 9, 5, 10, 1))),
                List.of(new AgentAccessService.ProjectMembershipSummary(3L, "order-service", ProjectMemberRole.REVIEWER)))));

        mockMvc.perform(get("/api/management/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].agents[0].name").value("alice-laptop"))
                .andExpect(jsonPath("$[0].agents[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].agents[0].tokenHash").doesNotExist());
    }

    @Test
    void rejectsUserManagementForNonAdministrators() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(accessService).requireAdmin(any(), any());

        mockMvc.perform(get("/api/management/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesAUserAndRevokesAnAgent() throws Exception {
        when(accessService.requireAdmin(any(), any())).thenReturn(new AgentAccessService.Principal("admin-1", "admin", AgentRole.ADMIN));
        when(accessService.updateUser(eq("user-1"), any(AgentAccessService.UpdateUserCommand.class), eq("admin-1")))
                .thenReturn(new AgentAccessService.UserSummary("user-1", "alice", "Alice Zhang", AgentRole.USER, false,
                        LocalDateTime.of(2026, 9, 5, 10, 0), List.of(), List.of()));
        mockMvc.perform(put("/api/management/users/user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice Zhang\",\"role\":\"USER\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        verify(accessService).updateUser(eq("user-1"), any(AgentAccessService.UpdateUserCommand.class), eq("admin-1"));

        mockMvc.perform(patch("/api/management/users/user-1/agents/agent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNoContent());
        verify(accessService).setAgentActive("user-1", "agent-1", false);
    }

    @Test
    void replacesProjectMembershipRole() throws Exception {
        mockMvc.perform(put("/api/management/projects/3/members/user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isNoContent());

        verify(accessService).grantProjectAccess(3L, "user-1", ProjectMemberRole.OWNER);
    }
}
