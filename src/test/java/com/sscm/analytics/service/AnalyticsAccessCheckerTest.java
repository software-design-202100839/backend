package com.sscm.analytics.service;

import com.sscm.auth.entity.Parent;
import com.sscm.auth.entity.Student;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.ParentRepository;
import com.sscm.auth.repository.ParentStudentRepository;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.common.entity.School;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsAccessChecker 단위 테스트")
class AnalyticsAccessCheckerTest {

    @InjectMocks
    private AnalyticsAccessChecker accessChecker;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ParentRepository parentRepository;

    @Mock
    private ParentStudentRepository parentStudentRepository;

    private final School testSchool = School.builder().id(1L).name("테스트학교").code("TEST").build();

    @BeforeEach
    void setUp() {
        TenantContext.setSchoolId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Authentication authOf(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    @Nested
    @DisplayName("교사/관리자 접근")
    class TeacherAdminAccess {

        @Test
        @DisplayName("교사는 같은 학교 학생 데이터 조회 가능")
        void teacher_canAccessAnyStudent() {
            Authentication auth = authOf(1L, "ROLE_TEACHER");
            Student student = Student.builder().id(99L)
                    .user(User.builder().id(50L).school(testSchool).build()).build();
            given(studentRepository.findById(99L)).willReturn(Optional.of(student));

            assertThatCode(() -> accessChecker.checkAccess(99L, auth))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("관리자는 같은 학교 학생 데이터 조회 가능")
        void admin_canAccessAnyStudent() {
            Authentication auth = authOf(1L, "ROLE_ADMIN");
            Student student = Student.builder().id(99L)
                    .user(User.builder().id(50L).school(testSchool).build()).build();
            given(studentRepository.findById(99L)).willReturn(Optional.of(student));

            assertThatCode(() -> accessChecker.checkAccess(99L, auth))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("학생 접근")
    class StudentAccess {

        @Test
        @DisplayName("학생은 본인 데이터만 조회 가능")
        void student_canAccessOwnData() {
            Long userId = 10L;
            Long studentId = 5L;
            Authentication auth = authOf(userId, "ROLE_STUDENT");

            Student student = Student.builder().id(studentId)
                    .user(User.builder().id(userId).school(testSchool).build()).build();
            given(studentRepository.findByUser_Id(userId)).willReturn(Optional.of(student));

            assertThatCode(() -> accessChecker.checkAccess(studentId, auth))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("학생이 다른 학생 데이터 조회 시 ACCESS_DENIED")
        void student_cannotAccessOtherData() {
            Long userId = 10L;
            Long ownStudentId = 5L;
            Long otherStudentId = 99L;
            Authentication auth = authOf(userId, "ROLE_STUDENT");

            Student student = Student.builder().id(ownStudentId)
                    .user(User.builder().id(userId).school(testSchool).build()).build();
            given(studentRepository.findByUser_Id(userId)).willReturn(Optional.of(student));

            assertThatThrownBy(() -> accessChecker.checkAccess(otherStudentId, auth))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("학생 엔티티가 없으면 STUDENT_NOT_FOUND")
        void student_notFound() {
            Long userId = 10L;
            Authentication auth = authOf(userId, "ROLE_STUDENT");

            given(studentRepository.findByUser_Id(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accessChecker.checkAccess(5L, auth))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STUDENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("학부모 접근")
    class ParentAccess {

        @Test
        @DisplayName("학부모는 자녀 데이터 조회 가능")
        void parent_canAccessChildData() {
            Long userId = 20L;
            Long studentId = 5L;
            Authentication auth = authOf(userId, "ROLE_PARENT");

            Parent parent = Parent.builder().id(1L)
                    .user(User.builder().id(userId).school(testSchool).build()).build();
            Student student = Student.builder().id(studentId)
                    .user(User.builder().id(30L).school(testSchool).build()).build();

            given(parentRepository.findByUser_Id(userId)).willReturn(Optional.of(parent));
            given(studentRepository.findById(studentId)).willReturn(Optional.of(student));
            given(parentStudentRepository.existsByParentAndStudent(parent, student)).willReturn(true);

            assertThatCode(() -> accessChecker.checkAccess(studentId, auth))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("학부모가 자녀가 아닌 학생 조회 시 ACCESS_DENIED")
        void parent_cannotAccessNonChildData() {
            Long userId = 20L;
            Long studentId = 5L;
            Authentication auth = authOf(userId, "ROLE_PARENT");

            Parent parent = Parent.builder().id(1L)
                    .user(User.builder().id(userId).school(testSchool).build()).build();
            Student student = Student.builder().id(studentId)
                    .user(User.builder().id(30L).school(testSchool).build()).build();

            given(parentRepository.findByUser_Id(userId)).willReturn(Optional.of(parent));
            given(studentRepository.findById(studentId)).willReturn(Optional.of(student));
            given(parentStudentRepository.existsByParentAndStudent(parent, student)).willReturn(false);

            assertThatThrownBy(() -> accessChecker.checkAccess(studentId, auth))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("학부모 엔티티가 없으면 ACCESS_DENIED")
        void parent_notFound() {
            Long userId = 20L;
            Authentication auth = authOf(userId, "ROLE_PARENT");

            given(parentRepository.findByUser_Id(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accessChecker.checkAccess(5L, auth))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("알 수 없는 역할")
    class UnknownRole {

        @Test
        @DisplayName("정의되지 않은 역할은 ACCESS_DENIED")
        void unknownRole_denied() {
            Authentication auth = authOf(1L, "ROLE_UNKNOWN");

            assertThatThrownBy(() -> accessChecker.checkAccess(5L, auth))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }
}
