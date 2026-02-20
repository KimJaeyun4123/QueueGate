package com.study.project.scheduler;

import com.study.project.service.DistributedLock;
import com.study.project.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private static final String LOCK_KEY = "scheduler:lock";

    private final QueueService queueService;
    private final DistributedLock distributedLock;

    @Value("${instance.id:unknown}")
    private String instanceId;

    @Scheduled(fixedRateString = "${queue.schedule-rate:1000}")
    public void admitFromQueue() {
        // 분산 락 획득 시도 (3초 TTL)
        if (!distributedLock.tryLock(LOCK_KEY, Duration.ofSeconds(3))) {
            return; // 다른 인스턴스가 처리 중
        }

        try {
            long admitted = queueService.admitUsers();
            if (admitted > 0) {
                long remaining = queueService.getWaitingCount();
                log.info("[{}] 입장 처리: {}명 입장, 대기 {}명 남음", instanceId, admitted, remaining);
            }
        } finally {
            distributedLock.unlock(LOCK_KEY);
        }
    }
}
