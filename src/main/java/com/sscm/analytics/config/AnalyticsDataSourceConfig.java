package com.sscm.analytics.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * 분석 전용 DB의 DataSource + JdbcTemplate 설정.
 *
 * Spring Boot의 기본 DataSource(spring.datasource)는 운영 DB에 연결된다.
 * 분석 DB는 별도 인스턴스이므로, 수동으로 DataSource를 만들고
 * 이를 사용하는 JdbcTemplate 빈을 등록한다.
 *
 * @Qualifier("analyticsJdbc")로 구분하여, 분석 관련 코드에서만 이 JdbcTemplate을 주입받는다.
 */
@Configuration
public class AnalyticsDataSourceConfig {

    // application-dev.yml의 analytics.datasource.* 값을 읽어옴
    @Value("${analytics.datasource.url}")
    private String url;

    @Value("${analytics.datasource.username}")
    private String username;

    @Value("${analytics.datasource.password}")
    private String password;

    /**
     * 분석 DB용 DataSource 생성.
     * 운영 DB DataSource와 이름이 겹치지 않도록 "analyticsDataSource"로 등록.
     */
    @Bean(name = "analyticsDataSource")
    public DataSource analyticsDataSource() {
        DataSource dataSource = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();

        // 앱 시작 시 analytics-schema.sql을 실행하여 테이블 자동 생성
        // CREATE TABLE IF NOT EXISTS 이므로 이미 있으면 무시됨
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("analytics-schema.sql"));
        populator.execute(dataSource);

        return dataSource;
    }

    /**
     * 분석 DB 전용 JdbcTemplate.
     *
     * 사용 예시:
     *   @Autowired @Qualifier("analyticsJdbc")
     *   private JdbcTemplate analyticsJdbc;
     *
     * 이렇게 하면 운영 DB가 아닌 분석 DB에 SQL이 실행된다.
     */
    @Bean(name = "analyticsJdbc")
    public JdbcTemplate analyticsJdbcTemplate(
            @Qualifier("analyticsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
