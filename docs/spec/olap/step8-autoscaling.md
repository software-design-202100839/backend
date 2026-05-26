# Step 8: ECS Auto Scaling 설정

> 작성일: 2026-05-26
> 목적: CPU 사용률 기반 자동 스케일아웃/인으로 트래픽 대응

---

## 설정 내용

| 항목 | 값 |
|------|-----|
| 최소 Task 수 | 1 |
| 최대 Task 수 | 3 |
| 스케일아웃 기준 | CPU 평균 70% 초과 |
| 스케일인 기준 | CPU 평균 70% 미만 |
| 스케일아웃 쿨다운 | 60초 |
| 스케일인 쿨다운 | 120초 |

---

## 동작 원리

```
CPU < 70%  → Task 1개 유지 (비용 절약)
CPU > 70%  → Task 최대 3개까지 자동 증가 (부하 대응)
CPU 감소   → 120초 후 Task 자동 감소 (과잉 방지)
```

CloudWatch Alarm이 자동 생성됨:
- AlarmHigh: CPU > 70% → 스케일아웃
- AlarmLow: CPU < 70% → 스케일인

---

## 왜 Auto Scaling?

| 질문 | 답변 |
|------|------|
| 고정 3개로 두면 안 돼? | 평소 트래픽이 낮으면 비용 낭비. Auto Scaling은 필요할 때만 증가 |
| 왜 CPU 70%? | 80% 이상이면 이미 응답 지연 발생. 70%에서 선제적으로 스케일아웃 |
| 왜 최대 3개? | 현재 규모 + 비용 고려. 필요 시 max-capacity 조정 가능 |
| 스케일아웃에 얼마 걸려? | 새 Task 시작 ~90초 (ECS Fargate 이미지 풀 + 앱 부팅) |

---

## 설정 명령어

```bash
# Scalable Target 등록
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/sscm-cluster/sscm-backend \
  --min-capacity 1 --max-capacity 3

# CPU 기반 스케일링 정책
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/sscm-cluster/sscm-backend \
  --policy-name sscm-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration \
    '{"TargetValue":70.0,
      "PredefinedMetricSpecification":{"PredefinedMetricType":"ECSServiceAverageCPUUtilization"},
      "ScaleOutCooldown":60,"ScaleInCooldown":120}'
```

---

## 비용 영향

- 평소: Task 1개 (~$0.03/hr)
- 부하 시: Task 최대 3개 (~$0.09/hr)
- 부하 해소 후 2분 뒤 자동 축소
