package com.sscm.common.service;

import com.sscm.common.entity.AuditLog;
import com.sscm.common.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService 단위 테스트")
class AuditLogServiceTest {

    @InjectMocks
    private AuditLogService auditLogService;

    @Mock
    private AuditLogRepository auditLogRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // record
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("record — 감사 로그 저장")
    class Record {

        @Test
        @DisplayName("정상 파라미터로 record 호출 시 AuditLog 저장")
        void savesAuditLog() {
            AuditLog saved = AuditLog.builder()
                    .id(1L)
                    .tableName("counselings")
                    .recordId(10L)
                    .fieldName("content")
                    .oldValue("이전 내용")
                    .newValue("새 내용")
                    .changedBy(1L)
                    .build();
            given(auditLogRepository.save(any(AuditLog.class))).willReturn(saved);

            auditLogService.record("counselings", 10L, "content", "이전 내용", "새 내용", 1L);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getTableName()).isEqualTo("counselings");
            assertThat(captured.getRecordId()).isEqualTo(10L);
            assertThat(captured.getFieldName()).isEqualTo("content");
            assertThat(captured.getOldValue()).isEqualTo("이전 내용");
            assertThat(captured.getNewValue()).isEqualTo("새 내용");
            assertThat(captured.getChangedBy()).isEqualTo(1L);
        }

        @Test
        @DisplayName("oldValue가 null인 경우에도 저장 가능")
        void savesWithNullOldValue() {
            AuditLog saved = AuditLog.builder()
                    .id(2L)
                    .tableName("users")
                    .recordId(5L)
                    .fieldName("email")
                    .oldValue(null)
                    .newValue("new@sscm.dev")
                    .changedBy(2L)
                    .build();
            given(auditLogRepository.save(any(AuditLog.class))).willReturn(saved);

            auditLogService.record("users", 5L, "email", null, "new@sscm.dev", 2L);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertThat(captor.getValue().getOldValue()).isNull();
            assertThat(captor.getValue().getNewValue()).isEqualTo("new@sscm.dev");
        }

        @Test
        @DisplayName("newValue가 null인 경우에도 저장 가능")
        void savesWithNullNewValue() {
            AuditLog saved = AuditLog.builder()
                    .id(3L)
                    .tableName("students")
                    .recordId(7L)
                    .fieldName("phone")
                    .oldValue("010-1234-5678")
                    .newValue(null)
                    .changedBy(3L)
                    .build();
            given(auditLogRepository.save(any(AuditLog.class))).willReturn(saved);

            auditLogService.record("students", 7L, "phone", "010-1234-5678", null, 3L);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertThat(captor.getValue().getOldValue()).isEqualTo("010-1234-5678");
            assertThat(captor.getValue().getNewValue()).isNull();
        }

        @Test
        @DisplayName("changedAt 필드는 자동으로 현재 시각으로 채워짐")
        void changedAtIsAutoSet() {
            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(inv -> inv.getArgument(0));

            auditLogService.record("grades", 1L, "score", "80", "90", 1L);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertThat(captor.getValue().getChangedAt()).isNotNull();
        }

        @Test
        @DisplayName("서로 다른 테이블/레코드/필드 조합으로 독립 저장")
        void savesWithDifferentTableAndField() {
            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(inv -> inv.getArgument(0));

            auditLogService.record("teachers", 3L, "department", "수학과", "과학과", 1L);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertThat(log.getTableName()).isEqualTo("teachers");
            assertThat(log.getRecordId()).isEqualTo(3L);
            assertThat(log.getFieldName()).isEqualTo("department");
            assertThat(log.getChangedBy()).isEqualTo(1L);
        }
    }
}
