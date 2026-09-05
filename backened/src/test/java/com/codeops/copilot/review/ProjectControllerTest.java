package com.codeops.copilot.review;

import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.persistence.ProjectService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import(ProjectController.class)
class ProjectControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private GitRepositoryService gitRepositoryService;

    @Test
    void importsProject() throws Exception {
        when(projectService.importProject("D:/repo")).thenReturn(view());

        mockMvc.perform(post("/api/projects/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"D:/repo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryPath").value("D:/repo"))
                .andExpect(jsonPath("$.policy.prePushEnabled").value(true));
    }

    @Test
    void registersCentralProjectFromARemoteUrl() throws Exception {
        when(projectService.registerRemoteProject("Order Service", "git@git.example.com:acme/order-service.git"))
                .thenReturn(new ProjectService.CentralProjectView(3L, "Order Service",
                        "git@git.example.com:acme/order-service.git", "git.example.com/acme/order-service",
                        "GIT", view().policy()));

        mockMvc.perform(post("/api/projects/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Order Service\",\"remoteUrl\":\"git@git.example.com:acme/order-service.git\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryKey").value("git.example.com/acme/order-service"));
    }

    @Test
    void updatesPolicy() throws Exception {
        when(projectService.updatePolicy(any(Long.class), any(ProjectService.PolicyCommand.class))).thenReturn(view());

        mockMvc.perform(put("/api/projects/3/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"preCommitEnabled\":true,\"prePushEnabled\":false,\"postMergeEnabled\":true,\"failOnSeverity\":\"MEDIUM\",\"failOpen\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.preCommitEnabled").value(false));
    }

    private ProjectService.ProjectView view() {
        return new ProjectService.ProjectView(3L, "repo", "D:/repo", "main", "abc",
                new ProjectService.PolicyView(true, false, true, false, "HIGH", false, LocalDateTime.now()));
    }
}
