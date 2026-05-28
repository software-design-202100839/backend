package com.sscm.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * 분석 전용 DB의 JdbcTemplate 설정.
 *
 * 주의: DataSource를 @Bean으로 노출하면 안 된다!
 * Spring Boot가 "이미 DataSource 빈이 있네?"하고 기본 DataSource 자동 생성을 건너뛰어서,
 * Flyway 등이 분석 DB에 연결되는 문제가 생긴다.
 *
 * 해결: DataSource는 이 클래스 내부에서만 생성하고,
 *       외부에는 JdbcTemplate만 @Bean으로 노출한다.
 */
@Configuration
public class AnalyticsDataSourceConfig {

    @Value("${analytics.datasource.url}")
    private String url;

    @Value("${analytics.datasource.username}")
    private String username;

    @Value("${analytics.datasource.password}")
    private String password;

    /**
     * 운영 DB JdbcTemplate을 @Primary로 명시 등록.
     * analyticsJdbc 빈과 타입이 동일하므로 @Qualifier 없이 주입되는 서비스에
     * 운영 DB가 확실히 주입되도록 보장한다.
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 분석 DB 전용 JdbcTemplate.
     *
     * 내부에서 DataSource를 생성하고, analytics-schema.sql로 테이블을 자동 생성한 뒤,
     * 그 DataSource를 감싸는 JdbcTemplate을 반환한다.
     *
     * DataSource는 @Bean으로 등록하지 않으므로 Spring Boot의 자동 설정에 영향을 주지 않는다.
     */
    @Bean(name = "analyticsJdbc")
    public JdbcTemplate analyticsJdbcTemplate() {
        // 분석 DB DataSource 생성 (빈으로 등록하지 않음)
        DataSource dataSource = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();

        // 앱 시작 시 analytics-schema.sql 실행 → 테이블 자동 생성
        // CREATE TABLE IF NOT EXISTS 이므로 이미 있으면 무시됨
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("analytics-schema.sql"));
        populator.execute(dataSource);

        return new JdbcTemplate(dataSource);
    }
}
