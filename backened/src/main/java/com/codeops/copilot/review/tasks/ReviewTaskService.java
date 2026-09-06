package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.persistence.ReviewHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewTaskService {
    public static final String QUEUED_EVENT = "REVIEW_TASK_QUEUED";

    private final ReviewTaskRepository taskRepository;
    private final ReviewOutboxEventRepository outboxEventRepository;

    public ReviewTaskService(ReviewTaskRepository taskRepository, ReviewOutboxEventRepository outboxEventRepository) {
        this.taskRepository = taskRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public ReviewTaskEntity create(CreateTaskCommand command) {
        ReviewTaskEntity task = new ReviewTaskEntity(UUID.randomUUID().toString(), command.projectId(),
                command.requestedByUserId(), command.repositoryKey(), command.title(), command.triggerType(),
                command.branch(), command.headCommit(), command.baseRef());
        List<ReviewTaskFileEntity> files = new ArrayList<>();
        for (ReviewHistoryService.FileCommand file : command.files()) {
            ReviewTaskFileEntity snapshot = new ReviewTaskFileEntity(file.path(), file.gitStatus(), file.additions(),
                    file.deletions(), file.patch(), file.contentHash());
            task.addFile(snapshot);
            files.add(snapshot);
        }
        for (List<ReviewHistoryService.FileCommand> group : ReviewTaskGrouping.group(command.files())) {
            List<ReviewTaskFileEntity> groupFiles = group.stream()
                    .map(file -> files.get(command.files().indexOf(file)))
                    .toList();
            task.addGroup(new ReviewTaskGroupEntity(task.getGroups().size() + 1, groupFiles));
        }
        ReviewTaskEntity saved = taskRepository.save(task);
        outboxEventRepository.save(new ReviewOutboxEventEntity(UUID.randomUUID().toString(), saved.getId(), QUEUED_EVENT,
                "{\"taskId\":\"" + saved.getId() + "\"}"));
        return saved;
    }

    @Transactional(readOnly = true)
    public ReviewTaskEntity get(String taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Review task not found"));
    }

    @Transactional(readOnly = true)
    public ReviewTaskEntity getWithDetails(String taskId) {
        ReviewTaskEntity task = taskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Review task not found"));
        task.getGroups().forEach(group -> group.getFiles().size());
        return task;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReviewTaskEntity> pageForProjects(List<Long> projectIds, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return projectIds.isEmpty() ? org.springframework.data.domain.Page.empty(request) : taskRepository.findByProjectIdIn(projectIds, request);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReviewTaskEntity> pageForAll(int page, int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return taskRepository.findAll(request);
    }

    @Transactional
    public ReviewTaskEntity cancel(String taskId) {
        ReviewTaskEntity task = get(taskId);
        task.requestCancellation();
        return task;
    }

    @Transactional
    public ReviewTaskEntity retry(String taskId) {
        ReviewTaskEntity task = get(taskId);
        task.requeue();
        task.getGroups().stream().filter(group -> group.getStatus() != ReviewTaskGroupStatus.COMPLETED).forEach(ReviewTaskGroupEntity::queue);
        outboxEventRepository.save(new ReviewOutboxEventEntity(UUID.randomUUID().toString(), task.getId(), QUEUED_EVENT,
                "{\"taskId\":\"" + task.getId() + "\"}"));
        return task;
    }

    public record CreateTaskCommand(long projectId, String requestedByUserId, String repositoryKey, String title,
                                    String triggerType, String branch, String headCommit, String baseRef,
                                    List<ReviewHistoryService.FileCommand> files) {
        public CreateTaskCommand {
            files = files == null ? List.of() : List.copyOf(files);
        }
    }
}
