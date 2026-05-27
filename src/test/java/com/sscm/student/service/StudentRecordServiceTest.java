package com.sscm.student.service;

import com.sscm.auth.entity.Role;
import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.auth.repository.UserRepository;
import com.sscm.common.entity.School;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.common.tenant.TenantContext;
import com.sscm.student.dto.StudentRecordRequest;
import com.sscm.student.dto.StudentRecordResponse;
import com.sscm.student.dto.StudentRecordUpdateRequest;
import com.sscm.student.entity.RecordCategory;
import com.sscm.student.entity.StudentRecord;
import com.sscm.student.repository.StudentRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentRecordService 단위 테스트")
class StudentRecordServiceTest {

    @InjectMocks
    private StudentRecordService studentRecordService;

    @Mock
    private StudentRecordRepository studentRecordRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final School testSchool = School.builder().id(1L).name("테스트학교").code("TEST").build();

    private User teacherUser;
    private User studentUser;
    private Student student;

    @BeforeEach
    void setUp() {
        TenantContext.setSchoolId(1L);

        teacherUser = User.builder()
                .name("이교사").role(Role.TEACHER).school(testSchool).build();
        setIdField(teacherUser, 10L);

        studentUser = User.builder()
                .name("이학생").role(Role.STUDENT).school(testSchool).build();
        setIdField(studentUser, 1L);

        student = Student.builder()
                .user(studentUser).admissionYear(2025).build();
        setIdField(student, 1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private StudentRecord createRecord(Long id, RecordCategory category, Map<String, Object> content) {
        StudentRecord record = StudentRecord.builder()
                .student(student).year(2026).semester(1)
                .category(category).content(content)
                .createdBy(10L).updatedBy(10L).build();
        setIdField(record, id);
        return record;
    }

    private StudentRecordRequest createRecordRequest(Long studentId, RecordCategory category,
                                                      Map<String, Object> content) throws Exception {
        StudentRecordRequest request = new StudentRecordRequest();
        setField(request, "studentId", studentId);
        setField(request, "year", 2026);
        setField(request, "semester", 1);
        setField(request, "category", category);
        setField(request, "content", content);
        return request;
    }

    private StudentRecordUpdateRequest createUpdateRequest(Map<String, Object> content) throws Exception {
        StudentRecordUpdateRequest request = new StudentRecordUpdateRequest();
        setField(request, "content", content);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setIdField(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("학생부 등록")
    class CreateRecord {

        @Test
        @DisplayName("출결 기록을 정상 등록한다")
        void success() throws Exception {
            Map<String, Object> content = Map.of("결석", 2, "지각", 1, "조퇴", 0, "결과", 0);
            StudentRecordRequest request = createRecordRequest(1L, RecordCategory.ATTENDANCE, content);
            StudentRecord saved = createRecord(1L, RecordCategory.ATTENDANCE, content);

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(studentRecordRepository.save(any(StudentRecord.class))).willReturn(saved);

            StudentRecordResponse result = studentRecordService.createRecord(request, 10L);

            assertThat(result.getCategory()).isEqualTo(RecordCategory.ATTENDANCE);
            assertThat(result.getStudentName()).isEqualTo("이학생");
            assertThat(result.getContent()).containsEntry("결석", 2);
            verify(studentRecordRepository).save(any(StudentRecord.class));
        }

        @Test
        @DisplayName("종합의견 학생부를 정상 등록한다")
        void generalOpinionRecord() throws Exception {
            Map<String, Object> content = Map.of("내용", "성실히 학업에 임함");
            StudentRecordRequest request = createRecordRequest(1L, RecordCategory.GENERAL_OPINION, content);
            StudentRecord saved = createRecord(1L, RecordCategory.GENERAL_OPINION, content);

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(studentRecordRepository.save(any(StudentRecord.class))).willReturn(saved);

            StudentRecordResponse result = studentRecordService.createRecord(request, 10L);

            assertThat(result.getCategory()).isEqualTo(RecordCategory.GENERAL_OPINION);
        }

        @Test
        @DisplayName("존재하지 않는 학생이면 STUDENT_NOT_FOUND 예외")
        void studentNotFound() throws Exception {
            StudentRecordRequest request = createRecordRequest(999L, RecordCategory.ATTENDANCE, Map.of("결석", 0));
            given(studentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> studentRecordService.createRecord(request, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("학생부 수정")
    class UpdateRecord {

        @Test
        @DisplayName("내용 수정이 정상 반영된다")
        void success() throws Exception {
            Map<String, Object> originalContent = Map.of("결석", 2);
            Map<String, Object> newContent = Map.of("결석", 3, "지각", 1);
            StudentRecord existing = createRecord(1L, RecordCategory.ATTENDANCE, originalContent);
            StudentRecordUpdateRequest request = createUpdateRequest(newContent);

            given(studentRecordRepository.findById(1L)).willReturn(Optional.of(existing));

            StudentRecordResponse result = studentRecordService.updateRecord(1L, request, 2L);

            assertThat(result.getContent()).containsEntry("결석", 3);
            assertThat(result.getContent()).containsEntry("지각", 1);
        }

        @Test
        @DisplayName("존재하지 않는 레코드면 RESOURCE_NOT_FOUND 예외")
        void notFound() throws Exception {
            StudentRecordUpdateRequest request = createUpdateRequest(Map.of("결석", 0));
            given(studentRecordRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> studentRecordService.updateRecord(999L, request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("학생부 조회")
    class GetRecord {

        @Test
        @DisplayName("단건 조회 성공")
        void success() {
            StudentRecord record = createRecord(1L, RecordCategory.AWARD, Map.of("상명", "교내 과학탐구대회 은상"));
            given(studentRecordRepository.findById(1L)).willReturn(Optional.of(record));

            StudentRecordResponse result = studentRecordService.getRecord(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getCategory()).isEqualTo(RecordCategory.AWARD);
        }

        @Test
        @DisplayName("존재하지 않는 레코드면 RESOURCE_NOT_FOUND 예외")
        void notFound() {
            given(studentRecordRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> studentRecordService.getRecord(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("학생별 학기 학생부 조회")
    class GetStudentRecords {

        @Test
        @DisplayName("교사가 카테고리 필터 없이 전체 조회")
        void withoutCategory() {
            StudentRecord r1 = createRecord(1L, RecordCategory.ATTENDANCE, Map.of("결석", 1));
            StudentRecord r2 = createRecord(2L, RecordCategory.GENERAL_OPINION, Map.of("내용", "우수"));

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(userRepository.findById(10L)).willReturn(Optional.of(teacherUser));
            given(studentRecordRepository.findByStudentIdAndYearAndSemester(1L, 2026, 1))
                    .willReturn(List.of(r1, r2));

            List<StudentRecordResponse> result = studentRecordService.getStudentRecords(1L, 2026, 1, null, 10L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("교사가 카테고리 필터로 ATTENDANCE만 조회")
        void withCategory() {
            StudentRecord r1 = createRecord(1L, RecordCategory.ATTENDANCE, Map.of("결석", 1));

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(userRepository.findById(10L)).willReturn(Optional.of(teacherUser));
            given(studentRecordRepository.findByStudentIdAndYearAndSemesterAndCategory(
                    1L, 2026, 1, RecordCategory.ATTENDANCE))
                    .willReturn(List.of(r1));

            List<StudentRecordResponse> result = studentRecordService.getStudentRecords(
                    1L, 2026, 1, RecordCategory.ATTENDANCE, 10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo(RecordCategory.ATTENDANCE);
        }

        @Test
        @DisplayName("존재하지 않는 학생이면 STUDENT_NOT_FOUND 예외")
        void studentNotFound() {
            given(studentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> studentRecordService.getStudentRecords(999L, 2026, 1, null, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        }

        @Test
        @DisplayName("학생부 기록이 없으면 빈 리스트 반환")
        void emptyRecords() {
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(userRepository.findById(10L)).willReturn(Optional.of(teacherUser));
            given(studentRecordRepository.findByStudentIdAndYearAndSemester(1L, 2026, 1))
                    .willReturn(Collections.emptyList());

            List<StudentRecordResponse> result = studentRecordService.getStudentRecords(1L, 2026, 1, null, 10L);

            assertThat(result).isEmpty();
        }
    }
}
