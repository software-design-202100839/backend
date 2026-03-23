package com.sscm.counsel.dto;

import com.sscm.counsel.entity.CounselCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class CounselingUpdateRequest {

    @NotNull(message = "상담 날짜는 필수입니다")
    private LocalDate counselDate;

    @NotNull(message = "카테고리는 필수입니다")
    private CounselCategory category;

    @NotBlank(message = "상담 내용은 필수입니다")
    private String content;

    private String nextPlan;

    private LocalDate nextCounselDate;

    private Boolean isShared;
}
