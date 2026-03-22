package com.sscm.feedback.entity;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeedbackCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_visible_to_student", nullable = false)
    @Builder.Default
    private Boolean isVisibleToStudent = false;

    @Column(name = "is_visible_to_parent", nullable = false)
    @Builder.Default
    private Boolean isVisibleToParent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void update(FeedbackCategory category, String content,
                       Boolean isVisibleToStudent, Boolean isVisibleToParent) {
        this.category = category;
        this.content = content;
        this.isVisibleToStudent = isVisibleToStudent;
        this.isVisibleToParent = isVisibleToParent;
        this.updatedAt = LocalDateTime.now();
    }
}
