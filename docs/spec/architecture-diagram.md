# 시스템 아키텍처 다이어그램

> PPT 발표용. draw.io 또는 Mermaid로 렌더링.

---

## 1. 전체 시스템 아키텍처

```mermaid
graph TB
    subgraph Client["클라이언트"]
        Browser["브라우저<br/>(교사/학생/학부모)"]
    end

    subgraph AWS["AWS Cloud"]
        subgraph CDN["CloudFront (CDN)"]
            CF["d2nrbxodaz2vy.cloudfront.net"]
        end

        subgraph S3["S3"]
            Static["sscm-frontend-static<br/>React SPA 정적 파일"]
        end

        subgraph ALB_Layer["Application Load Balancer"]
            ALB["ALB<br/>경로 기반 라우팅"]
        end

        subgraph ECS["ECS Fargate Cluster"]
            Backend1["Backend Task 1<br/>Spring Boot"]
            Backend2["Backend Task 2<br/>(Auto Scaling)"]
            BackendN["Backend Task N<br/>(max 3)"]
            Consumer["Kafka Consumer<br/>(같은 앱 내)"]
            Monitoring["Prometheus + Grafana"]
        end

        subgraph Data["데이터 계층"]
            RDS_Main["RDS PostgreSQL<br/>운영 DB"]
            RDS_Analytics["RDS PostgreSQL<br/>분석 DB (OLAP)"]
            Redis["ElastiCache Redis<br/>JWT 캐시"]
        end

        subgraph Kafka["Amazon MSK"]
            MSK["Kafka Cluster<br/>kafka.t3.small x2"]
        end
    end

    Browser -->|"HTTPS"| CF
    CF -->|"/* (정적)"| Static
    CF -->|"/api/*, /ws/*"| ALB
    ALB --> Backend1
    ALB --> Backend2
    ALB --> BackendN
    ALB -->|"/grafana/*"| Monitoring
    Backend1 --> RDS_Main
    Backend1 --> Redis
    Backend1 -->|"이벤트 발행"| MSK
    MSK -->|"이벤트 수신"| Consumer
    Consumer -->|"집계 결과"| RDS_Analytics
    Backend1 -->|"대시보드 조회"| RDS_Analytics
```

---

## 2. OLAP 데이터 파이프라인

```mermaid
graph LR
    subgraph OLTP["운영 시스템 (OLTP)"]
        Service["도메인 서비스<br/>ScoreService<br/>FeedbackService<br/>CounselingService"]
        DB_Main["운영 DB<br/>PostgreSQL"]
    end

    subgraph Pipeline["이벤트 파이프라인"]
        Event["Spring Event<br/>ScoreChangedEvent"]
        Bridge["EventBridge<br/>Spring → Kafka 변환"]
        MSK2["Amazon MSK<br/>4개 토픽"]
    end

    subgraph OLAP["분석 시스템 (OLAP)"]
        Consumer2["Kafka Consumer<br/>4개 Consumer"]
        Aggregation["집계 로직<br/>운영 DB SELECT →<br/>분석 DB UPSERT"]
        DB_Analytics["분석 DB<br/>6개 집계 테이블"]
        Dashboard["Dashboard API<br/>REST 조회"]
        AI["AI 챗봇<br/>Gemini + Tool Use"]
    end

    Service -->|"1. 데이터 변경"| DB_Main
    Service -->|"2. 이벤트 발행"| Event
    Event --> Bridge
    Bridge -->|"3. Kafka 전송"| MSK2
    MSK2 -->|"4. 이벤트 수신"| Consumer2
    Consumer2 --> Aggregation
    Aggregation -->|"SELECT"| DB_Main
    Aggregation -->|"5. UPSERT"| DB_Analytics
    Dashboard -->|"6. 조회"| DB_Analytics
    AI -->|"Tool Use"| Dashboard
```

---

## 3. 요청 흐름 + 장애 격리

```mermaid
graph TD
    Request["API 요청"] --> JWT["JWT 검증<br/>Redis 캐시 (<1ms)"]
    JWT -->|"캐시 miss"| DB_Fallback["DB fallback (2-5ms)"]
    JWT --> Auth["인가 검증<br/>역할별 접근제어"]
    Auth --> Logic["비즈니스 로직"]
    Logic --> Response["응답"]

    Logic -->|"데이터 변경 시"| KafkaEvent["Kafka 이벤트 발행<br/>(비동기, @Async)"]

    subgraph Isolation["장애 격리"]
        KafkaDown["❌ Kafka 장애"]
        RedisDown["❌ Redis 장애"]
        AnalyticsDown["❌ 분석 DB 장애"]
    end

    KafkaDown -->|"운영 서비스 정상<br/>분석만 지연<br/>backfill로 복구"| OK1["✅"]
    RedisDown -->|"DB fallback<br/>느려질 뿐 중단 없음"| OK2["✅"]
    AnalyticsDown -->|"운영 완전 무관<br/>대시보드만 불가"| OK3["✅"]
```

---

## 4. CI/CD 파이프라인

```mermaid
graph LR
    Dev["개발자<br/>git push"] -->|"develop"| CI["GitHub Actions CI<br/>테스트 + JaCoCo<br/>+ SonarCloud"]
    CI -->|"통과"| CD["GitHub Actions CD<br/>(수동 실행)"]
    CD --> Build["Docker Build"]
    Build --> ECR["ECR Push<br/>latest + SHA"]
    ECR --> Deploy["ECS<br/>force-new-deployment"]
    Deploy --> Health["헬스체크<br/>/actuator/health"]

    Frontend["프론트엔드<br/>npm run build"] --> S3Upload["S3 Sync"]
    S3Upload --> Invalidation["CloudFront<br/>캐시 무효화"]
```

---

## draw.io용 노트

위 Mermaid 다이어그램을 draw.io에서 다시 그릴 때 참고:

### 색상 가이드
- 클라이언트: 파란색
- ECS/컨테이너: 주황색
- 데이터베이스: 초록색
- Kafka/메시징: 보라색
- CDN/S3: 노란색
- 모니터링: 회색

### 포함할 AWS 아이콘
- ECS Fargate, ALB, RDS, ElastiCache, MSK, S3, CloudFront, CloudWatch, ECR

### 슬라이드 배치
1. 슬라이드 3: 전체 시스템 아키텍처 (1번)
2. 슬라이드 4: OLAP 데이터 파이프라인 (2번)
3. 슬라이드 5: 부하 테스트 결과 + 장애 격리 (3번 참고)
