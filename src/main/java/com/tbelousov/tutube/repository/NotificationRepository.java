package com.tbelousov.tutube.repository;

import com.tbelousov.tutube.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Repository
@Slf4j
public class NotificationRepository implements ClearableRepository {

    private final Map<Long, Notification> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Notification save(Notification notification) {
        var id = notification.getId();
        if (id == null) id = idGenerator.getAndIncrement();

        final var finalId = id;
        return storage.compute(finalId, (k, v) ->
                Notification.builder()
                        .id(finalId)
                        .userId(notification.getUserId())
                        .message(notification.getMessage())
                        .createdAt(notification.getCreatedAt())
                        .sendAt(notification.getSendAt())
                        .sent(notification.isSent())
                        .triggerType(notification.getTriggerType())
                        .tone(notification.getTone())
                        .source(notification.getSource())
                        .context(notification.getContext())
                        .build()
        );
    }

    public Optional<Notification> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public Stream<Notification> streamAll() {
        return storage.values().stream();
    }

    public List<Notification> findByUserId(Long userId) {
        return storage.values().stream()
                .filter(n -> n.getUserId().equals(userId))
                .toList();
    }

    public List<Notification> findByUserIdAndCreatedAtAfter(Long userId, Instant after) {
        return storage.values().stream()
                .filter(n -> n.getUserId().equals(userId))
                .filter(n -> n.getCreatedAt().isAfter(after))
                .toList();
    }

    public Instant findLastDeliveryTime(Long userId) {
        return storage.values().stream()
                .filter(n -> n.getUserId().equals(userId))
                .filter(Notification::isSent)
                .map(Notification::getSendAt)
                .max(Instant::compareTo)
                .orElse(null);
    }

    public long countSentSince(Long userId, Instant since) {
        return storage.values().stream()
                .filter(n -> n.getUserId().equals(userId))
                .filter(Notification::isSent)
                .filter(n -> n.getCreatedAt().isAfter(since) || n.getCreatedAt().equals(since))
                .count();
    }

    public int countPendingByUser(Long userId) {
        return (int) storage.values().stream()
                .filter(n -> n.getUserId().equals(userId))
                .filter(n -> !n.isSent())
                .count();
    }

    public boolean existsTriggerSince(Long userId, String triggerType, Instant since) {
        return storage.values().stream()
                .filter(n -> n.getUserId().equals(userId))
                .filter(n -> triggerType.equals(n.getTriggerType()))
                .anyMatch(n -> n.getCreatedAt().isAfter(since) || n.getCreatedAt().equals(since));
    }

    /**
     * Аналог @Modifying query - обновляет запись если условия выполнены
     * Возвращает 1 если обновлено, 0 - если нет
     */
    public int markSentIfDue(Long id, Instant now) {
        var result = new AtomicInteger(0);
        storage.compute(id, (k, n) -> {
            if (n == null || n.isSent()) return n; // уже отправлено
            if (n.getSendAt() != null && n.getSendAt().isAfter(now)) return n; // ещё не время

            result.set(1);
            return Notification.builder()
                    .id(n.getId())
                    .userId(n.getUserId())
                    .message(n.getMessage())
                    .createdAt(n.getCreatedAt())
                    .sendAt(n.getSendAt() != null ? n.getSendAt() : now)
                    .sent(true) // <= тутъ новое значение
                    .triggerType(n.getTriggerType())
                    .tone(n.getTone())
                    .source(n.getSource())
                    .context(n.getContext())
                    .build();
        });
        return result.get();
    }

    public void deleteById(Long id) {
        storage.remove(id);
    }

    @Override
    public void clear() {
        storage.clear();
        idGenerator.set(1);
    }

    public long count() {
        return storage.size();
    }
}