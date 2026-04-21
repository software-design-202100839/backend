package com.sscm.notification.service;

import com.sscm.auth.entity.User;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.notification.dto.NotificationResponse;
import com.sscm.notification.dto.UnreadCountResponse;
import com.sscm.notification.entity.Notification;
import com.sscm.notification.entity.NotificationReferenceType;
import com.sscm.notification.entity.NotificationType;
import com.sscm.notification.event.NotificationEvent;
import com.sscm.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private User recipient;
    private Notification notification;

    @BeforeEach
    void setUp() {
        recipient = User.builder().id(1L).name("이학생").build();

        notification = Notification.builder()
                .id(10L)
                .recipient(recipient)
                .type(NotificationType.SCORE_UPDATE)
                .title("성적 업데이트")
                .message("수학 성적이 등록되었습니다")
                .referenceType(NotificationReferenceType.SCORE)
                .referenceId(100L)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMyNotifications / getUnreadNotifications / getUnreadCount
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("알림 조회")
    class ReadNotifications {

        @Test
        @DisplayName("전체 알림 조회")
        void getMyNotifications() {
            given(notificationRepository.findByRecipientIdOrderByReadAndDate(1L))
                    .willReturn(List.of(notification));

            List<NotificationResponse> result = notificationService.getMyNotifications(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("성적 업데이트");
            assertThat(result.get(0).getType()).isEqualTo(NotificationType.SCORE_UPDATE);
        }

        @Test
        @DisplayName("읽지 않은 알림 조회")
        void getUnreadNotifications() {
            given(notificationRepository.findUnreadByRecipientId(1L))
                    .willReturn(List.of(notification));

            List<NotificationResponse> result = notificationService.getUnreadNotifications(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsRead()).isFalse();
        }

        @Test
        @DisplayName("읽지 않은 알림 수 조회")
        void getUnreadCount() {
            given(notificationRepository.countUnreadByRecipientId(1L)).willReturn(3L);

            UnreadCountResponse result = notificationService.getUnreadCount(1L);

            assertThat(result.getCount()).isEqualTo(3L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // markAsRead
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("알림 읽음 처리")
    class MarkAsRead {

        @Test
        @DisplayName("정상 읽음 처리")
        void success() {
            given(notificationRepository.findByIdWithRecipient(10L))
                    .willReturn(Optional.of(notification));

            NotificationResponse result = notificationService.markAsRead(10L, 1L);

            assertThat(result).isNotNull();
            assertThat(notification.getIsRead()).isTrue();
        }

        @Test
        @DisplayName("알림 없음 → NOTIFICATION_NOT_FOUND")
        void notFound() {
            given(notificationRepository.findByIdWithRecipient(99L))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(99L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("본인 알림이 아님 → ACCESS_DENIED")
        void accessDenied() {
            given(notificationRepository.findByIdWithRecipient(10L))
                    .willReturn(Optional.of(notification)); // recipient.id == 1L

            assertThatThrownBy(() -> notificationService.markAsRead(10L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("전체 읽음 처리")
        void markAllAsRead() {
            notificationService.markAllAsRead(1L);

            verify(notificationRepository).markAllAsReadByRecipientId(1L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteNotification
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("알림 삭제")
    class DeleteNotification {

        @Test
        @DisplayName("정상 삭제")
        void success() {
            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

            notificationService.deleteNotification(10L, 1L);

            verify(notificationRepository).delete(notification);
        }

        @Test
        @DisplayName("알림 없음 → NOTIFICATION_NOT_FOUND")
        void notFound() {
            given(notificationRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.deleteNotification(99L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("본인 알림이 아님 → ACCESS_DENIED")
        void accessDenied() {
            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationService.deleteNotification(10L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createNotifications
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("알림 생성 (이벤트)")
    class CreateNotifications {

        @Test
        @DisplayName("정상 알림 생성 및 WebSocket 전송")
        void success() {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientIds(List.of(1L))
                    .type(NotificationType.SCORE_UPDATE)
                    .title("성적 업데이트")
                    .message("수학 성적이 등록되었습니다")
                    .referenceType(NotificationReferenceType.SCORE)
                    .referenceId(100L)
                    .build();

            given(userRepository.findById(1L)).willReturn(Optional.of(recipient));
            given(notificationRepository.save(any(Notification.class))).willReturn(notification);

            notificationService.createNotifications(event);

            verify(notificationRepository).save(any(Notification.class));
            verify(messagingTemplate).convertAndSendToUser(
                    eq("1"),
                    eq("/queue/notifications"),
                    any(NotificationResponse.class)
            );
        }

        @Test
        @DisplayName("수신자를 찾을 수 없으면 건너뜀")
        void recipientNotFound_skips() {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientIds(List.of(99L))
                    .type(NotificationType.SYSTEM)
                    .title("시스템 알림")
                    .message("메시지")
                    .build();

            given(userRepository.findById(99L)).willReturn(Optional.empty());

            notificationService.createNotifications(event);

            verify(notificationRepository, never()).save(any());
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("여러 수신자에게 알림 생성")
        void multipleRecipients() {
            User recipient2 = User.builder().id(2L).name("박학생").build();
            Notification notification2 = Notification.builder()
                    .id(11L).recipient(recipient2)
                    .type(NotificationType.SCORE_UPDATE)
                    .title("성적 업데이트").message("내용")
                    .build();

            NotificationEvent event = NotificationEvent.builder()
                    .recipientIds(List.of(1L, 2L))
                    .type(NotificationType.SCORE_UPDATE)
                    .title("성적 업데이트")
                    .message("내용")
                    .build();

            given(userRepository.findById(1L)).willReturn(Optional.of(recipient));
            given(userRepository.findById(2L)).willReturn(Optional.of(recipient2));
            given(notificationRepository.save(any(Notification.class)))
                    .willReturn(notification, notification2);

            notificationService.createNotifications(event);

            verify(notificationRepository, times(2)).save(any(Notification.class));
        }
    }
}
