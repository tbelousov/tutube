package com.tbelousov.tutube.repository;

import com.tbelousov.tutube.entity.Notification;
import org.hibernate.query.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserId(Long userId);
    List<Notification> findBySentFalse();
    List<Notification> findByUserIdAndCreatedAtAfter(Long userId, Instant after);

    @Query("SELECT max(n.sendAt) FROM Notification n " +
            "WHERE n.userId = :userId AND n.sent = true")
    Instant findLastDeliveryTime(Long userId);

    @Query("SELECT count(n) FROM Notification n " +
            "WHERE n.userId = :userId AND n.sent = true AND n.createdAt >= :since")
    long countSentSince(Long userId, Instant since);

    @Query("SELECT count(n) FROM Notification n " +
            "WHERE n.userId = :userId AND n.sent = false")
    int countPendingByUser(Long userId);

    @Query("SELECT count(n) > 0 FROM Notification n " +
            "WHERE n.userId = :userId AND n.triggerType = :triggerType AND n.createdAt >= :since")
    boolean existsTriggerSince(Long userId, String triggerType, Instant since);

    @Modifying
    @Query("UPDATE Notification n " +
           "SET n.sent = true " +
           "WHERE n.id = :id AND n.sent = false AND n.sendAt <= :now")
    int markSentIfDue(Long id, Instant now);
}