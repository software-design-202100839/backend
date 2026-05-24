package com.sscm.analytics.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 성적 변경 시 Kafka로 전송되는 데이터.
 *
 * Consumer는 이 정보를 보고:
 * - studentId + year + semester로 해당 학생의 성적 요약을 재집계
 * - subjectId + year + semester로 해당 과목의 통계를 재집계
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreEventPayload {
    private Long scoreId;
    private Long studentId;
    private Long subjectId;
    private Long teacherId;
    private Integer year;
    private Integer semester;
    private BigDecimal score;
    private String gradeLetter;
}
