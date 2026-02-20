package com.study.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueTokenResponse {
    private String token;
    private long enteredAt;
}
