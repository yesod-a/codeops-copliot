package com.codeops.copilot.review.rules;

import com.codeops.copilot.review.git.GitRepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuleController.class)
@Import(RuleController.class)
class RuleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleService ruleService;

    @MockBean
    private GitRepositoryService gitRepositoryService;

    @Test
    void createsGlobalRule() throws Exception {
        when(ruleService.createGlobal(any())).thenReturn(ruleView());

        mockMvc.perform(post("/api/rules/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"事务完整性\",\"category\":\"CORRECTNESS\",\"pathPattern\":\"**/*.java\",\"content\":\"检查事务\",\"priority\":200,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.pathPattern").value("**/*.java"));
    }

    @Test
    void previewsRulesForSelectedProjectPaths() throws Exception {
        when(ruleService.preview(3L, List.of("backend/src/App.java"))).thenReturn(new RuleResolutionService.RulePreview(List.of(
                new RuleResolutionService.EffectiveFileRule("backend/src/App.java", "abc", List.of())
        )));

        mockMvc.perform(post("/api/projects/3/rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paths\":[\"backend/src/App.java\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].path").value("backend/src/App.java"));
    }

    private RuleService.RuleView ruleView() {
        return new RuleService.RuleView(1L, "GLOBAL", null, "事务完整性", "CORRECTNESS", "**/*.java",
                "检查事务", 200, true, 1, null, null);
    }
}
