package com.study.project.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueueRankResponse {
    private Long rank;
    private long totalWaiting;
    private long estimatedWaitSeconds;
    private boolean admitted;
}
