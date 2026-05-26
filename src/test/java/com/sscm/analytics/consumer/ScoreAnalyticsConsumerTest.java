package com.sscm.analytics.consumer;

import com.sscm.analytics.event.AnalyticsEvent;
import com.sscm.analytics.repository.AnalyticsJdbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreAnalyticsConsumer 단위 테스트")
class ScoreAnalyticsConsumerTest {

    @InjectMocks
    private ScoreAnalyticsConsumer consumer;

    @Mock
    private AnalyticsJdbcRepository analyticsRepo;

    private AnalyticsEvent<LinkedHashMap<String, Object>> createScoreEvent(
            Long studentId, Long subjectId, Integer year, Integer semester) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentId", studentId.intValue());
        payload.put("subjectId", subjectId.intValue());
        payload.put("year", year);
        payload.put("semester", semester);

        return new AnalyticsEvent<>("SCORE_CREATED", java.time.LocalDateTime.now(), payload);
    }

    @Test
    @DisplayName("성적 이벤트 수신 시 3개 집계 메서드 호출")
    void consume_callsThreeAggregationMethods() {
        AnalyticsEvent<LinkedHashMap<String, Object>> event = createScoreEvent(5L, 1L, 2026, 1);

        consumer.consume(event);

        verify(analyticsRepo).upsertStudentScoreSummary(5L, 2026, 1);
        verify(analyticsRepo).upsertSubjectStatistics(1L, 2026, 1);
        verify(analyticsRepo).upsertStudentDashboard(5L, 2026, 1);
    }

    @Test
    @DisplayName("예외 발생 시 다른 이벤트 처리에 영향 없음 (로깅만)")
    void consume_exceptionDoesNotPropagate() {
        AnalyticsEvent<LinkedHashMap<String, Object>> event = createScoreEvent(5L, 1L, 2026, 1);
        doThrow(new RuntimeException("DB 연결 실패"))
                .when(analyticsRepo).upsertStudentScoreSummary(anyLong(), anyInt(), anyInt());

        // 예외가 밖으로 전파되지 않아야 함 (Consumer 내부에서 catch)
        consumer.consume(event);

        verify(analyticsRepo).upsertStudentScoreSummary(5L, 2026, 1);
    }
}
