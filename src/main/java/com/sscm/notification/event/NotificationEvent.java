package com.sscm.notification.event;

import com.sscm.notification.entity.NotificationReferenceType;
import com.sscm.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 다른 도메인에서 알림 생성이 필요할 때 발행하는 이벤트.
 * NotificationEventListener가 수신하여 알림 저장 + WebSocket 전송.
 */
@Getter
@Builder
public class NotificationEvent {

    private List<Long> recipientIds;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationReferenceType referenceType;
    private Long referenceId;
}
