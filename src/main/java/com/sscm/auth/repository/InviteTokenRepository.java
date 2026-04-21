package com.sscm.auth.repository;

import com.sscm.auth.entity.InviteToken;
import com.sscm.auth.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {

    // 미사용 + 미폐기(5회 미초과) + 미만료 OTP 조회
    @Query("""
            SELECT it FROM InviteToken it
            WHERE it.phoneHash = :phoneHash
              AND it.purpose = :purpose
              AND it.usedAt IS NULL
              AND it.expiresAt > :now
              AND it.attemptCount < 5
            ORDER BY it.createdAt DESC
            """)
    Optional<InviteToken> findActiveToken(String phoneHash, OtpPurpose purpose, Instant now);

    // 동일 전화번호의 모든 미사용 OTP 폐기 (신규 발급 전)
    @Modifying
    @Query("""
            UPDATE InviteToken it SET it.usedAt = :now
            WHERE it.phoneHash = :phoneHash
              AND it.purpose = :purpose
              AND it.usedAt IS NULL
            """)
    int invalidateAll(String phoneHash, OtpPurpose purpose, Instant now);

    // 최근 발급 시각 조회 (1분 쿨다운 확인용)
    @Query("""
            SELECT MAX(it.createdAt) FROM InviteToken it
            WHERE it.phoneHash = :phoneHash
              AND it.purpose = :purpose
            """)
    Optional<Instant> findLatestCreatedAt(String phoneHash, OtpPurpose purpose);
}
