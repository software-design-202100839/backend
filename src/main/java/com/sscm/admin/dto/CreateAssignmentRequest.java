package com.sscm.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CreateAssignmentRequest {
    @NotNull private Long teacherId;
    @NotNull private Long classId;
    @NotNull private Long subjectId;
    @NotNull private Integer academicYear;
}
