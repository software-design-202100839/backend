package com.sscm.analytics.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생부(StudentRecord) 등록 시 Kafka로 전송되는 데이터.
 *
 * Consumer는 이 정보를 보고:
 * - studentId + year + semester로 해당 학생의 카테고리별 기록 건수를 재집계
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordEventPayload {
    private Long recordId;
    private Long studentId;
    private Integer year;
    private Integer semester;
    private String category;    // ATTENDANCE, GENERAL_OPINION, AWARD, VOLUNTEER, SPECIAL_NOTE, OTHER
}
