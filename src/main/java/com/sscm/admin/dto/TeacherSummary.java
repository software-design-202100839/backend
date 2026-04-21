package com.sscm.admin.dto;

import com.sscm.auth.entity.Teacher;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeacherSummary {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String department;
    private boolean isActive;
    private boolean isActivated;

    public static TeacherSummary from(Teacher teacher) {
        return TeacherSummary.builder()
                .id(teacher.getId())
                .name(teacher.getUser().getName())
                .phone(teacher.getUser().getPhone())
                .email(teacher.getUser().getEmail())
                .department(teacher.getDepartment())
                .isActive(teacher.getUser().getIsActive())
                .isActivated(teacher.getUser().getIsActivated())
                .build();
    }
}
