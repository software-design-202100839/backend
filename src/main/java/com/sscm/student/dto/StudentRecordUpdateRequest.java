package com.sscm.student.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class StudentRecordUpdateRequest {

    @NotNull(message = "내용은 필수입니다")
    private Map<String, Object> content;
}
