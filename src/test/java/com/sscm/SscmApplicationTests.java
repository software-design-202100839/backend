package com.sscm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
		disabledReason = "DB 연결 없이는 스킵 — CI에서 Docker Compose로 실행 시 활성화")
class SscmApplicationTests {

	@Test
	void contextLoads() {
	}

}
