package com.codeops.copilot.review;

import com.codeops.copilot.review.git.GitChangedFile;
import com.codeops.copilot.review.git.GitFileStatus;
import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.git.GitScope;
import com.codeops.copilot.review.git.RepositorySnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private GitRepositoryService repositoryService;

    @Test
    void acceptsReviewRequestAndStartsAsyncProcessing() throws Exception {
        UUID taskId = UUID.randomUUID();
        ReviewTask task = new ReviewTask(taskId, "acme/order-service", 42L, "Review payment",
                List.of(new ChangedFile("PaymentService.java", "return true;")), ReviewStatus.PENDING,
                List.of(), java.time.Instant.now(), java.time.Instant.now(), null);
        when(reviewService.submit(any(ReviewRequest.class))).thenReturn(task);

        String body = objectMapper.writeValueAsString(new ReviewController.SubmitReviewRequest(
                "acme/order-service", 42L, "Review payment",
                List.of(new ReviewController.ChangedFileRequest("PaymentService.java", "return true;"))
        ));

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(reviewService).processAsync(eq(taskId));
    }

    @Test
    void scansRepositoryAndReturnsChangedFileMetadata() throws Exception {
        when(repositoryService.scan(any(), eq(GitScope.WORKTREE), isNull()))
                .thenReturn(new RepositorySnapshot("C:/repo", "main", "a".repeat(40), List.of(
                        new GitChangedFile("src/App.java", GitFileStatus.MODIFIED, 2, 1,
                                "@@ -1 +1 @@", false, true, null)
                )));

        mockMvc.perform(post("/api/repositories/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"C:\\\\repo\",\"scope\":\"WORKTREE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.files[0].path").value("src/App.java"))
                .andExpect(jsonPath("$.files[0].additions").value(2));
    }

    @Test
    void submitsOnlySelectedGitFiles() throws Exception {
        UUID taskId = UUID.randomUUID();
        ReviewTask task = new ReviewTask(taskId, "C:/repo", 123L, "Review local changes",
                List.of(new ChangedFile("src/App.java", "+class App {}")), ReviewStatus.PENDING,
                List.of(), java.time.Instant.now(), java.time.Instant.now(), null);
        when(repositoryService.readSelected(any(), eq(GitScope.WORKTREE), isNull(), eq(List.of("src/App.java"))))
                .thenReturn(List.of(new ChangedFile("src/App.java", "+class App {}")));
        when(reviewService.submit(any(ReviewRequest.class))).thenReturn(task);

        mockMvc.perform(post("/api/reviews/from-git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"C:\\\\repo\",\"scope\":\"WORKTREE\",\"title\":\"Review local changes\",\"files\":[\"src/App.java\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(taskId.toString()));

        verify(reviewService).submit(org.mockito.ArgumentMatchers.argThat(request -> request.files().size() == 1
                && request.files().getFirst().path().equals("src/App.java")));
        verify(reviewService).processAsync(taskId);
    }
}
