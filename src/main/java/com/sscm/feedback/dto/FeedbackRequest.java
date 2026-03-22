package com.sscm.feedback.dto;

import com.sscm.feedback.entity.FeedbackCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FeedbackRequest {

    @NotNull(message = "학생 ID는 필수입니다")
    private Long studentId;

    @NotNull(message = "카테고리는 필수입니다")
    private FeedbackCategory category;

    @NotBlank(message = "피드백 내용은 필수입니다")
    private String content;

    private Boolean isVisibleToStudent = false;

    private Boolean isVisibleToParent = false;
}
