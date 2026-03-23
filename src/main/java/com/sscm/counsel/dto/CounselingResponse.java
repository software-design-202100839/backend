package com.sscm.counsel.dto;

import com.sscm.counsel.entity.CounselCategory;
import com.sscm.counsel.entity.Counseling;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class CounselingResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private Long teacherId;
    private String teacherName;
    private LocalDate counselDate;
    private CounselCategory category;
    private String content;
    private String nextPlan;
    private LocalDate nextCounselDate;
    private Boolean isShared;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CounselingResponse from(Counseling counseling) {
        return CounselingResponse.builder()
                .id(counseling.getId())
                .studentId(counseling.getStudent().getId())
                .studentName(counseling.getStudent().getUser().getName())
                .teacherId(counseling.getTeacher().getId())
                .teacherName(counseling.getTeacher().getUser().getName())
                .counselDate(counseling.getCounselDate())
                .category(counseling.getCategory())
                .content(counseling.getContent())
                .nextPlan(counseling.getNextPlan())
                .nextCounselDate(counseling.getNextCounselDate())
                .isShared(counseling.getIsShared())
                .createdAt(counseling.getCreatedAt())
                .updatedAt(counseling.getUpdatedAt())
                .build();
    }
}
