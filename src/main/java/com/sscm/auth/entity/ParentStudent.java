package com.sscm.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parent_student")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@IdClass(ParentStudentId.class)
public class ParentStudent {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Relationship relationship;

    public enum Relationship {
        FATHER, MOTHER, GUARDIAN
    }
}
