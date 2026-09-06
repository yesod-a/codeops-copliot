package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.persistence.ReviewHistoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskGroupingTest {
    @Test
    void packsFilesIntoBoundedGroupsAndIsolatesOversizedFiles() {
        List<ReviewHistoryService.FileCommand> files = List.of(
                file("one.java", 20_000), file("two.java", 20_000), file("three.java", 20_000),
                file("large.java", 31_000), file("four.java", 1_000));

        List<List<ReviewHistoryService.FileCommand>> groups = ReviewTaskGrouping.group(files);

        assertThat(groups).extracting(List::size).containsExactly(2, 1, 1, 1);
        assertThat(groups.get(2)).extracting(ReviewHistoryService.FileCommand::path).containsExactly("large.java");
    }

    @Test
    void limitsOrdinaryGroupsToSixFiles() {
        List<ReviewHistoryService.FileCommand> files = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> file("file-" + index + ".java", 10)).toList();

        List<List<ReviewHistoryService.FileCommand>> groups = ReviewTaskGrouping.group(files);

        assertThat(groups).extracting(List::size).containsExactly(6, 1);
    }

    private ReviewHistoryService.FileCommand file(String path, int size) {
        return new ReviewHistoryService.FileCommand(path, "M", 1, 0, "x".repeat(size), null);
    }
}
