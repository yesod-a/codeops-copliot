package com.codeops.copilot.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ObservabilitySchemaTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesExecutionEventsTableAndIndexes() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'review_execution_events'", Integer.class);
        assertThat(tableCount).isEqualTo(1);

        Integer projectIndexCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.indexes where lower(table_name) = 'review_execution_events' and lower(index_name) = 'idx_execution_events_project_created'",
                Integer.class);
        Integer taskIndexCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.indexes where lower(table_name) = 'review_execution_events' and lower(index_name) = 'idx_execution_events_task_created'",
                Integer.class);
        assertThat(projectIndexCount).isEqualTo(1);
        assertThat(taskIndexCount).isEqualTo(1);
    }
}
