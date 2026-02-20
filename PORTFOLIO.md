# QueueGate - 대용량 트래픽 처리 티켓팅 대기열 시스템

## 프로젝트 개요

> 수만 명이 동시에 접속하는 티켓팅 상황에서 서버 과부하 없이 안정적으로 대기열을 관리하고, 순차적으로 입장시키는 **Virtual Waiting Room(가상 대기실)** 시스템

| 항목 | 내용 |
|------|------|
| 기간 | 2026.02 |
| 인원 | 1인 (개인 프로젝트) |
| 역할 | 백엔드 설계/구현, 인프라 구성, 부하 테스트 |
| GitHub | [QueueGate Repository 링크] |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Spring Boot 4.0.2, Java 17 |
| In-Memory DB | Redis 7 (Sorted Set, Set, String) |
| Load Balancer | Nginx (Round Robin) |
| Container | Docker, Docker Compose |
| Frontend | Thymeleaf, Chart.js |
| 부하 테스트 | Locust (Python) |

---

## 시스템 아키텍처

```
                    ┌─────────────────┐
                    │   Locust 부하   │
                    │   (10,000명)    │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  Nginx (:80)    │
                    │  Load Balancer  │
                    │  Round Robin    │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼──────┐ ┌────▼───────┐ ┌────▼───────┐
     │  App-1 (:8080)│ │ App-2(:8080)│ │ App-3(:8080)│
     │  Spring Boot  │ │ Spring Boot │ │ Spring Boot │
     └────────┬──────┘ └─────┬──────┘ └─────┬──────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    ┌────────▼────────┐
                    │  Redis (:6379)  │
                    │  Sorted Set     │
                    │  (공유 상태)     │
                    └─────────────────┘
```

---

## 핵심 기능 및 구현 상세

### 1. Redis Sorted Set 기반 대기열 관리

**왜 Redis Sorted Set인가?**
- RDB에 수만 건을 직접 INSERT하면 DB 병목 발생
- Redis는 메모리 기반으로 초당 10만+ 명령 처리 가능
- Sorted Set은 Score(타임스탬프) 기준 자동 정렬 → 자연스러운 FIFO 대기열

**Redis Key 설계**

| Key | Type | 용도 |
|-----|------|------|
| `queue:wait` | Sorted Set | 대기열 (Score=진입시각, Member=UUID) |
| `queue:active` | Set | 입장 허용된 사용자 집합 |
| `queue:active:ttl:{token}` | String (TTL) | 사용자별 만료 관리 (5분) |
| `queue:counter` | String | 총 입장 처리 수 (통계용) |

**주요 Redis 명령어 활용**

```
진입:     ZADD queue:wait {timestamp} {uuid}     → O(log N)
순위조회: ZRANK queue:wait {token}                → O(log N)
입장처리: ZPOPMIN queue:wait {count}              → O(log N * count), 원자적 연산
검증:     SISMEMBER queue:active {token}          → O(1)
```

### 2. 대기열 플로우

```
[사용자] 예매하기 클릭
    │
    ▼
[POST /api/queue/enter]
    │  ZADD queue:wait {timestamp} {uuid}
    │  → 토큰(UUID) 발급
    ▼
[대기실 페이지] (/waiting?token=xxx)
    │  2초 간격 폴링: GET /api/queue/rank?token=xxx
    │  → ZRANK로 순위 조회
    │  → 순위, 대기인원, 예상시간 표시
    ▼
[스케줄러] (1초 간격, 100명씩)
    │  ZPOPMIN queue:wait 100
    │  → SADD queue:active {token}
    │  → SET queue:active:ttl:{token} "1" EX 300
    ▼
[입장 허용] admitted=true 감지
    │  → 자동 리다이렉트
    ▼
[예매 페이지] (/booking?token=xxx)
    │  SISMEMBER queue:active {token} → 토큰 검증
    │  → 유효하지 않은 토큰은 메인으로 리다이렉트
    ▼
[예매 완료]
```

### 3. Nginx 로드 밸런싱

**구성**
- 3개 Spring Boot 인스턴스를 Nginx가 Round Robin으로 분배
- 모든 인스턴스가 동일한 Redis를 공유 → Stateless 구조
- 어떤 인스턴스가 요청을 처리하든 동일한 결과 보장

