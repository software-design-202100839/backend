package com.sscm.analytics.event;

import com.sscm.analytics.config.KafkaConfig;
import com.sscm.analytics.event.payload.CounselingEventPayload;
import com.sscm.analytics.event.payload.FeedbackEventPayload;
import com.sscm.analytics.event.payload.RecordEventPayload;
import com.sscm.analytics.event.payload.ScoreEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsEventBridge 단위 테스트")
class AnalyticsEventBridgeTest {

    @InjectMocks
    private AnalyticsEventBridge eventBridge;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("ScoreChangedEvent 수신 시 scores 토픽으로 전송")
    void onScoreChanged_sendsToScoresTopic() {
        ScoreEventPayload payload = ScoreEventPayload.builder()
                .studentId(5L).subjectId(1L).year(2026).semester(1).build();
        ScoreChangedEvent event = new ScoreChangedEvent("CREATED", payload);

        eventBridge.onScoreChanged(event);

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_SCORES), eq("5"), messageCaptor.capture());

        AnalyticsEvent<?> sent = (AnalyticsEvent<?>) messageCaptor.getValue();
        assertThat(sent.getEventType()).isEqualTo("SCORE_CREATED");
        assertThat(sent.getPayload()).isEqualTo(payload);
    }

    @Test
    @DisplayName("FeedbackChangedEvent 수신 시 feedbacks 토픽으로 전송")
    void onFeedbackChanged_sendsToFeedbacksTopic() {
        FeedbackEventPayload payload = FeedbackEventPayload.builder()
                .studentId(3L).build();
        FeedbackChangedEvent event = new FeedbackChangedEvent("CREATED", payload);

        eventBridge.onFeedbackChanged(event);

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_FEEDBACKS), eq("3"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("RecordChangedEvent 수신 시 records 토픽으로 전송")
    void onRecordChanged_sendsToRecordsTopic() {
        RecordEventPayload payload = RecordEventPayload.builder()
                .studentId(7L).build();
        RecordChangedEvent event = new RecordChangedEvent("CREATED", payload);

        eventBridge.onRecordChanged(event);

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_RECORDS), eq("7"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CounselingChangedEvent 수신 시 counselings 토픽으로 전송")
    void onCounselingChanged_sendsToCounselingsTopic() {
        CounselingEventPayload payload = CounselingEventPayload.builder()
                .studentId(2L).build();
        CounselingChangedEvent event = new CounselingChangedEvent("UPDATED", payload);

        eventBridge.onCounselingChanged(event);

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_COUNSELINGS), eq("2"), messageCaptor.capture());

        AnalyticsEvent<?> sent = (AnalyticsEvent<?>) messageCaptor.getValue();
        assertThat(sent.getEventType()).isEqualTo("COUNSELING_UPDATED");
    }

    @Test
    @DisplayName("Kafka 메시지 Key는 studentId 문자열")
    void kafkaKey_isStudentIdString() {
        ScoreEventPayload payload = ScoreEventPayload.builder()
                .studentId(123L).subjectId(1L).year(2026).semester(1).build();
        ScoreChangedEvent event = new ScoreChangedEvent("UPDATED", payload);

        eventBridge.onScoreChanged(event);

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_SCORES), eq("123"), org.mockito.ArgumentMatchers.any());
    }
}
