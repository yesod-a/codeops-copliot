package com.codeops.copilot.review.tasks;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** Keeps task-scoped SSE connections in the web process. */
@Service
public class ReviewTaskEventStream {
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private volatile BiConsumer<SseEmitter, SseEmitter.SseEventBuilder> sender = this::send;

    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        subscribers.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> remove(taskId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        try {
            sender.accept(emitter, SseEmitter.event().name("connected").data(Map.of("taskId", taskId)));
        } catch (RuntimeException ignored) {
            remove.run();
            emitter.completeWithError(ignored);
        }
        return emitter;
    }

    public void publish(ReviewTaskEvent event) {
        if (event == null || event.taskId() == null) return;
        List<SseEmitter> targets = subscribers.getOrDefault(event.taskId(), new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : targets) {
            try {
                sender.accept(emitter, SseEmitter.event().name("task.updated").data(event));
            } catch (RuntimeException error) {
                remove(event.taskId(), emitter);
                emitter.completeWithError(error);
            }
        }
    }

    @Scheduled(fixedDelayString = "${codeops.review-events.heartbeat-ms:15000}")
    public void heartbeat() {
        subscribers.forEach((taskId, targets) -> targets.forEach(emitter -> {
            try {
                sender.accept(emitter, SseEmitter.event().comment("keepalive"));
            } catch (RuntimeException error) {
                remove(taskId, emitter);
                emitter.completeWithError(error);
            }
        }));
    }

    int subscriberCount(String taskId) {
        return subscribers.getOrDefault(taskId, new CopyOnWriteArrayList<>()).size();
    }

    void setSenderForTest(BiConsumer<SseEmitter, SseEmitter.SseEventBuilder> sender) {
        this.sender = sender;
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to send task event", error);
        }
    }

    void remove(String taskId, SseEmitter emitter) {
        var values = subscribers.get(taskId);
        if (values == null) return;
        values.remove(emitter);
        if (values.isEmpty()) subscribers.remove(taskId, values);
    }
}