```nginx
upstream queuegate {
    server app1:8080;
    server app2:8080;
    server app3:8080;
}
```

**인스턴스 추적**
- 응답 헤더에 `X-Instance-Id` 추가하여 어떤 인스턴스가 처리했는지 식별
- 모니터링 대시보드에서 인스턴스별 요청 분배 현황 실시간 표시

### 4. Redis 분산 락 (Distributed Lock)

**문제**: 3개 인스턴스가 각각 `@Scheduled`를 실행하면 동일 사용자가 3번 입장 처리될 수 있음

**해결**: Redis `SETNX` 기반 분산 락

```java
// 락 획득 시도 (TTL 3초)
public boolean tryLock(String key, Duration ttl) {
    return redisTemplate.opsForValue()
            .setIfAbsent(key, "locked", ttl);
}
```

- 스케줄러 실행 시 `scheduler:lock` 키로 락 획득 시도
- 락 획득 성공한 인스턴스만 `ZPOPMIN` 실행
- 실패한 인스턴스는 해당 주기 스킵
- TTL 3초로 설정하여 인스턴스 장애 시에도 자동 해제

### 5. 실시간 모니터링 대시보드

Chart.js 기반 실시간 대시보드 (`/monitor`)

**표시 항목:**
- 핵심 지표 카드: 대기 중, 활성 사용자, 총 입장 처리, 초당 처리량, 총 요청 수
- 대기열/활성 사용자 추이 차트 (Line)
- 초당 처리량 차트 (Line)
- 인스턴스별 요청 분배 차트 (Doughnut)
- 인스턴스별 처리 현황 바 차트
- 이벤트 로그 (입장 처리, 대기열 급증 감지)

---

## 부하 테스트 결과

### 테스트 환경

| 항목 | 사양 |
|------|------|
| 머신 | MacBook Pro (Apple Silicon) |
| 테스트 도구 | Locust 2.43.3 |
| 서버 구성 | Nginx + Spring Boot × 3 + Redis (Docker) |

### 테스트 1: 단일 인스턴스 (10,000명)

| 항목 | 결과 |
|------|------|
| 동시 접속자 | 10,000명 |
| 총 요청 수 | 290,146건 |
| 실패율 | **0.27%** |
| 초당 처리량 | **~2,937 RPS** |
| 순위 조회 중간값 | 380ms |

### 테스트 2: 로드 밸런싱 3개 인스턴스 (10,000명)

| 항목 | 결과 |
|------|------|
| 동시 접속자 | 10,000명 |
| 총 요청 수 | 84,954건 |
| 실패율 | **0.14%** (로드 밸런싱 후 감소) |
| 초당 처리량 | **~1,945 RPS** |
| 10,000명 전원 입장 | **60초 이내** |

**API별 응답 시간 (로드 밸런싱)**

| API | 중간값 | P95 | P99 |
|-----|--------|-----|-----|
| 대기열 진입 (POST) | 46ms | 270ms | 620ms |
| 순위 조회 - 폴링 (GET) | 45ms | 100ms | 290ms |
| 예매 페이지 (GET) | 40ms | 81ms | 110ms |
| 토큰 검증 (GET) | 38ms | 65ms | 94ms |

### 로드 밸런싱 분배 검증

```
요청 1: X-Instance-Id: app-1
요청 2: X-Instance-Id: app-2
요청 3: X-Instance-Id: app-3
요청 4: X-Instance-Id: app-1   ← Round Robin 순환 확인
요청 5: X-Instance-Id: app-2
요청 6: X-Instance-Id: app-3
```

---

## 프로젝트 구조

