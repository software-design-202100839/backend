package com.sscm.common.service;

import com.sscm.common.entity.AuditLog;
import com.sscm.common.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 변경 이력을 audit_logs 테이블에 저장.
     * REQUIRES_NEW: 본 트랜잭션 롤백 여부와 무관하게 감사 로그는 독립 저장.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String tableName, Long recordId, String fieldName,
                       String oldValue, String newValue, Long changedBy) {
        auditLogRepository.save(AuditLog.builder()
                .tableName(tableName)
                .recordId(recordId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .build());
    }
}
