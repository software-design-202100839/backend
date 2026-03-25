package com.sscm.notification.service;

import com.sscm.auth.entity.User;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.notification.dto.NotificationResponse;
import com.sscm.notification.dto.UnreadCountResponse;
import com.sscm.notification.entity.Notification;
import com.sscm.notification.event.NotificationEvent;
import com.sscm.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByReadAndDate(userId)
                .stream().map(NotificationResponse::from).toList();
    }

    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository.findUnreadByRecipientId(userId)
                .stream().map(NotificationResponse::from).toList();
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        long count = notificationRepository.countUnreadByRecipientId(userId);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdWithRecipient(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        notification.markAsRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        notificationRepository.delete(notification);
    }

    @Transactional
    public void createNotifications(NotificationEvent event) {
        for (Long recipientId : event.getRecipientIds()) {
            User recipient = userRepository.findById(recipientId).orElse(null);
            if (recipient == null) {
                log.warn("알림 수신자를 찾을 수 없음: userId={}", recipientId);
                continue;
            }

            Notification notification = Notification.builder()
                    .recipient(recipient)
                    .type(event.getType())
                    .title(event.getTitle())
                    .message(event.getMessage())
                    .referenceType(event.getReferenceType())
                    .referenceId(event.getReferenceId())
                    .build();

            Notification saved = notificationRepository.save(notification);
            NotificationResponse response = NotificationResponse.from(saved);

            // WebSocket 실시간 전송
            messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/notifications",
                    response
            );
            log.debug("알림 전송 완료: recipientId={}, type={}", recipientId, event.getType());
        }
    }
}
