package com.sscm.analytics.chatbot.service;

import com.pgvector.PGvector;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 피드백·상담 텍스트의 벡터 임베딩 저장 및 유사도 검색 서비스.
 *
 * 왜 Spring AI의 PgVectorStore를 안 쓰는가?
 * - PgVectorStore는 범용 Document 스키마를 전제로 설계됨
 * - 우리는 school_id, student_id, academic_year 등 커스텀 메타데이터 컬럼으로
 *   멀티테넌트 필터링이 필요 → JdbcTemplate 직접 사용이 더 적합
 *
 * 임베딩 모델: Gemini text-embedding-004 (768차원)
 * 유사도 연산: pgvector의 코사인 거리 연산자 <=> 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final JdbcTemplate jdbcTemplate;
    private final GeminiEmbeddingClient embeddingClient;

    /**
     * pgvector-java 공식 방식: JDBC 커넥션에 vector 타입 등록.
     * HikariCP 환경에서 커넥션 풀 초기화 시 한 번 실행.
     */
    @PostConstruct
    void registerVectorType() {
        try {
            jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conn -> {
                PGvector.addVectorType(conn);
                log.info("pgvector 타입 등록 완료");
                return null;
            });
        } catch (Exception e) {
            log.error("pgvector 타입 등록 실패: {}", e.getMessage());
        }
    }

    // ── 저장 ─────────────────────────────────────────────────

    /**
     * 피드백 텍스트를 임베딩하여 feedback_embeddings에 저장.
     * ON CONFLICT DO NOTHING으로 중복 저장을 방지한다.
     */
    public void embedFeedback(Long feedbackId, Long studentId, Long schoolId,
                              int year, int semester, String category, String content) {
        float[] embedding = embeddingClient.embed(content);
        String preview = truncate(content, 200);

        jdbcTemplate.update(
                "INSERT INTO feedback_embeddings " +
                "(feedback_id, student_id, school_id, academic_year, semester, category, content_preview, embedding) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector) " +
                "ON CONFLICT DO NOTHING",
                feedbackId, studentId, schoolId, year, semester, category, preview,
                toVectorLiteral(embedding));

        log.debug("피드백 임베딩 저장 완료: feedbackId={}", feedbackId);
    }

    /**
     * 상담 텍스트를 임베딩하여 counseling_embeddings에 저장.
     */
    public void embedCounseling(Long counselingId, Long studentId, Long schoolId,
                                int year, int semester, String category, String content) {
        float[] embedding = embeddingClient.embed(content);
        String preview = truncate(content, 200);

        jdbcTemplate.update(
                "INSERT INTO counseling_embeddings " +
                "(counseling_id, student_id, school_id, academic_year, semester, category, content_preview, embedding) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector) " +
                "ON CONFLICT DO NOTHING",
                counselingId, studentId, schoolId, year, semester, category, preview,
                toVectorLiteral(embedding));

        log.debug("상담 임베딩 저장 완료: counselingId={}", counselingId);
    }

    // ── 검색 ─────────────────────────────────────────────────

    /**
     * 피드백을 의미 기반으로 검색.
     *
     * pgvector의 <=> (코사인 거리) 연산자를 사용하여 유사도 순으로 정렬.
     * similarity = 1 - cosine_distance (1에 가까울수록 유사)
     *
     * @param query             자연어 검색 질의
     * @param schoolId          멀티테넌트 필터 (필수)
     * @param allowedStudentIds 접근 가능한 학생 ID 목록 (null이면 해당 학교 전체)
     * @param year              학년도 필터 (null이면 전체)
     * @param semester          학기 필터 (null이면 전체)
     * @param limit             반환 건수
     * @return 유사도 순으로 정렬된 검색 결과
     */
    public List<Map<String, Object>> searchFeedback(String query, Long schoolId,
                                                     List<Long> allowedStudentIds,
                                                     Integer year, Integer semester, int limit) {
        float[] queryEmbedding = embeddingClient.embed(query);
        String vecLiteral = toVectorLiteral(queryEmbedding);

        StringBuilder sql = new StringBuilder(
                "SELECT fe.feedback_id, fe.student_id, fe.category, fe.content_preview, " +
                "fe.academic_year, fe.semester, " +
                "1 - (fe.embedding <=> ?::vector) AS similarity " +
                "FROM feedback_embeddings fe WHERE fe.school_id = ?");

        List<Object> params = new ArrayList<>();
        params.add(vecLiteral);
        params.add(schoolId);

        appendFilters(sql, params, allowedStudentIds, year, semester);

        sql.append(" ORDER BY fe.embedding <=> ?::vector LIMIT ?");
        params.add(vecLiteral);
        params.add(limit);

        return executeSimpleQuery(sql.toString(), params.toArray());
    }

    /**
     * 상담을 의미 기반으로 검색.
     */
    public List<Map<String, Object>> searchCounseling(String query, Long schoolId,
                                                       List<Long> allowedStudentIds,
                                                       Integer year, Integer semester, int limit) {
        float[] queryEmbedding = embeddingClient.embed(query);
        String vecLiteral = toVectorLiteral(queryEmbedding);

        StringBuilder sql = new StringBuilder(
                "SELECT ce.counseling_id, ce.student_id, ce.category, ce.content_preview, " +
                "ce.academic_year, ce.semester, " +
                "1 - (ce.embedding <=> ?::vector) AS similarity " +
                "FROM counseling_embeddings ce WHERE ce.school_id = ?");

        List<Object> params = new ArrayList<>();
        params.add(vecLiteral);
        params.add(schoolId);

        appendFilters(sql, params, allowedStudentIds, year, semester);

        sql.append(" ORDER BY ce.embedding <=> ?::vector LIMIT ?");
        params.add(vecLiteral);
        params.add(limit);

        return executeSimpleQuery(sql.toString(), params.toArray());
    }

    /**
     * pgvector 타입 등록 후 UPDATE/INSERT 실행.
     */
    private void executeVectorUpdate(String sql, Object... params) {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conn -> {
            PGvector.addVectorType(conn);
            try (var ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * pgvector 타입 등록 후 쿼리 실행.
     * 매 쿼리마다 해당 커넥션에 PGvector.registerTypes를 호출하여
     * HikariCP 커넥션 풀에서 어떤 커넥션을 받더라도 안전하게 동작.
     */
    private List<Map<String, Object>> executeVectorQuery(String sql, Object[] params) {
        return jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<List<Map<String, Object>>>) conn -> {
            PGvector.addVectorType(conn);
            try (var ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                var rs = ps.executeQuery();
                List<Map<String, Object>> results = new ArrayList<>();
                var meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    for (int c = 1; c <= cols; c++) {
                        row.put(meta.getColumnLabel(c), rs.getObject(c));
                    }
                    results.add(row);
                }
                return results;
            }
        });
    }

    // ── 내부 유틸 ────────────────────────────────────────────

    /**
     * 공통 WHERE 절 필터 추가: student_ids, year, semester.
     */
    private void appendFilters(StringBuilder sql, List<Object> params,
                               List<Long> allowedStudentIds, Integer year, Integer semester) {
        if (allowedStudentIds != null && !allowedStudentIds.isEmpty()) {
            String placeholders = allowedStudentIds.stream()
                    .map(id -> "?")
                    .collect(Collectors.joining(","));
            sql.append(" AND student_id IN (").append(placeholders).append(")");
            params.addAll(allowedStudentIds);
        }
        if (year != null) {
            sql.append(" AND academic_year = ?");
            params.add(year);
        }
        if (semester != null) {
            sql.append(" AND semester = ?");
            params.add(semester);
        }
    }

    /**
     * PGvector 객체 바인딩 대신 문자열 + ::vector 캐스팅으로 벡터를 전달.
     * pgvector-java의 PGvector.setObject()가 HikariCP + PostgreSQL JDBC 42.7.x에서
     * "Unknown type vector" 에러를 발생시키는 문제를 우회.
     * PreparedStatement 파라미터 바인딩이므로 SQL injection 위험 없음.
     */
    private List<Map<String, Object>> executeSimpleQuery(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * float 배열을 pgvector 리터럴 문자열로 변환.
     * 예: [0.1, 0.2, 0.3] → "[0.1,0.2,0.3]"
     * SQL에서 ?::vector로 캐스팅하여 사용.
     */
    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * float 배열을 pgvector-java 공식 PGvector 객체로 변환.
     * embedFeedback/embedCounseling 저장용 (executeVectorUpdate에서 사용).
     */
    private PGvector toVector(float[] embedding) {
        return new PGvector(embedding);
    }

    /**
     * 텍스트를 지정 길이로 잘라 미리보기 생성.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
