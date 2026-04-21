package com.sscm.student.entity;

import com.sscm.auth.entity.Student;
import com.sscm.grade.entity.Subject;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "student_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StudentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer semester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RecordCategory category;

    // 세특(SPECIAL) 전용 — BASIC이면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> content;

    @Column(name = "is_visible_to_student", nullable = false)
    @Builder.Default
    private Boolean isVisibleToStudent = false;

    @Column(name = "is_visible_to_parent", nullable = false)
    @Builder.Default
    private Boolean isVisibleToParent = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.DRAFT;

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

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    public void updateContent(Map<String, Object> content, Long updatedBy) {
        this.content = content;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateVisibility(boolean visibleToStudent, boolean visibleToParent) {
        this.isVisibleToStudent = visibleToStudent;
        this.isVisibleToParent = visibleToParent;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateReviewStatus(ReviewStatus status) {
        this.reviewStatus = status;
        this.updatedAt = LocalDateTime.now();
    }
}
