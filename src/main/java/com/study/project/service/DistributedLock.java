package com.study.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis SETNX 기반 분산 락 획득
     * @return true if lock acquired
     */
    public boolean tryLock(String key, Duration ttl) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key, "locked", ttl);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 락 해제
     */
    public void unlock(String key) {
        redisTemplate.delete(key);
    }
}
