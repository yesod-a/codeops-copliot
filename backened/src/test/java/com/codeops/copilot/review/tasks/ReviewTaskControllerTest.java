package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.agent.AgentAccessService;
import com.codeops.copilot.review.agent.AgentRole;
import com.codeops.copilot.review.persistence.ReviewHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewTaskController.class)
@Import(ReviewTaskController.class)
class ReviewTaskControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean ReviewTaskService taskService;
    @MockBean AgentAccessService accessService;

    @Test
    void returnsGroupsFilesAndCompletedFindingsForTaskDetails() throws Exception {
        ReviewTaskEntity task = new ReviewTaskEntity("task-1", 7L, "user-1", "github.com/acme/repo",
                "Push review", "pre-push", "main", "head", "base");
        ReviewTaskFileEntity file = new ReviewTaskFileEntity("src/App.java", "M", 2, 1, "+change", null);
        task.addFile(file);
        task.addGroup(new ReviewTaskGroupEntity(1, List.of(file)));
        when(taskService.getWithDetails("task-1")).thenReturn(task);
        when(accessService.authenticate(null)).thenReturn(new AgentAccessService.Principal("user-1", "dev", AgentRole.USER));
        when(accessService.canReview("user-1", 7L)).thenReturn(true);

        mockMvc.perform(get("/api/review-tasks/task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.groups[0].groupNumber").value(1))
                .andExpect(jsonPath("$.groups[0].files[0].path").value("src/App.java"))
                .andExpect(jsonPath("$.groups[0].findings").isArray());
    }
}
