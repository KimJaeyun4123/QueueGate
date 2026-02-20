package com.study.project.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonitorResponse {
    private long waitingCount;
    private long activeCount;
    private long totalProcessed;
    private double throughputPerSec;
    private long timestamp;
    private String instanceId;
}
