package com.sscm.student.dto;

import com.sscm.auth.entity.Student;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentInfoResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private Integer grade;
    private Integer classNum;
    private Integer studentNum;
    private Integer admissionYear;

    public static StudentInfoResponse from(Student student) {
        return StudentInfoResponse.builder()
                .id(student.getId())
                .name(student.getUser().getName())
                .email(student.getUser().getEmail())
                .phone(student.getUser().getPhone())
                .grade(student.getGrade())
                .classNum(student.getClassNum())
                .studentNum(student.getStudentNum())
                .admissionYear(student.getAdmissionYear())
                .build();
    }
}