```
QueueGate/
├── Dockerfile                          # Spring Boot 컨테이너 이미지
├── docker-compose.yml                  # Redis + App×3 + Nginx 오케스트레이션
├── nginx/nginx.conf                    # Nginx 로드 밸런서 설정
├── locustfile.py                       # Locust 부하 테스트 시나리오
├── build.gradle                        # Gradle 빌드 설정
│
├── src/main/java/com/study/project/
│   ├── QueueGateApplication.java       # @EnableScheduling
│   ├── config/
│   │   ├── RedisConfig.java            # StringRedisTemplate 빈
│   │   └── InstanceFilter.java         # X-Instance-Id 응답 헤더
│   ├── dto/
│   │   ├── QueueTokenResponse.java     # 토큰 응답
│   │   ├── QueueRankResponse.java      # 순위 응답
│   │   └── MonitorResponse.java        # 모니터링 응답
│   ├── service/
│   │   ├── QueueService.java           # 핵심 대기열 로직 (Redis 연산)
│   │   └── DistributedLock.java        # Redis SETNX 분산 락
│   ├── scheduler/
│   │   └── QueueScheduler.java         # 주기적 입장 처리 (분산 락 적용)
│   └── controller/
│       ├── QueueApiController.java     # REST API (enter, rank, verify, monitor)
│       └── PageController.java         # Thymeleaf 페이지 라우팅
│
├── src/main/resources/
│   ├── application.yml                 # 환경변수 기반 설정
│   ├── templates/
│   │   ├── index.html                  # 메인 (예매하기 버튼)
│   │   ├── waiting.html                # 대기실 (순번 + 프로그레스 바)
│   │   ├── booking.html                # 예매 페이지 (좌석 선택)
│   │   └── monitor.html                # 실시간 모니터링 대시보드
│   └── static/
│       ├── css/style.css
│       └── js/queue.js                 # 2초 폴링 + 자동 리다이렉트
```

---

## 트러블슈팅 & 기술적 의사결정

### 1. ZPOPMIN을 선택한 이유
- `ZRANGEBYSCORE` + `ZREM`은 2개 명령 → 사이에 race condition 발생 가능
- `ZPOPMIN`은 단일 원자적 연산으로 조회와 삭제를 동시에 처리
- 멀티 인스턴스 환경에서도 동일 사용자가 중복 처리되지 않음

### 2. 활성 사용자 TTL 설계
- `queue:active` Set에 TTL을 직접 걸면 전체 사용자가 한번에 만료됨
- 사용자별 `queue:active:ttl:{token}` 키를 별도로 두어 개별 만료 관리
- `isActive()` 호출 시 TTL 키 존재 여부를 먼저 확인 → Lazy Cleanup 방식

### 3. 스케줄러 분산 락
- 단일 인스턴스에서는 문제 없지만, 3개 인스턴스에서 동일 스케줄러가 각각 실행
- Redis `SETNX` (setIfAbsent) + TTL로 간단한 분산 락 구현
- 전문 분산 락 라이브러리(Redisson) 없이도 충분한 수준의 동시성 제어 달성

### 4. Docker 이미지 호환성
- `eclipse-temurin:17-jre-alpine`은 Apple Silicon(ARM)에서 미지원
- `eclipse-temurin:17-jre`로 변경하여 멀티 아키텍처 지원

---

## 배운 점

1. **Redis 자료구조 선택의 중요성**: Sorted Set이 대기열에 최적화된 이유를 체감 (O(log N) 삽입/조회, 자동 정렬)
2. **Stateless 아키텍처**: Redis를 공유 상태 저장소로 사용하면 수평 확장이 매우 쉬움
3. **분산 환경의 복잡성**: 단일 인스턴스에서는 문제 없던 스케줄러가 멀티 인스턴스에서 race condition 유발
4. **부하 테스트의 가치**: Locust로 실제 트래픽을 시뮬레이션하여 병목 지점을 사전에 파악
5. **Docker Compose의 편리함**: 복잡한 멀티 서비스 환경을 한 줄(`docker compose up`)로 재현 가능

---

## 향후 개선 계획

- [ ] Server-Sent Events(SSE) 도입으로 폴링 → 실시간 Push 전환
- [ ] Redis Cluster 구성으로 Redis 단일 장애점 제거
- [ ] Redisson 기반 분산 락으로 업그레이드 (Watchdog 자동 연장)
- [ ] Spring Boot Actuator + Prometheus + Grafana 모니터링 체계
- [ ] RDB(MySQL) 연동으로 실제 좌석 예매/결제 로직 구현
- [ ] Rate Limiting 추가 (동일 IP 과다 요청 차단)
