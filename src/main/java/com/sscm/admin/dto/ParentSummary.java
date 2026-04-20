package com.sscm.admin.dto;

import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.ParentStudent;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ParentSummary {
    private Long id;
    private String name;
    private String phone;
    private boolean isActivated;
    private List<ChildInfo> children;

    @Getter
    @Builder
    public static class ChildInfo {
        private Long studentId;
        private String studentName;
        private String relationship;
    }

    public static ParentSummary from(Parent parent, List<ParentStudent> links) {
        List<ChildInfo> children = links.stream()
                .map(l -> ChildInfo.builder()
                        .studentId(l.getStudent().getId())
                        .studentName(l.getStudent().getUser().getName())
                        .relationship(l.getRelationship().name())
                        .build())
                .toList();

        return ParentSummary.builder()
                .id(parent.getId())
                .name(parent.getUser().getName())
                .phone(parent.getUser().getPhone())
                .isActivated(parent.getUser().getIsActivated())
                .children(children)
                .build();
    }
}
