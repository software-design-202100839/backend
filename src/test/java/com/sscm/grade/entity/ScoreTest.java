package com.sscm.grade.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Score 엔티티 단위 테스트")
class ScoreTest {

    @ParameterizedTest(name = "점수 {0} → 등급 {1}")
    @CsvSource({
            "100.00, A+",
            "95.00,  A+",
            "94.99,  A",
            "90.00,  A",
            "89.99,  B+",
            "85.00,  B+",
            "84.99,  B",
            "80.00,  B",
            "79.99,  C+",
            "75.00,  C+",
            "74.99,  C",
            "70.00,  C",
            "69.99,  D+",
            "65.00,  D+",
            "64.99,  D",
            "60.00,  D",
            "59.99,  F",
            "0.00,   F"
    })
    @DisplayName("점수별 등급 자동 계산이 정확해야 한다")
    void calculateGradeLetter(String scoreStr, String expectedGrade) {
        BigDecimal score = new BigDecimal(scoreStr.trim());
        assertThat(Score.calculateGradeLetter(score)).isEqualTo(expectedGrade.trim());
    }

    @Test
    @DisplayName("성적 수정 시 점수, 등급, 수정자가 갱신된다")
    void updateScore() {
        Score score = Score.builder()
                .score(new BigDecimal("80.00"))
                .gradeLetter("B")
                .updatedBy(1L)
                .build();

        score.updateScore(new BigDecimal("95.00"), "A+", 2L);

        assertThat(score.getScore()).isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(score.getGradeLetter()).isEqualTo("A+");
        assertThat(score.getUpdatedBy()).isEqualTo(2L);
        assertThat(score.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("석차 수정이 정상 반영된다")
    void updateRank() {
        Score score = Score.builder().build();

        score.updateRank(3);

        assertThat(score.getRank()).isEqualTo(3);
    }
}
