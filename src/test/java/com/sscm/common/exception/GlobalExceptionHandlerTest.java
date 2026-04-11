package com.sscm.common.exception;

import com.sscm.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("OptimisticLockingFailureException → 409 CONCURRENT_MODIFICATION")
    void handleOptimisticLock() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleOptimisticLock(new OptimisticLockingFailureException("conflict"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMMON_004");
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException(Hibernate)도 동일하게 409로 처리")
    void handleHibernateOptimisticLock() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Score", 1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMMON_004");
    }

    @Test
    @DisplayName("BusinessException → ErrorCode의 HttpStatus와 코드 그대로 매핑")
    void handleBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.SCORE_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("GRADE_001");
    }
}
