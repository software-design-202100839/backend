package com.sscm.analytics.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피드백 등록 시 Kafka로 전송되는 데이터.
 *
 * Consumer는 이 정보를 보고:
 * - studentId + year + semester로 해당 학생의 피드백 카테고리별 건수를 재집계
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackEventPayload {
    private Long feedbackId;
    private Long studentId;
    private Long teacherId;
    private Long schoolId;
    private Integer year;
    private Integer semester;
    private String category;    // ACADEMIC, BEHAVIOR, ATTENDANCE, ATTITUDE, GENERAL
}
