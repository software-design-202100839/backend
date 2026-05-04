package com.sscm.student.dto;

import com.sscm.auth.entity.Student;
import com.sscm.common.entity.StudentEnrollment;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudentInfoResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private Integer admissionYear;
    private List<EnrollmentInfo> enrollments;

    @Getter
    @Builder
    public static class EnrollmentInfo {
        private int academicYear;
        private int grade;
        private int classNum;
        private int studentNum;

        public static EnrollmentInfo from(StudentEnrollment e) {
            return EnrollmentInfo.builder()
                    .academicYear(e.getAcademicYear())
                    .grade(e.getClassRoom().getGrade())
                    .classNum(e.getClassRoom().getClassNum())
                    .studentNum(e.getStudentNum())
                    .build();
        }
    }

    public static StudentInfoResponse from(Student student) {
        return from(student, List.of());
    }

    public static StudentInfoResponse from(Student student, List<StudentEnrollment> enrollments) {
        return StudentInfoResponse.builder()
                .id(student.getId())
                .name(student.getUser().getName())
                .email(student.getUser().getEmail())
                .phone(student.getUser().getPhone())
                .admissionYear(student.getAdmissionYear())
                .enrollments(enrollments.stream().map(EnrollmentInfo::from).toList())
                .build();
    }
}
