package com.sscm.student.entity;

import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StudentRecord 엔티티 단위 테스트")
class StudentRecordEntityTest {

    private StudentRecord buildRecord() {
        User user = User.builder().id(1L).name("이학생").build();
        Student student = Student.builder().id(1L).user(user).admissionYear(2024).build();

        return StudentRecord.builder()
                .id(1L)
                .student(student)
                .year(2024)
                .semester(1)
                .category(RecordCategory.BASIC)
                .content(Map.of("note", "우수"))
                .createdBy(10L)
                .updatedBy(10L)
                .build();
    }

    @Test
    @DisplayName("updateVisibility — 가시성 변경")
    void updateVisibility() {
        StudentRecord record = buildRecord();
        assertThat(record.getIsVisibleToStudent()).isFalse();
        assertThat(record.getIsVisibleToParent()).isFalse();

        record.updateVisibility(true, true);

        assertThat(record.getIsVisibleToStudent()).isTrue();
        assertThat(record.getIsVisibleToParent()).isTrue();
        assertThat(record.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateReviewStatus — 검토 상태 변경")
    void updateReviewStatus() {
        StudentRecord record = buildRecord();
        assertThat(record.getReviewStatus()).isEqualTo(ReviewStatus.DRAFT);

        record.updateReviewStatus(ReviewStatus.APPROVED);

        assertThat(record.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(record.getUpdatedAt()).isNotNull();
    }
}
