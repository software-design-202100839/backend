package com.sscm.common.entity;

import com.sscm.auth.entity.Student;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "student_enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StudentEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassRoom classRoom;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "student_num", nullable = false)
    private Integer studentNum;
}
