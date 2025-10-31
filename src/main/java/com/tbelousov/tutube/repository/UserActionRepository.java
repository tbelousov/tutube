package com.tbelousov.tutube.repository;

import com.tbelousov.tutube.entity.UserAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface UserActionRepository extends JpaRepository<UserAction, Long> {
    List<UserAction> findByUserId(Long userId);

    List<UserAction> findByUserIdAndTimestampAfter(Long userId, Instant after);

    @Query("SELECT a FROM UserAction a " +
            "WHERE a.actionType = 'COMMENT' AND a.videoId = :videoId AND a.userId <> :currentUser AND a.timestamp > :since")
    List<UserAction> findOtherUsersCommentsOnVideo(Long videoId, Long currentUser, Instant since);

    @Query("SELECT COUNT(a) > 0 FROM UserAction a " +
            "WHERE a.userId = :userId AND a.actionType = 'SUBSCRIBE' AND a.channelId = :channelId")
    boolean isUserSubscribedToChannel(Long userId, Long channelId);

    @Query("SELECT COUNT(a) FROM UserAction a " +
            "WHERE a.actionType = 'LIKE_COMMENT' AND a.commentId = :commentId AND a.timestamp > :since")
    long countCommentLikesSince(Long commentId, Instant since);
}