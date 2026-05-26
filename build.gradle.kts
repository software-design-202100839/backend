plugins {
	java
	jacoco
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.sonarqube") version "6.0.1.5171"
	id("org.owasp.dependencycheck") version "9.0.9"
}

group = "com.sscm"
version = "0.0.1-SNAPSHOT"
description = "SSCM - 학생 성적 및 상담 관리 시스템"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }  // Spring AI는 아직 milestone 저장소에 있음
}

// Spring AI BOM — Spring AI 관련 의존성 버전을 일괄 관리
dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:1.0.0-M6")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
	// Redis — JWT 블랙리스트 캐시 + WebSocket 세션 공유 (다중 인스턴스 대응)
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// Kafka — 도메인 이벤트를 Kafka로 발행(Producer)하고 수신(Consumer)하기 위한 Spring 래퍼
	implementation("org.springframework.kafka:spring-kafka")
	// Spring AI + Gemini — AI 챗봇 (Tool Use / Function Calling 패턴)
	// Google AI Studio의 OpenAI 호환 API를 사용
	implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
	// API Documentation
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
	// Spring AI가 가져온 swagger-core와 springdoc의 swagger-annotations 버전 충돌 해결
	implementation("io.swagger.core.v3:swagger-annotations:2.2.29")
	implementation("io.swagger.core.v3:swagger-core-jakarta:2.2.29")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")   // 테스트용 내장 Kafka 브로커
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

jacoco {
	toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
}

tasks.jacocoTestCoverageVerification {
	violationRules {
		rule {
			limit {
				minimum = "0.30".toBigDecimal()
			}
		}
	}
}

tasks.sonarqube {
	dependsOn(tasks.jacocoTestReport)
}

sonarqube {
	properties {
		property("sonar.host.url", "https://sonarcloud.io")
		property("sonar.organization", "software-design-202100839")
		property("sonar.projectKey", "software-design-202100839_backend")
		property("sonar.projectName", "SSCM Backend")
		property("sonar.sources", "src/main/java")
		property("sonar.tests", "src/test/java")
		property("sonar.java.coveragePlugin", "jacoco")
		property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
	}
}
