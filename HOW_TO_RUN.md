# QueueGate 구동 및 테스트 방법

## 1. Docker 클러스터 실행 (Nginx + 3 인스턴스 + Redis)

# 프로젝트 빌드
cd /Users/jaeyunkim/Documents/QueueGate
./gradlew bootJar

# Docker 클러스터 시작
docker compose up -d --build

# 상태 확인
docker compose ps


- 모니터링: http://localhost/monitor
- 메인 페이지: http://localhost
- 대기실: http://localhost/waiting?token=xxx

## 2. 단일 인스턴스 실행 (개발/디버깅용)

# Redis 먼저 실행
redis-server &

# Spring Boot 실행
./gradlew bootRun

- 접속: http://localhost:8080

## 3. Locust 부하 테스트

### 3-1. UI 모드 (권장) — monitor 페이지에서 직접 제어

# Locust 웹 서버 실행 (포트 8089)
locust -f locustfile.py --host=http://localhost --web-port=8089

- 이후 http://localhost/monitor 에서 바로 시작/중지 가능
- 엔드포인트별 RPS · 응답시간 · 실패율을 모니터 화면에서 실시간 확인

### 3-2. Headless 모드 (CLI 자동화)

# 10,000명 테스트 (Docker 클러스터 대상, 포트 80)
locust -f locustfile.py --host=http://localhost --headless \
  -u 10000 -r 500 --run-time 60s

# 단일 인스턴스 대상 (포트 8080)
locust -f locustfile.py --host=http://localhost:8080 --headless \
  -u 10000 -r 500 --run-time 60s

## 4. Redis 초기화 (테스트 전 권장)

redis-cli FLUSHALL

## 5. 종료

# Docker 클러스터 종료
docker compose down

# 로컬 Redis 종료
redis-cli shutdown

## 순서 요약

빌드 → Redis 초기화 → Docker 클러스터 시작 → Locust 웹 서버 실행 → 모니터링 페이지(http://localhost/monitor)에서 테스트 시작
