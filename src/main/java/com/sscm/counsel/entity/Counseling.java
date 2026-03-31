package com.sscm.counsel.entity;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.Teacher;
import com.sscm.common.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "counselings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Counseling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "counsel_date", nullable = false)
    private LocalDate counselDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CounselCategory category;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "next_plan", columnDefinition = "TEXT")
    private String nextPlan;

    @Column(name = "next_counsel_date")
    private LocalDate nextCounselDate;

    @Column(name = "is_shared", nullable = false)
    @Builder.Default
    private Boolean isShared = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void update(LocalDate counselDate, CounselCategory category, String content,
                       String nextPlan, LocalDate nextCounselDate, Boolean isShared) {
        this.counselDate = counselDate;
        this.category = category;
        this.content = content;
        this.nextPlan = nextPlan;
        this.nextCounselDate = nextCounselDate;
        this.isShared = isShared;
        this.updatedAt = LocalDateTime.now();
    }
}
