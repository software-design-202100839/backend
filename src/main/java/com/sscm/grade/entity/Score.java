package com.sscm.grade.entity;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scores",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"student_id", "subject_id", "year", "semester"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer semester;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "grade_letter", length = 5)
    private String gradeLetter;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    public void updateScore(BigDecimal newScore, String gradeLetter, Long updatedBy) {
        this.score = newScore;
        this.gradeLetter = gradeLetter;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRank(Integer rank) {
        this.rank = rank;
    }

    public static String calculateGradeLetter(BigDecimal score) {
        double val = score.doubleValue();
        if (val >= 95) return "A+";
        if (val >= 90) return "A";
        if (val >= 85) return "B+";
        if (val >= 80) return "B";
        if (val >= 75) return "C+";
        if (val >= 70) return "C";
        if (val >= 65) return "D+";
        if (val >= 60) return "D";
        return "F";
    }
}
