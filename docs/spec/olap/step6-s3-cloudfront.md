# Step 6: S3 + CloudFront — Frontend CDN 배포

> 작성일: 2026-05-26
> 목적: React SPA를 S3 + CloudFront로 정적 호스팅. 컨테이너 서빙 대체.

---

## 왜 S3 + CloudFront?

| 방식 | 문제 |
|------|------|
| ECS로 React 서빙 | 정적 파일에 컨테이너 자원 낭비. CPU/메모리를 HTML/JS 서빙에 소비 |
| S3 + CloudFront | CDN 엣지 400+ 리전에서 캐시 서빙. 비용 거의 무료. 글로벌 레이턴시 감소 |

---

## 아키텍처

```
사용자 → CloudFront (CDN)
           ├── /* → S3 (React 정적 파일)
           ├── /api/* → ALB → ECS Backend
           └── /ws/* → ALB → ECS Backend (WebSocket)
```

- CloudFront가 경로 기반 라우팅으로 프론트/백엔드 분리
- React SPA: 404/403 → index.html 리다이렉트 (클라이언트 라우팅)
- API 요청: 캐시 비활성화 (CachingDisabled 정책)

---

## 배포 구성

| 리소스 | 설명 |
|--------|------|
| S3 Bucket | `sscm-frontend-static` — React 빌드 파일 저장 |
| CloudFront OAC | S3에 CloudFront만 접근 허용 (퍼블릭 차단) |
| CloudFront Distribution | CDN + 경로 기반 라우팅 |
| CloudFormation 스택 | `sscm-cdn` (`infra/cfn-cdn.yml`) |

---

## 배포 결과

| 항목 | 값 |
|------|-----|
| CloudFront URL | https://d2nrbxodaz2vy.cloudfront.net |
| Distribution ID | E3JYLIWWT7NFX5 |
| S3 Bucket | sscm-frontend-static |

---

## 배포 명령어

```bash
# 1. Frontend 빌드
cd /mnt/c/Users/seung/workspace/sscm-frontend
npm run build

# 2. S3 업로드
aws s3 sync dist/ s3://sscm-frontend-static/ --delete

# 3. CloudFront 캐시 무효화
aws cloudfront create-invalidation --distribution-id E3JYLIWWT7NFX5 --paths "/*"
```

---

## 비용

- S3: 저장 ~1MB, 거의 무료
- CloudFront: 무료 티어 1TB/월 전송, 1천만 요청/월
- 발표 시연 수준에서 비용 발생 없음
