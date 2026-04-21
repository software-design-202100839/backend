package com.sscm.admin.service;

import com.sscm.admin.dto.*;
import com.sscm.auth.entity.*;
import com.sscm.auth.repository.*;
import com.sscm.common.crypto.EncryptionUtil;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final ClassRoomRepository classRoomRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final SubjectRepository subjectRepository;

    // ─── 교사 관리 ────────────────────────────────────────────

    public Page<TeacherSummary> getTeachers(Pageable pageable) {
        return teacherRepository.findAll(pageable)
                .map(TeacherSummary::from);
    }

    @Transactional
    public TeacherSummary registerTeacher(RegisterTeacherRequest req) {
        String phoneHash = EncryptionUtil.sha256(req.getPhone());
        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException(ErrorCode.PHONE_DUPLICATE);
        }

        User user = User.builder()
                .name(req.getName())
                .phone(req.getPhone())
                .phoneHash(phoneHash)
                .role(Role.TEACHER)
                .isActive(true)
                .isActivated(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        Teacher teacher = Teacher.builder()
                .user(user)
                .department(req.getDepartment())
                .build();
        teacherRepository.save(teacher);

        return TeacherSummary.from(teacher);
    }

    // ─── 학생 관리 ────────────────────────────────────────────

    public Page<StudentSummary> getStudents(Pageable pageable) {
        return studentRepository.findAll(pageable).map(student -> {
            int currentYear = LocalDateTime.now().getYear();
            StudentEnrollment enrollment = enrollmentRepository
                    .findByStudentAndAcademicYear(student, currentYear).orElse(null);
            return StudentSummary.from(student, enrollment);
        });
    }

    @Transactional
    public StudentSummary registerStudent(RegisterStudentRequest req) {
        String phoneHash = EncryptionUtil.sha256(req.getPhone());
        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException(ErrorCode.PHONE_DUPLICATE);
        }

        User user = User.builder()
                .name(req.getName())
                .phone(req.getPhone())
                .phoneHash(phoneHash)
                .role(Role.STUDENT)
                .isActive(true)
                .isActivated(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        Student student = Student.builder()
                .user(user)
                .admissionYear(req.getAdmissionYear())
                .build();
        studentRepository.save(student);

        return StudentSummary.from(student, null);
    }

    // ─── 학부모 관리 ──────────────────────────────────────────

    public Page<ParentSummary> getParents(Pageable pageable) {
        return parentRepository.findAll(pageable).map(parent -> {
            List<ParentStudent> links = parentStudentRepository.findByParent(parent);
            return ParentSummary.from(parent, links);
        });
    }

    @Transactional
    public ParentSummary registerParent(RegisterParentRequest req) {
        String phoneHash = EncryptionUtil.sha256(req.getPhone());
        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException(ErrorCode.PHONE_DUPLICATE);
        }

        User user = User.builder()
                .name(req.getName())
                .phone(req.getPhone())
                .phoneHash(phoneHash)
                .role(Role.PARENT)
                .isActive(true)
                .isActivated(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        Parent parent = Parent.builder().user(user).build();
        parentRepository.save(parent);

        return ParentSummary.from(parent, List.of());
    }

    @Transactional
    public void linkParentChild(Long studentId, LinkParentChildRequest req) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        Parent parent = parentRepository.findById(req.getParentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARENT_NOT_FOUND));

        if (parentStudentRepository.existsByParentAndStudent(parent, student)) {
            throw new BusinessException(ErrorCode.PARENT_CHILD_DUPLICATE);
        }

        ParentStudent link = ParentStudent.builder()
                .parent(parent)
                .student(student)
                .relationship(req.getRelationship())
                .build();
        parentStudentRepository.save(link);
    }

    // ─── 반 관리 ──────────────────────────────────────────────

    public List<ClassSummary> getClasses(int academicYear) {
        List<ClassRoom> classes = classRoomRepository.findByAcademicYearWithTeacher(academicYear);
        return classes.stream().map(c -> {
            int count = enrollmentRepository.findByClassRoom(c).size();
            return ClassSummary.from(c, count);
        }).toList();
    }

    @Transactional
    public ClassSummary createClass(CreateClassRequest req) {
        if (classRoomRepository.existsByAcademicYearAndGradeAndClassNum(
                req.getAcademicYear(), req.getGrade(), req.getClassNum())) {
            throw new BusinessException(ErrorCode.CLASS_DUPLICATE);
        }

        ClassRoom classRoom = ClassRoom.builder()
                .academicYear(req.getAcademicYear())
                .grade(req.getGrade())
                .classNum(req.getClassNum())
                .build();
        classRoomRepository.save(classRoom);

        return ClassSummary.from(classRoom, 0);
    }

    @Transactional
    public void assignHomeroom(Long classId, AssignHomeroomRequest req) {
        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));
        Teacher teacher = teacherRepository.findById(req.getTeacherId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
        classRoom.assignHomeroom(teacher);
    }

    @Transactional
    public void enrollStudent(Long classId, EnrollStudentRequest req) {
        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));
        Student student = studentRepository.findById(req.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        if (enrollmentRepository.existsByStudentAndAcademicYear(student, classRoom.getAcademicYear())) {
            throw new BusinessException(ErrorCode.ENROLLMENT_DUPLICATE);
        }
        if (enrollmentRepository.existsByClassRoomAndStudentNum(classRoom, req.getStudentNum())) {
            throw new BusinessException(ErrorCode.STUDENT_NUM_DUPLICATE);
        }

        StudentEnrollment enrollment = StudentEnrollment.builder()
                .student(student)
                .classRoom(classRoom)
                .academicYear(classRoom.getAcademicYear())
                .studentNum(req.getStudentNum())
                .build();
        enrollmentRepository.save(enrollment);
    }

    // ─── 과목 배정 ────────────────────────────────────────────

    public List<AssignmentSummary> getAssignments(int academicYear) {
        return assignmentRepository.findByAcademicYearWithDetails(academicYear)
                .stream().map(AssignmentSummary::from).toList();
    }

    @Transactional
    public AssignmentSummary createAssignment(CreateAssignmentRequest req) {
        Teacher teacher = teacherRepository.findById(req.getTeacherId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEACHER_NOT_FOUND));
        ClassRoom classRoom = classRoomRepository.findById(req.getClassId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));
        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBJECT_NOT_FOUND));

        if (assignmentRepository.existsByTeacherAndClassRoomAndSubjectAndAcademicYear(
                teacher, classRoom, subject, req.getAcademicYear())) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_DUPLICATE);
        }

        TeacherAssignment assignment = TeacherAssignment.builder()
                .teacher(teacher)
                .classRoom(classRoom)
                .subject(subject)
                .academicYear(req.getAcademicYear())
                .build();
        assignmentRepository.save(assignment);

        return AssignmentSummary.from(assignment);
    }

    @Transactional
    public void deleteAssignment(Long assignmentId) {
        TeacherAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        assignmentRepository.delete(assignment);
    }
}
