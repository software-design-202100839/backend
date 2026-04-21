package com.sscm.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AssignHomeroomRequest {
    @NotNull private Long teacherId;
}
