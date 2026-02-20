package com.study.project.controller;

import com.study.project.dto.MonitorResponse;
import com.study.project.dto.QueueRankResponse;
import com.study.project.dto.QueueTokenResponse;
import com.study.project.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueApiController {

    private final QueueService queueService;

    @PostMapping("/enter")
    public ResponseEntity<QueueTokenResponse> enter() {
        return ResponseEntity.ok(queueService.enterQueue());
    }

    @GetMapping("/rank")
    public ResponseEntity<QueueRankResponse> rank(@RequestParam String token) {
        return ResponseEntity.ok(queueService.getRank(token));
    }

    @GetMapping("/verify")
    public ResponseEntity<Boolean> verify(@RequestParam String token) {
        return ResponseEntity.ok(queueService.isActive(token));
    }

    @GetMapping("/monitor")
    public ResponseEntity<MonitorResponse> monitor() {
        return ResponseEntity.ok(queueService.getMonitorData());
    }
}
