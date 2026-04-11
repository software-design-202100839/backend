package com.sscm.common.exception;

import com.sscm.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        List<ApiResponse.FieldError> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("COMMON_001", "유효성 검증에 실패했습니다", fieldErrors));
    }

    /**
     * SSCM-58: 낙관적 락 충돌 → 409 Conflict로 변환.
     * Hibernate가 던지는 ObjectOptimisticLockingFailureException은
     * Spring의 OptimisticLockingFailureException 하위 클래스이므로 한 번에 처리.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("낙관적 락 충돌 — 다른 트랜잭션이 먼저 수정함: {}", e.getMessage());
        ErrorCode ec = ErrorCode.CONCURRENT_MODIFICATION;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(ApiResponse.error(ec.getCode(), ec.getMessage()));
    }
}
