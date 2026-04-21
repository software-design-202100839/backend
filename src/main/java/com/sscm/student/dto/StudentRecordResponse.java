package com.sscm.student.dto;

import com.sscm.student.entity.RecordCategory;
import com.sscm.student.entity.StudentRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class StudentRecordResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private Integer year;
    private Integer semester;
    private RecordCategory category;
    private Map<String, Object> content;
    private Boolean isVisibleToStudent;
    private Boolean isVisibleToParent;
    private String reviewStatus;  // DRAFT, REVIEWED, APPROVED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudentRecordResponse from(StudentRecord record) {
        return StudentRecordResponse.builder()
                .id(record.getId())
                .studentId(record.getStudent().getId())
                .studentName(record.getStudent().getUser().getName())
                .year(record.getYear())
                .semester(record.getSemester())
                .category(record.getCategory())
                .content(record.getContent())
                .isVisibleToStudent(record.getIsVisibleToStudent())
                .isVisibleToParent(record.getIsVisibleToParent())
                .reviewStatus(record.getReviewStatus() != null ? record.getReviewStatus().name() : null)
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
