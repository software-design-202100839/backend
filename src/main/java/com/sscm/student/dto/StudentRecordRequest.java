package com.sscm.student.dto;

import com.sscm.student.entity.RecordCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class StudentRecordRequest {

    @NotNull(message = "학생 ID는 필수입니다")
    private Long studentId;

    @NotNull(message = "학년도는 필수입니다")
    private Integer year;

    @NotNull(message = "학기는 필수입니다")
    @Min(value = 1, message = "학기는 1 또는 2입니다")
    @Max(value = 2, message = "학기는 1 또는 2입니다")
    private Integer semester;

    @NotNull(message = "카테고리는 필수입니다")
    private RecordCategory category;

    @NotNull(message = "내용은 필수입니다")
    private Map<String, Object> content;
}
