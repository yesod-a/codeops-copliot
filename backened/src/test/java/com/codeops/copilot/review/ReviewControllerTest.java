package com.codeops.copilot.review;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
}
