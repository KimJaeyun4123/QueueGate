package com.study.project.service;

import com.study.project.dto.QueueRankResponse;
import com.study.project.dto.QueueTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.study.project.dto.MonitorResponse;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String WAIT_KEY = "queue:wait";
    private static final String ACTIVE_KEY = "queue:active";
    private static final String ACTIVE_TTL_PREFIX = "queue:active:ttl:";
    private static final String COUNTER_KEY = "queue:counter";

    private final StringRedisTemplate redisTemplate;

    private final AtomicLong lastProcessedCount = new AtomicLong(0);
    private final AtomicLong lastThroughputTime = new AtomicLong(System.currentTimeMillis());
    private volatile double currentThroughput = 0.0;

    @Value("${queue.batch-size:10}")
    private int batchSize;

    @Value("${queue.active-ttl:300}")
    private long activeTtlSeconds;

    @Value("${instance.id:app-local}")
    private String instanceId;

    @Value("${queue.schedule-rate:3000}")
    private long scheduleRateMs;

    /**
     * 대기열 진입 - ZADD queue:wait {timestamp} {uuid}
     */
    public QueueTokenResponse enterQueue() {
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(WAIT_KEY, token, now);
        log.info("대기열 진입: token={}", token);
        return new QueueTokenResponse(token, now);
    }

    /**
     * 대기 순위 조회 - ZRANK로 순위, SISMEMBER로 입장 여부 확인
     */
    public QueueRankResponse getRank(String token) {
        // 이미 활성 사용자인지 확인
        Boolean isActive = redisTemplate.opsForSet().isMember(ACTIVE_KEY, token);
        if (Boolean.TRUE.equals(isActive)) {
            return QueueRankResponse.builder()
                    .admitted(true)
                    .rank(null)
                    .totalWaiting(0)
                    .estimatedWaitSeconds(0)
                    .build();
        }

        // 대기열에서 순위 조회
        Long rank = redisTemplate.opsForZSet().rank(WAIT_KEY, token);
        Long totalWaiting = redisTemplate.opsForZSet().zCard(WAIT_KEY);

        if (rank == null) {
            return QueueRankResponse.builder()
                    .admitted(false)
                    .rank(null)
                    .totalWaiting(totalWaiting != null ? totalWaiting : 0)
                    .estimatedWaitSeconds(0)
                    .build();
        }

        // 예상 대기 시간: (rank / batchSize) * scheduleRate
        long estimatedWait = ((rank / batchSize) + 1) * (scheduleRateMs / 1000);

        return QueueRankResponse.builder()
                .admitted(false)
                .rank(rank + 1) // 0-based -> 1-based
                .totalWaiting(totalWaiting != null ? totalWaiting : 0)
                .estimatedWaitSeconds(estimatedWait)
                .build();
    }

    /**
     * 상위 N명 입장 처리 - ZPOPMIN queue:wait {batchSize}
     */
    public long admitUsers() {
        Set<ZSetOperations.TypedTuple<String>> popped =
                redisTemplate.opsForZSet().popMin(WAIT_KEY, batchSize);

        if (popped == null || popped.isEmpty()) {
            return 0;
        }

        for (ZSetOperations.TypedTuple<String> entry : popped) {
            String token = entry.getValue();
            redisTemplate.opsForSet().add(ACTIVE_KEY, token);
            redisTemplate.opsForValue().set(
                    ACTIVE_TTL_PREFIX + token, "1",
                    Duration.ofSeconds(activeTtlSeconds)
            );
            redisTemplate.opsForValue().increment(COUNTER_KEY);
            log.info("입장 허용: token={}", token);
        }

        return popped.size();
    }

    /**
     * 토큰이 active 상태인지 확인
     */
    public boolean isActive(String token) {
        Boolean ttlExists = redisTemplate.hasKey(ACTIVE_TTL_PREFIX + token);
        if (Boolean.FALSE.equals(ttlExists)) {
            redisTemplate.opsForSet().remove(ACTIVE_KEY, token);
            return false;
        }
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(ACTIVE_KEY, token)
        );
    }

    /**
     * 현재 대기 인원 수
     */
    public long getWaitingCount() {
        Long size = redisTemplate.opsForZSet().zCard(WAIT_KEY);
        return size != null ? size : 0;
    }

    /**
     * 현재 활성 사용자 수
     */
    public long getActiveCount() {
        Long size = redisTemplate.opsForSet().size(ACTIVE_KEY);
        return size != null ? size : 0;
    }

    /**
     * 총 입장 처리 수
     */
    public long getTotalProcessed() {
        String val = redisTemplate.opsForValue().get(COUNTER_KEY);
        return val != null ? Long.parseLong(val) : 0;
    }

    /**
     * 초당 처리량 계산 및 모니터링 데이터 반환
     */
    public MonitorResponse getMonitorData() {
        long totalProcessed = getTotalProcessed();
        long now = System.currentTimeMillis();

        long prevCount = lastProcessedCount.getAndSet(totalProcessed);
        long prevTime = lastThroughputTime.getAndSet(now);
        long elapsed = now - prevTime;

        if (elapsed > 0 && totalProcessed >= prevCount) {
            currentThroughput = (totalProcessed - prevCount) * 1000.0 / elapsed;
        } else {
            currentThroughput = 0.0;
        }

        return MonitorResponse.builder()
                .waitingCount(getWaitingCount())
                .activeCount(getActiveCount())
                .totalProcessed(totalProcessed)
                .throughputPerSec(Math.round(currentThroughput * 100.0) / 100.0)
                .timestamp(now)
                .instanceId(instanceId)
                .build();
    }
}
