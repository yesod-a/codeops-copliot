package com.codeops.copilot.review.agent;

import com.codeops.copilot.review.persistence.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientProjectController.class)
@Import(ClientProjectController.class)
class ClientProjectControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentAccessService accessService;

    @MockBean
    private ProjectService projectService;

    @Test
    void resolvesAnAuthorizedRemoteRepositoryForTheLocalClient() throws Exception {
        when(accessService.authenticate("agent-token")).thenReturn(new AgentAccessService.Principal(
                "user-1", "developer", AgentRole.USER));
        when(projectService.findCentralProject("git@git.example.com:acme/order-service.git"))
                .thenReturn(Optional.of(new ProjectService.CentralProjectView(3L, "Order Service",
                        "https://git.example.com/acme/order-service.git", "git.example.com/acme/order-service",
                        "GIT", new ProjectService.PolicyView(true, false, true, false, "HIGH", false,
                        LocalDateTime.now()))));
        when(accessService.projectRole("user-1", 3L)).thenReturn(Optional.of(ProjectMemberRole.REVIEWER));

        mockMvc.perform(post("/api/client/projects/resolve")
                        .header("Authorization", "Bearer agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remoteUrl\":\"git@git.example.com:acme/order-service.git\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(3))
                .andExpect(jsonPath("$.repositoryKey").value("git.example.com/acme/order-service"))
                .andExpect(jsonPath("$.role").value("REVIEWER"))
                .andExpect(jsonPath("$.preCommitEnabled").value(false))
                .andExpect(jsonPath("$.prePushEnabled").value(true));
    }

    @Test
    void rejectsAResolutionRequestWithAnInvalidAgentToken() throws Exception {
        mockMvc.perform(post("/api/client/projects/resolve")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remoteUrl\":\"git@git.example.com:acme/order-service.git\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsADisabledResolutionForAnUnregisteredRemoteRepository() throws Exception {
        when(accessService.authenticate("agent-token")).thenReturn(new AgentAccessService.Principal(
                "user-1", "developer", AgentRole.USER));
        when(projectService.findCentralProject("git@git.example.com:acme/unregistered.git"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/client/projects/resolve")
                        .header("Authorization", "Bearer agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remoteUrl\":\"git@git.example.com:acme/unregistered.git\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.reviewEnabled").value(false))
                .andExpect(jsonPath("$.prePushEnabled").value(false));
    }

    @Test
    void rejectsAViewerResolvingARegisteredRepositoryForReview() throws Exception {
        when(accessService.authenticate("agent-token")).thenReturn(new AgentAccessService.Principal(
                "user-1", "developer", AgentRole.USER));
        when(projectService.findCentralProject("git@git.example.com:acme/order-service.git"))
                .thenReturn(Optional.of(centralProject()));
        when(accessService.projectRole("user-1", 3L)).thenReturn(Optional.of(ProjectMemberRole.VIEWER));

        mockMvc.perform(post("/api/client/projects/resolve")
                        .header("Authorization", "Bearer agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remoteUrl\":\"git@git.example.com:acme/order-service.git\"}"))
                .andExpect(status().isForbidden());
    }

    private ProjectService.CentralProjectView centralProject() {
        return new ProjectService.CentralProjectView(3L, "Order Service",
                "https://git.example.com/acme/order-service.git", "git.example.com/acme/order-service",
                "GIT", new ProjectService.PolicyView(true, false, true, false, "HIGH", false,
                LocalDateTime.now()));
    }
}
