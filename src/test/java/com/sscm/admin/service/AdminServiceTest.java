package com.sscm.admin.service;

import com.sscm.admin.dto.*;
import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.entity.ClassRoom;
import com.sscm.common.entity.StudentEnrollment;
import com.sscm.common.entity.TeacherAssignment;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.common.repository.ClassRoomRepository;
import com.sscm.common.repository.StudentEnrollmentRepository;
import com.sscm.common.repository.TeacherAssignmentRepository;
import com.sscm.grade.entity.Subject;
import com.sscm.grade.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService 단위 테스트")
class AdminServiceTest {

    @InjectMocks
    private AdminService adminService;

    @Mock private UserRepository userRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ParentRepository parentRepository;
    @Mock private ParentStudentRepository parentStudentRepository;
    @Mock private ClassRoomRepository classRoomRepository;
    @Mock private StudentEnrollmentRepository enrollmentRepository;
    @Mock private TeacherAssignmentRepository assignmentRepository;
    @Mock private SubjectRepository subjectRepository;

    private User teacherUser;
    private Teacher teacher;
    private User studentUser;
    private Student student;
    private User parentUser;
    private Parent parent;
    private ClassRoom classRoom;
    private Subject subject;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder().id(1L).name("김교사").phone("01011112222")
                .role(Role.TEACHER).isActive(true).isActivated(false).build();
        teacher = Teacher.builder().id(1L).user(teacherUser).department("수학").build();

        studentUser = User.builder().id(2L).name("이학생").phone("01033334444")
                .role(Role.STUDENT).isActive(true).isActivated(false).build();
        student = Student.builder().id(1L).user(studentUser).admissionYear(2024).build();

        parentUser = User.builder().id(3L).name("박학부모").phone("01055556666")
                .role(Role.PARENT).isActive(true).isActivated(false).build();
        parent = Parent.builder().id(1L).user(parentUser).build();

        classRoom = ClassRoom.builder().id(1L).academicYear(2024).grade(2).classNum(3).build();

        subject = Subject.builder().id(1L).name("수학").code("MATH101").build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 교사 관리
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("교사 관리")
    class TeacherManagement {

