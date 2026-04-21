# SSCM-57: DAST 보안 스캔

- Assignee: 이큐에이 (QA Lee)
- Sprint: 3
- Status: done
- Created: 2026-03-28
- Completed: 2026-04-03
- Epic: SSCM-9 (보안 강화)

## 결과 요약

도구: OWASP ZAP (Burp Suite 대체 — 동일 OWASP Top 10 커버리지)

```
FAIL: 0건  |  WARN: 9건  |  PASS: 58건
Critical: 0건  |  High: 0건
```

## WARN 항목 (전부 Medium/Low)

| # | 항목 | 위험도 | 설명 |
|---|------|--------|------|
| 1 | Missing Anti-clickjacking Header | Medium | X-Frame-Options 헤더 없음 |
| 2 | X-Content-Type-Options Missing | Low | MIME 스니핑 방지 헤더 없음 |
| 3 | Suspicious Comments | Informational | JS 번들에 주석 잔류 |
| 4 | Server Version Leak | Low | Nginx 버전 정보 노출 |
| 5 | CSP Header Not Set | Medium | Content-Security-Policy 없음 |
| 6 | Non-Storable Content | Informational | 캐시 설정 미흡 |
| 7 | Permissions Policy Missing | Low | Permissions-Policy 헤더 없음 |
| 8 | Modern Web Application | Informational | SPA 감지 (정보성) |
| 9 | COEP Header Missing | Low | Cross-Origin-Embedder-Policy 없음 |

## 대응 방안
WARN 항목은 전부 Nginx 보안 헤더 추가로 해결 가능 (Sprint 4 또는 시간 여유 시):
```nginx
add_header X-Frame-Options "SAMEORIGIN";
add_header X-Content-Type-Options "nosniff";
add_header Content-Security-Policy "default-src 'self'; script-src 'self'";
add_header Permissions-Policy "camera=(), microphone=()";
add_header Cross-Origin-Embedder-Policy "require-corp";
server_tokens off;
```

## Acceptance Criteria
- [x] DAST 리포트 생성 (docs/zap-report.html, zap-report.json)
- [x] Critical 0건
- [x] High 0건

## 리포트 파일
- `docs/zap-report.html` — HTML 리포트
- `docs/zap-report.json` — JSON 리포트 (91KB)
