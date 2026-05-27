package com.sscm.analytics.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 상담 등록/수정 시 Kafka로 전송되는 데이터.
 *
 * Consumer는 이 정보를 보고:
 * - studentId + year + semester로 해당 학생의 상담 카테고리별 건수를 재집계
 * - counselDate로 마지막 상담일 갱신
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounselingEventPayload {
    private Long counselingId;
    private Long studentId;
    private Long teacherId;
    private Long schoolId;
    private LocalDate counselDate;
    private String category;    // ACADEMIC, CAREER, BEHAVIOR, PERSONAL, OTHER
}
