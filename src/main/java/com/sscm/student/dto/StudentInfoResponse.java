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
    private Integer admissionYear;

    public static StudentInfoResponse from(Student student) {
        return StudentInfoResponse.builder()
                .id(student.getId())
                .name(student.getUser().getName())
                .email(student.getUser().getEmail())
                .phone(student.getUser().getPhone())
                .admissionYear(student.getAdmissionYear())
                .build();
    }
}
