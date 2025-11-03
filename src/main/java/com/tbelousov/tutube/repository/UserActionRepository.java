package com.tbelousov.tutube.repository;

import com.tbelousov.tutube.entity.UserAction;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Repository
public class UserActionRepository implements ClearableRepository {

    private final Map<Long, UserAction> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public UserAction save(UserAction action) {
        var id = action.getId();
        if (id == null) id = idGenerator.getAndIncrement();

        final var finalId = id;
        return storage.compute(finalId, (k, v) ->
                UserAction.builder()
                        .id(finalId)
                        .userId(action.getUserId())
                        .actionType(action.getActionType())
                        .timestamp(action.getTimestamp())
                        .deviceType(action.getDeviceType())
                        .location(action.getLocation())
                        .weather(action.getWeather())
                        .videoId(action.getVideoId())
                        .channelId(action.getChannelId())
                        .commentId(action.getCommentId())
                        .donationAmount(action.getDonationAmount())
                        .videoProgress(action.getVideoProgress())
                        .videoTopic(action.getVideoTopic())
                        .seeksCount(action.getSeeksCount())
                        .watchDurationSec(action.getWatchDurationSec())
                        .introSkipSec(action.getIntroSkipSec())
                        .typosCount(action.getTyposCount())
                        .sentimentScore(action.getSentimentScore())
                        .build()
        );
    }

    public Optional<UserAction> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public Stream<UserAction> streamAll() {
        return storage.values().stream();
    }

    public List<UserAction> findByUserId(Long userId) {
        return storage.values().stream()
                .filter(a -> a.getUserId().equals(userId))
                .toList();
    }

    public List<UserAction> findByUserIdAndTimestampAfter(Long userId, Instant after) {
        return storage.values().stream()
                .filter(a -> a.getUserId().equals(userId))
                .filter(a -> a.getTimestamp().isAfter(after))
                .toList();
    }

    public List<UserAction> findOtherUsersCommentsOnVideo(Long videoId, Long currentUser, Instant since) {
        return storage.values().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.COMMENT)
                .filter(a -> a.getVideoId() != null && a.getVideoId().equals(videoId))
                .filter(a -> !a.getUserId().equals(currentUser))
                .filter(a -> a.getTimestamp().isAfter(since))
                .toList();
    }

    public boolean isUserSubscribedToChannel(Long userId, Long channelId) {
        return storage.values().stream()
                .anyMatch(a -> a.getUserId().equals(userId)
                        && a.getActionType() == UserAction.ActionType.SUBSCRIBE
                        && a.getChannelId() != null
                        && a.getChannelId().equals(channelId));
    }

    public long countCommentLikesSince(Long commentId, Instant since) {
        return storage.values().stream()
                .filter(a -> a.getActionType() == UserAction.ActionType.LIKE_COMMENT)
                .filter(a -> a.getCommentId() != null && a.getCommentId().equals(commentId))
                .filter(a -> a.getTimestamp().isAfter(since))
                .count();
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