package com.sscm.common.tenant;

import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;

/**
 * 현재 요청의 학교(테넌트) 컨텍스트를 ThreadLocal로 관리.
 *
 * JWT 필터에서 schoolId를 추출하여 set → 서비스 레이어에서 get → 요청 끝나면 clear.
 * 메서드 시그니처를 변경하지 않고도 모든 서비스에서 현재 학교를 알 수 있다.
 */
public class TenantContext {

    private static final ThreadLocal<Long> currentSchoolId = new ThreadLocal<>();

    public static Long getSchoolId() {
        return currentSchoolId.get();
    }

    public static void setSchoolId(Long schoolId) {
        currentSchoolId.set(schoolId);
    }

    public static void clear() {
        currentSchoolId.remove();
    }

    public static Long requireSchoolId() {
        Long id = currentSchoolId.get();
        if (id == null) {
            throw new BusinessException(ErrorCode.SCHOOL_NOT_SET);
        }
        return id;
    }
}
