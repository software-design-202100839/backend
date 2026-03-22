package com.sscm.feedback.dto;

import com.sscm.feedback.entity.Feedback;
import com.sscm.feedback.entity.FeedbackCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FeedbackResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private Long teacherId;
    private String teacherName;
    private FeedbackCategory category;
    private String content;
    private Boolean isVisibleToStudent;
    private Boolean isVisibleToParent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FeedbackResponse from(Feedback feedback) {
        return FeedbackResponse.builder()
                .id(feedback.getId())
                .studentId(feedback.getStudent().getId())
                .studentName(feedback.getStudent().getUser().getName())
                .teacherId(feedback.getTeacher().getId())
                .teacherName(feedback.getTeacher().getUser().getName())
                .category(feedback.getCategory())
                .content(feedback.getContent())
                .isVisibleToStudent(feedback.getIsVisibleToStudent())
                .isVisibleToParent(feedback.getIsVisibleToParent())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }
}
