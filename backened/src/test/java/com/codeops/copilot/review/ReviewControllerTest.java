package com.codeops.copilot.review;

import com.codeops.copilot.review.git.GitChangedFile;
import com.codeops.copilot.review.git.GitFileStatus;
import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.git.GitScope;
import com.codeops.copilot.review.git.RepositorySnapshot;
import com.codeops.copilot.review.persistence.ReviewHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private ReviewHistoryService historyService;

    @MockBean
    private GitRepositoryService repositoryService;

    @Test
    void savesCompletedReviewWithoutStartingAnAsyncTask() throws Exception {
        UUID id = UUID.randomUUID();
        ReviewHistoryService.ReviewHistoryView view = view(id);
        when(historyService.save(any(ReviewHistoryService.SaveReviewCommand.class))).thenReturn(view);

        String body = objectMapper.writeValueAsString(new ReviewController.SaveReviewRequest(
                UUID.randomUUID(), "C:/repo", null, "Review changes", "GIT", "WORKTREE", null,
                "main", "a".repeat(40), "gpt-4o-mini", List.of(
                new ReviewController.ReviewFileRequest("src/App.java", "MODIFIED", 2, 1, "@@", "hash")
        ), List.of()));

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.files[0].path").value("src/App.java"));

        verify(historyService).save(any(ReviewHistoryService.SaveReviewCommand.class));
    }

    @Test
    void listsReviewSummaries() throws Exception {
        UUID id = UUID.randomUUID();
        when(historyService.list(20, 0)).thenReturn(List.of(new ReviewHistoryService.ReviewHistorySummary(
                id, "Review changes", "C:/repo", "GIT", "COMPLETED", 85, 1,
                LocalDateTime.now(), LocalDateTime.now())));

        mockMvc.perform(get("/api/reviews").param("limit", "20").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].findingCount").value(1));
    }

    @Test
    void getsAndDeletesReviewDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(historyService.get(id)).thenReturn(view(id));

        mockMvc.perform(get("/api/reviews/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository").value("C:/repo"));

        mockMvc.perform(delete("/api/reviews/{id}", id))
                .andExpect(status().isNoContent());

        verify(historyService).delete(id);
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
                .andExpect(jsonPath("$.files[0].path").value("src/App.java"));
    }

    private ReviewHistoryService.ReviewHistoryView view(UUID id) {
        return new ReviewHistoryService.ReviewHistoryView(
                id, "C:/repo", "Review changes", "GIT", "WORKTREE", null, "main", "a".repeat(40),
                "COMPLETED", "gpt-4o-mini", 85, 1, null, LocalDateTime.now(), LocalDateTime.now(),
                List.of(new ReviewHistoryService.ReviewFileView("src/App.java", "MODIFIED", 2, 1, "@@", "hash")),
                List.of(new ReviewFinding("SECURITY", Severity.HIGH, "src/App.java", 2,
                        "问题", "建议", "证据", 0.95)));
    }
}
