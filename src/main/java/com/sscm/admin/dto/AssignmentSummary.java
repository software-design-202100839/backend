package com.sscm.admin.dto;

import com.sscm.common.entity.TeacherAssignment;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssignmentSummary {
    private Long id;
    private TeacherInfo teacher;
    private ClassInfo classInfo;
    private SubjectInfo subject;
    private int academicYear;

    @Getter @Builder
    public static class TeacherInfo {
        private Long id; private String name; private String department;
    }

    @Getter @Builder
    public static class ClassInfo {
        private Long id; private int grade; private int classNum;
    }

    @Getter @Builder
    public static class SubjectInfo {
        private Long id; private String name; private String code;
    }

    public static AssignmentSummary from(TeacherAssignment a) {
        return AssignmentSummary.builder()
                .id(a.getId())
                .teacher(TeacherInfo.builder()
                        .id(a.getTeacher().getId())
                        .name(a.getTeacher().getUser().getName())
                        .department(a.getTeacher().getDepartment())
                        .build())
                .classInfo(ClassInfo.builder()
                        .id(a.getClassRoom().getId())
                        .grade(a.getClassRoom().getGrade())
                        .classNum(a.getClassRoom().getClassNum())
                        .build())
                .subject(SubjectInfo.builder()
                        .id(a.getSubject().getId())
                        .name(a.getSubject().getName())
                        .code(a.getSubject().getCode())
                        .build())
                .academicYear(a.getAcademicYear())
                .build();
    }
}
