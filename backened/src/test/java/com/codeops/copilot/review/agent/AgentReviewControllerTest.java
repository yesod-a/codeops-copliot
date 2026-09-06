package com.codeops.copilot.review.agent;

import com.codeops.copilot.review.persistence.ReviewHistoryService;
import com.codeops.copilot.review.tasks.ReviewTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentReviewController.class)
@Import(AgentReviewController.class)
class AgentReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentAccessService accessService;

    @MockBean
    private ReviewHistoryService historyService;

    @MockBean
    private CentralReviewService centralReviewService;

    @MockBean
    private com.codeops.copilot.review.persistence.ProjectService projectService;

    @MockBean
    private ReviewTaskService taskService;

    @Test
    void rejectsAgentReviewWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/agent/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":3,\"title\":\"Review\",\"files\":[{\"path\":\"src/App.java\"}] }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void savesAgentReviewForAuthorizedProject() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(accessService.authenticate("agent-token")).thenReturn(new AgentAccessService.Principal(
                "user-1", "developer", AgentRole.USER));
        when(accessService.canReview("user-1", 3L)).thenReturn(true);
        when(projectService.get(3L)).thenReturn(new com.codeops.copilot.review.persistence.ProjectService.ProjectView(
                3L, "order-service", null, "master", "head",
                new com.codeops.copilot.review.persistence.ProjectService.PolicyView(
                        true, false, true, false, "HIGH", false, null)));
        when(projectService.repositoryMatches(3L, "git.example.com/acme/order-service")).thenReturn(true);
        when(centralReviewService.review(any(AgentReviewController.AgentReviewRequest.class), eq("HIGH")))
                .thenReturn(new CentralReviewService.ReviewOutcome(List.of(), false, null));
        when(historyService.saveForProject(eq(3L), any(ReviewHistoryService.SaveReviewCommand.class)))
                .thenReturn(new ReviewHistoryService.ReviewHistoryView(
                        reviewId, "order-service", "Review", "GIT", "BASE_COMMIT", null,
                        "master", "head", "COMPLETED", "hook-llm", 100, 0, null,
                        null, null, List.of(), List.of()));

        mockMvc.perform(post("/api/agent/reviews")
                        .header("Authorization", "Bearer agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":3,\"title\":\"Review\",\"repositoryKey\":\"git.example.com/acme/order-service\",\"files\":[{\"path\":\"src/App.java\",\"patch\":\"+class App {}\"}],\"findings\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAnAgentReviewForAnotherRegisteredRepository() throws Exception {
        when(accessService.authenticate("agent-token")).thenReturn(new AgentAccessService.Principal(
                "user-1", "developer", AgentRole.USER));
        when(accessService.canReview("user-1", 3L)).thenReturn(true);
        when(projectService.repositoryMatches(3L, "git.example.com/acme/other-service")).thenReturn(false);

        mockMvc.perform(post("/api/agent/reviews")
                        .header("Authorization", "Bearer agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":3,\"repositoryKey\":\"git.example.com/acme/other-service\",\"title\":\"Review\",\"files\":[{\"path\":\"src/App.java\",\"patch\":\"+class App {}\"}]}"))
                .andExpect(status().isForbidden());
    }
}
