package com.sscm.admin.dto;

import com.sscm.common.entity.ClassRoom;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClassSummary {
    private Long id;
    private int academicYear;
    private int grade;
    private int classNum;
    private HomeroomTeacherInfo homeroomTeacher;
    private int studentCount;

    @Getter
    @Builder
    public static class HomeroomTeacherInfo {
        private Long id;
        private String name;
        private String department;
    }

    public static ClassSummary from(ClassRoom classRoom, int studentCount) {
        HomeroomTeacherInfo teacherInfo = null;
        if (classRoom.getHomeroomTeacher() != null) {
            teacherInfo = HomeroomTeacherInfo.builder()
                    .id(classRoom.getHomeroomTeacher().getId())
                    .name(classRoom.getHomeroomTeacher().getUser().getName())
                    .department(classRoom.getHomeroomTeacher().getDepartment())
                    .build();
        }

        return ClassSummary.builder()
                .id(classRoom.getId())
                .academicYear(classRoom.getAcademicYear())
                .grade(classRoom.getGrade())
                .classNum(classRoom.getClassNum())
                .homeroomTeacher(teacherInfo)
                .studentCount(studentCount)
                .build();
    }
}
