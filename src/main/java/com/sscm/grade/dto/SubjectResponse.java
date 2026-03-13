package com.sscm.grade.dto;

import com.sscm.grade.entity.Subject;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubjectResponse {

    private Long id;
    private String name;
    private String code;
    private String description;

    public static SubjectResponse from(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .code(subject.getCode())
                .description(subject.getDescription())
                .build();
    }
}
