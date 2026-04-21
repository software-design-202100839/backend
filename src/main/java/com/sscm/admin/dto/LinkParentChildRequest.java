package com.sscm.admin.dto;

import com.sscm.auth.entity.ParentStudent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class LinkParentChildRequest {
    @NotNull private Long parentId;
    @NotNull private ParentStudent.Relationship relationship;
}
