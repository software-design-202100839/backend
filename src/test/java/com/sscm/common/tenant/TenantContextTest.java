package com.sscm.common.tenant;

import com.sscm.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantContext 단위 테스트")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("schoolId 설정 및 조회")
    void setAndGet() {
        TenantContext.setSchoolId(1L);
        assertThat(TenantContext.getSchoolId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("clear 후 null 반환")
    void clearResetsToNull() {
        TenantContext.setSchoolId(1L);
        TenantContext.clear();
        assertThat(TenantContext.getSchoolId()).isNull();
    }

    @Test
    @DisplayName("requireSchoolId — 설정되어 있으면 반환")
    void requireSchoolId_returnsWhenSet() {
        TenantContext.setSchoolId(42L);
        assertThat(TenantContext.requireSchoolId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("requireSchoolId — 미설정 시 BusinessException")
    void requireSchoolId_throwsWhenNotSet() {
        assertThatThrownBy(TenantContext::requireSchoolId)
                .isInstanceOf(BusinessException.class);
    }
}
