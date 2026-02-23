# QueueGate

> 수만 명이 동시에 접속하는 티켓팅 상황에서 서버 과부하 없이 안정적으로 대기열을 관리하는 **Virtual Waiting Room(가상 대기실)** 시스템

![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.2-6DB33F?style=flat-square&logo=springboot)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis)
![Nginx](https://img.shields.io/badge/Nginx-Round_Robin-009639?style=flat-square&logo=nginx)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)

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
     ┌────────▼──────┐ ┌─────▼──────┐ ┌────▼───────┐
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

## 핵심 기능

### Redis Sorted Set 기반 대기열

| Key | Type | 용도 |
|-----|------|------|
| `queue:wait` | Sorted Set | 대기열 (Score=진입시각, Member=UUID) |
| `queue:active` | Set | 입장 허용된 사용자 집합 |
| `queue:active:ttl:{token}` | String (TTL) | 사용자별 만료 관리 (5분) |
| `queue:counter` | String | 총 입장 처리 수 (통계용) |

### 대기열 플로우

```
[사용자] 예매하기 클릭
    │
    ▼
[POST /api/queue/enter] → 토큰(UUID) 발급
    │
    ▼
[대기실] 2초 간격 폴링으로 순위 조회
    │
    ▼
[스케줄러] 1초마다 100명씩 ZPOPMIN → 입장 허용
    │
    ▼
[예매 페이지] 토큰 검증 후 입장
```

### Redis 분산 락

3개 인스턴스가 동일한 스케줄러를 실행할 때 중복 처리 방지를 위해 `SETNX` 기반 분산 락 적용.

```java
redisTemplate.opsForValue().setIfAbsent("scheduler:lock", "locked", Duration.ofSeconds(3));
```

---

## 빠른 시작

### 요구 사항

- Java 17
- Docker & Docker Compose
- (부하 테스트 시) Python + Locust

### 실행

```bash
# 1. 빌드
./gradlew bootJar

# 2. Redis 초기화 (테스트 전 권장)
redis-cli FLUSHALL

# 3. Docker 클러스터 시작 (Nginx + App×3 + Redis)
docker compose up -d --build
```

| 서비스 | URL |
|--------|-----|
| 메인 페이지 | http://localhost |
| 모니터링 대시보드 | http://localhost/monitor |

### 개발 모드 (단일 인스턴스)

```bash
# Redis 실행
redis-server &

# Spring Boot 실행
./gradlew bootRun
```

접속: http://localhost:8080

### 부하 테스트 (Locust)

```bash
# Locust 웹 서버 실행
locust -f locustfile.py --host=http://localhost --web-port=8089
```

이후 http://localhost/monitor 에서 바로 시작/중지 가능

```bash
# Headless 모드 (10,000명)
locust -f locustfile.py --host=http://localhost --headless -u 10000 -r 500 --run-time 60s
```

### 종료

```bash
docker compose down
```

---

## 부하 테스트 결과

**환경:** MacBook Pro (Apple Silicon) / Nginx + Spring Boot × 3 + Redis (Docker)

### 단일 인스턴스 (10,000명)

| 항목 | 결과 |
|------|------|
| 총 요청 수 | 290,146건 |
| 실패율 | **0.27%** |
| 초당 처리량 | **~2,937 RPS** |
| 순위 조회 중간값 | 380ms |

### 로드 밸런싱 3개 인스턴스 (10,000명)

| 항목 | 결과 |
|------|------|
| 총 요청 수 | 84,954건 |
| 실패율 | **0.14%** |
| 초당 처리량 | **~1,945 RPS** |
| 10,000명 전원 입장 | **60초 이내** |

**API별 응답 시간**

| API | 중간값 | P95 | P99 |
|-----|--------|-----|-----|
| 대기열 진입 (POST) | 46ms | 270ms | 620ms |
| 순위 조회 (GET) | 45ms | 100ms | 290ms |
| 예매 페이지 (GET) | 40ms | 81ms | 110ms |
| 토큰 검증 (GET) | 38ms | 65ms | 94ms |

---

## 프로젝트 구조

```
QueueGate/
├── Dockerfile
├── docker-compose.yml
├── nginx/nginx.conf
├── locustfile.py
└── src/main/java/com/study/project/
    ├── config/
    │   ├── RedisConfig.java        # StringRedisTemplate 빈
    │   └── InstanceFilter.java     # X-Instance-Id 응답 헤더
    ├── service/
    │   ├── QueueService.java       # 핵심 대기열 로직
    │   └── DistributedLock.java    # Redis SETNX 분산 락
    ├── scheduler/
    │   └── QueueScheduler.java     # 주기적 입장 처리
    └── controller/
        ├── QueueApiController.java # REST API
        └── PageController.java     # Thymeleaf 페이지 라우팅
```

---

## 트러블슈팅 & 기술적 의사결정

**ZPOPMIN을 선택한 이유**
- `ZRANGEBYSCORE` + `ZREM`은 2개 명령 → race condition 발생 가능
- `ZPOPMIN`은 단일 원자적 연산으로 조회와 삭제를 동시에 처리

**활성 사용자 TTL 설계**
- `queue:active` Set에 TTL을 직접 걸면 전체 사용자가 한번에 만료됨
- 사용자별 `queue:active:ttl:{token}` 키를 별도로 두어 개별 만료 관리 (Lazy Cleanup)

**Docker 이미지 호환성**
- `eclipse-temurin:17-jre-alpine`은 Apple Silicon(ARM)에서 미지원 → `eclipse-temurin:17-jre`로 변경

---

## 향후 개선 계획

- [ ] Server-Sent Events(SSE) 도입으로 폴링 → 실시간 Push 전환
- [ ] Redis Cluster 구성으로 단일 장애점 제거
- [ ] Redisson 기반 분산 락으로 업그레이드
- [ ] Spring Boot Actuator + Prometheus + Grafana 모니터링 체계
- [ ] Rate Limiting 추가 (동일 IP 과다 요청 차단)