        @Test
        @DisplayName("교사 목록 조회")
        void getTeachers() {
            Pageable pageable = PageRequest.of(0, 10);
            given(teacherRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(List.of(teacher)));

            Page<TeacherSummary> result = adminService.getTeachers(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("김교사");
        }

        @Test
        @DisplayName("교사 등록 — 정상")
        void registerTeacher_success() {
            RegisterTeacherRequest req = new RegisterTeacherRequest();
            ReflectionTestUtils.setField(req, "name", "신규교사");
            ReflectionTestUtils.setField(req, "phone", "01099998888");
            ReflectionTestUtils.setField(req, "department", "영어");

            given(userRepository.existsByPhoneHash(anyString())).willReturn(false);
            given(userRepository.save(any(User.class))).willReturn(teacherUser);
            given(teacherRepository.save(any(Teacher.class))).willReturn(teacher);

            TeacherSummary result = adminService.registerTeacher(req);

            assertThat(result).isNotNull();
            verify(userRepository).save(any(User.class));
            verify(teacherRepository).save(any(Teacher.class));
        }

        @Test
        @DisplayName("교사 등록 — 전화번호 중복 → PHONE_DUPLICATE")
        void registerTeacher_phoneDuplicate() {
            RegisterTeacherRequest req = new RegisterTeacherRequest();
            ReflectionTestUtils.setField(req, "name", "신규교사");
            ReflectionTestUtils.setField(req, "phone", "01011112222");
            ReflectionTestUtils.setField(req, "department", "영어");

            given(userRepository.existsByPhoneHash(anyString())).willReturn(true);

            assertThatThrownBy(() -> adminService.registerTeacher(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PHONE_DUPLICATE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 학생 관리
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("학생 관리")
    class StudentManagement {

        @Test
        @DisplayName("학생 목록 조회")
        void getStudents() {
            Pageable pageable = PageRequest.of(0, 10);
            given(studentRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(List.of(student)));
            given(enrollmentRepository.findByStudentAndAcademicYear(any(), any(Integer.class)))
                    .willReturn(Optional.empty());

            Page<StudentSummary> result = adminService.getStudents(pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("학생 등록 — 정상")
        void registerStudent_success() {
            RegisterStudentRequest req = new RegisterStudentRequest();
            ReflectionTestUtils.setField(req, "name", "신규학생");
            ReflectionTestUtils.setField(req, "phone", "01077776666");
            ReflectionTestUtils.setField(req, "admissionYear", 2025);

            given(userRepository.existsByPhoneHash(anyString())).willReturn(false);
            given(userRepository.save(any(User.class))).willReturn(studentUser);
            given(studentRepository.save(any(Student.class))).willReturn(student);

            StudentSummary result = adminService.registerStudent(req);

            assertThat(result).isNotNull();
            verify(studentRepository).save(any(Student.class));
        }

        @Test
        @DisplayName("학생 등록 — 전화번호 중복 → PHONE_DUPLICATE")
        void registerStudent_phoneDuplicate() {
            RegisterStudentRequest req = new RegisterStudentRequest();
            ReflectionTestUtils.setField(req, "name", "신규학생");
            ReflectionTestUtils.setField(req, "phone", "01033334444");
            ReflectionTestUtils.setField(req, "admissionYear", 2025);

            given(userRepository.existsByPhoneHash(anyString())).willReturn(true);

            assertThatThrownBy(() -> adminService.registerStudent(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PHONE_DUPLICATE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 학부모 관리
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("학부모 관리")
    class ParentManagement {

        @Test
        @DisplayName("학부모 목록 조회")
        void getParents() {
            Pageable pageable = PageRequest.of(0, 10);
            given(parentRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(List.of(parent)));
            given(parentStudentRepository.findByParent(parent)).willReturn(List.of());

            Page<ParentSummary> result = adminService.getParents(pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("학부모 등록 — 정상")
        void registerParent_success() {
            RegisterParentRequest req = new RegisterParentRequest();
            ReflectionTestUtils.setField(req, "name", "신규학부모");
            ReflectionTestUtils.setField(req, "phone", "01088887777");

            given(userRepository.existsByPhoneHash(anyString())).willReturn(false);
            given(userRepository.save(any(User.class))).willReturn(parentUser);
            given(parentRepository.save(any(Parent.class))).willReturn(parent);

            ParentSummary result = adminService.registerParent(req);

            assertThat(result).isNotNull();
            verify(parentRepository).save(any(Parent.class));
        }

        @Test
        @DisplayName("학부모 등록 — 전화번호 중복 → PHONE_DUPLICATE")
        void registerParent_phoneDuplicate() {
            RegisterParentRequest req = new RegisterParentRequest();
            ReflectionTestUtils.setField(req, "name", "신규학부모");
            ReflectionTestUtils.setField(req, "phone", "01055556666");

            given(userRepository.existsByPhoneHash(anyString())).willReturn(true);

            assertThatThrownBy(() -> adminService.registerParent(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PHONE_DUPLICATE);
        }

        @Test
        @DisplayName("학부모-자녀 연결 — 정상")
        void linkParentChild_success() {
            LinkParentChildRequest req = new LinkParentChildRequest();
            ReflectionTestUtils.setField(req, "parentId", 1L);
            ReflectionTestUtils.setField(req, "relationship", ParentStudent.Relationship.MOTHER);

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(parentRepository.findById(1L)).willReturn(Optional.of(parent));
            given(parentStudentRepository.existsByParentAndStudent(parent, student)).willReturn(false);

            adminService.linkParentChild(1L, req);

            verify(parentStudentRepository).save(any(ParentStudent.class));
        }

        @Test
        @DisplayName("학부모-자녀 연결 — 학생 없음 → STUDENT_NOT_FOUND")
        void linkParentChild_studentNotFound() {
            LinkParentChildRequest req = new LinkParentChildRequest();
            ReflectionTestUtils.setField(req, "parentId", 1L);
            ReflectionTestUtils.setField(req, "relationship", ParentStudent.Relationship.FATHER);

            given(studentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.linkParentChild(99L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_FOUND);
        }

        @Test
        @DisplayName("학부모-자녀 연결 — 학부모 없음 → PARENT_NOT_FOUND")
        void linkParentChild_parentNotFound() {
            LinkParentChildRequest req = new LinkParentChildRequest();
            ReflectionTestUtils.setField(req, "parentId", 99L);
            ReflectionTestUtils.setField(req, "relationship", ParentStudent.Relationship.FATHER);

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(parentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.linkParentChild(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PARENT_NOT_FOUND);
        }

        @Test
        @DisplayName("학부모-자녀 연결 — 이미 연결됨 → PARENT_CHILD_DUPLICATE")
        void linkParentChild_duplicate() {
            LinkParentChildRequest req = new LinkParentChildRequest();
            ReflectionTestUtils.setField(req, "parentId", 1L);
            ReflectionTestUtils.setField(req, "relationship", ParentStudent.Relationship.MOTHER);

            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(parentRepository.findById(1L)).willReturn(Optional.of(parent));
            given(parentStudentRepository.existsByParentAndStudent(parent, student)).willReturn(true);

            assertThatThrownBy(() -> adminService.linkParentChild(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PARENT_CHILD_DUPLICATE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 반 관리
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("반 관리")
    class ClassManagement {

        @Test
        @DisplayName("반 목록 조회")
        void getClasses() {
            given(classRoomRepository.findByAcademicYearWithTeacher(2024)).willReturn(List.of(classRoom));
            given(enrollmentRepository.findByClassRoom(classRoom)).willReturn(List.of());

            List<ClassSummary> result = adminService.getClasses(2024);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("반 생성 — 정상")
        void createClass_success() {
            CreateClassRequest req = new CreateClassRequest();
            ReflectionTestUtils.setField(req, "academicYear", 2024);
            ReflectionTestUtils.setField(req, "grade", 2);
            ReflectionTestUtils.setField(req, "classNum", 4);

            given(classRoomRepository.existsByAcademicYearAndGradeAndClassNum(2024, 2, 4))
                    .willReturn(false);
            given(classRoomRepository.save(any(ClassRoom.class))).willReturn(classRoom);

            ClassSummary result = adminService.createClass(req);

            assertThat(result).isNotNull();
            verify(classRoomRepository).save(any(ClassRoom.class));
        }

        @Test
        @DisplayName("반 생성 — 중복 → CLASS_DUPLICATE")
        void createClass_duplicate() {
            CreateClassRequest req = new CreateClassRequest();
            ReflectionTestUtils.setField(req, "academicYear", 2024);
            ReflectionTestUtils.setField(req, "grade", 2);
            ReflectionTestUtils.setField(req, "classNum", 3);

            given(classRoomRepository.existsByAcademicYearAndGradeAndClassNum(2024, 2, 3))
                    .willReturn(true);

            assertThatThrownBy(() -> adminService.createClass(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CLASS_DUPLICATE);
        }

        @Test
        @DisplayName("담임 배정 — 정상")
        void assignHomeroom_success() {
            AssignHomeroomRequest req = new AssignHomeroomRequest();
            ReflectionTestUtils.setField(req, "teacherId", 1L);

            given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
            given(teacherRepository.findById(1L)).willReturn(Optional.of(teacher));

            adminService.assignHomeroom(1L, req);
        }

        @Test
        @DisplayName("담임 배정 — 반 없음 → CLASS_NOT_FOUND")
        void assignHomeroom_classNotFound() {
            AssignHomeroomRequest req = new AssignHomeroomRequest();
            ReflectionTestUtils.setField(req, "teacherId", 1L);

            given(classRoomRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.assignHomeroom(99L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CLASS_NOT_FOUND);
        }

        @Test
        @DisplayName("담임 배정 — 교사 없음 → TEACHER_NOT_FOUND")
        void assignHomeroom_teacherNotFound() {
            AssignHomeroomRequest req = new AssignHomeroomRequest();
            ReflectionTestUtils.setField(req, "teacherId", 99L);

            given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
            given(teacherRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.assignHomeroom(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.TEACHER_NOT_FOUND);
        }

        @Test
        @DisplayName("학생 배정 — 정상")
        void enrollStudent_success() {
            EnrollStudentRequest req = new EnrollStudentRequest();
            ReflectionTestUtils.setField(req, "studentId", 1L);
            ReflectionTestUtils.setField(req, "studentNum", 15);

            given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(enrollmentRepository.existsByStudentAndAcademicYear(student, 2024)).willReturn(false);
            given(enrollmentRepository.existsByClassRoomAndStudentNum(classRoom, 15)).willReturn(false);

            adminService.enrollStudent(1L, req);

            verify(enrollmentRepository).save(any(StudentEnrollment.class));
        }

        @Test
        @DisplayName("학생 배정 — 반 없음 → CLASS_NOT_FOUND")
        void enrollStudent_classNotFound() {
            EnrollStudentRequest req = new EnrollStudentRequest();
            ReflectionTestUtils.setField(req, "studentId", 1L);
            ReflectionTestUtils.setField(req, "studentNum", 15);

            given(classRoomRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.enrollStudent(99L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CLASS_NOT_FOUND);
        }

        @Test
        @DisplayName("학생 배정 — 이미 배정됨 → ENROLLMENT_DUPLICATE")
        void enrollStudent_enrollmentDuplicate() {
            EnrollStudentRequest req = new EnrollStudentRequest();
            ReflectionTestUtils.setField(req, "studentId", 1L);
            ReflectionTestUtils.setField(req, "studentNum", 15);

            given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(enrollmentRepository.existsByStudentAndAcademicYear(student, 2024)).willReturn(true);

            assertThatThrownBy(() -> adminService.enrollStudent(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ENROLLMENT_DUPLICATE);
        }

        @Test
        @DisplayName("학생 배정 — 번호 중복 → STUDENT_NUM_DUPLICATE")
        void enrollStudent_studentNumDuplicate() {
            EnrollStudentRequest req = new EnrollStudentRequest();
            ReflectionTestUtils.setField(req, "studentId", 1L);
            ReflectionTestUtils.setField(req, "studentNum", 15);

            given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
            given(studentRepository.findById(1L)).willReturn(Optional.of(student));
            given(enrollmentRepository.existsByStudentAndAcademicYear(student, 2024)).willReturn(false);
            given(enrollmentRepository.existsByClassRoomAndStudentNum(classRoom, 15)).willReturn(true);

            assertThatThrownBy(() -> adminService.enrollStudent(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NUM_DUPLICATE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 과목 배정
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("과목 배정")
    class AssignmentManagement {

        @Test
        @DisplayName("배정 목록 조회")
        void getAssignments() {
            TeacherAssignment assignment = TeacherAssignment.builder()
                    .id(1L).teacher(teacher).classRoom(classRoom).subject(subject).academicYear(2024)
                    .build();
            given(assignmentRepository.findByAcademicYearWithDetails(2024)).willReturn(List.of(assignment));

            List<AssignmentSummary> result = adminService.getAssignments(2024);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("배정 생성 — 정상")
        void createAssignment_success() {
            CreateAssignmentRequest req = new CreateAssignmentRequest();
            ReflectionTestUtils.setField(req, "teacherId", 1L);
            ReflectionTestUtils.setField(req, "classId", 1L);
            ReflectionTestUtils.setField(req, "subjectId", 1L);
            ReflectionTestUtils.setField(req, "academicYear", 2024);

            TeacherAssignment assignment = TeacherAssignment.builder()
                    .id(1L).teacher(teacher).classRoom(classRoom).subject(subject).academicYear(2024)
                    .build();

            given(teacherRepository.findById(1L)).willReturn(Optional.of(teacher));
            given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(assignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2024)).willReturn(false);
            given(assignmentRepository.save(any(TeacherAssignment.class))).willReturn(assignment);

            AssignmentSummary result = adminService.createAssignment(req);

            assertThat(result).isNotNull();
            verify(assignmentRepository).save(any(TeacherAssignment.class));
        }

        @Test
        @DisplayName("배정 생성 — 중복 → ASSIGNMENT_DUPLICATE")
        void createAssignment_duplicate() {
            CreateAssignmentRequest req = new CreateAssignmentRequest();
            ReflectionTestUtils.setField(req, "teacherId", 1L);
            ReflectionTestUtils.setField(req, "classId", 1L);
            ReflectionTestUtils.setField(req, "subjectId", 1L);
            ReflectionTestUtils.setField(req, "academicYear", 2024);

            given(teacherRepository.findById(1L)).willReturn(Optional.of(teacher));
            given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
            given(subjectRepository.findById(1L)).willReturn(Optional.of(subject));
            given(assignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                    teacher, classRoom, subject, 2024)).willReturn(true);

            assertThatThrownBy(() -> adminService.createAssignment(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ASSIGNMENT_DUPLICATE);
        }

        @Test
        @DisplayName("배정 삭제 — 정상")
        void deleteAssignment_success() {
            TeacherAssignment assignment = TeacherAssignment.builder()
                    .id(1L).teacher(teacher).classRoom(classRoom).subject(subject).academicYear(2024)
                    .build();
            given(assignmentRepository.findById(1L)).willReturn(Optional.of(assignment));

            adminService.deleteAssignment(1L);

            verify(assignmentRepository).delete(assignment);
        }

        @Test
        @DisplayName("배정 삭제 — 없음 → ASSIGNMENT_NOT_FOUND")
        void deleteAssignment_notFound() {
            given(assignmentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.deleteAssignment(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ASSIGNMENT_NOT_FOUND);
        }
    }
}
