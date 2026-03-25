package com.sscm.notification.repository;

import com.sscm.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n JOIN FETCH n.recipient " +
            "WHERE n.id = :id")
    Optional<Notification> findByIdWithRecipient(@Param("id") Long id);

    @Query("SELECT n FROM Notification n JOIN FETCH n.recipient " +
            "WHERE n.recipient.id = :recipientId " +
            "ORDER BY n.isRead ASC, n.createdAt DESC")
    List<Notification> findByRecipientIdOrderByReadAndDate(@Param("recipientId") Long recipientId);

    @Query("SELECT n FROM Notification n JOIN FETCH n.recipient " +
            "WHERE n.recipient.id = :recipientId AND n.isRead = false " +
            "ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByRecipientId(@Param("recipientId") Long recipientId);

    @Query("SELECT COUNT(n) FROM Notification n " +
            "WHERE n.recipient.id = :recipientId AND n.isRead = false")
    long countUnreadByRecipientId(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
            "WHERE n.recipient.id = :recipientId AND n.isRead = false")
    int markAllAsReadByRecipientId(@Param("recipientId") Long recipientId);
}
