package com.sscm.admin.dto;

import com.sscm.auth.entity.Student;
import com.sscm.common.entity.StudentEnrollment;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentSummary {
    private Long id;
    private String name;
    private String phone;
    private Integer admissionYear;
    private boolean isActivated;
    private EnrollmentInfo currentEnrollment;

    @Getter
    @Builder
    public static class EnrollmentInfo {
        private int academicYear;
        private int grade;
        private int classNum;
        private int studentNum;
    }

    public static StudentSummary from(Student student, StudentEnrollment enrollment) {
        EnrollmentInfo enrollmentInfo = enrollment == null ? null : EnrollmentInfo.builder()
                .academicYear(enrollment.getAcademicYear())
                .grade(enrollment.getClassRoom().getGrade())
                .classNum(enrollment.getClassRoom().getClassNum())
                .studentNum(enrollment.getStudentNum())
                .build();

        return StudentSummary.builder()
                .id(student.getId())
                .name(student.getUser().getName())
                .phone(student.getUser().getPhone())
                .admissionYear(student.getAdmissionYear())
                .isActivated(student.getUser().getIsActivated())
                .currentEnrollment(enrollmentInfo)
                .build();
    }
}
