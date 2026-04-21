package com.sscm.common.entity;

import com.sscm.auth.entity.Teacher;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ClassRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(nullable = false)
    private Integer grade;

    @Column(name = "class_num", nullable = false)
    private Integer classNum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homeroom_teacher_id")
    private Teacher homeroomTeacher;

    public void assignHomeroom(Teacher teacher) {
        this.homeroomTeacher = teacher;
    }
}
