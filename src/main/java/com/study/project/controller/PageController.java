package com.study.project.controller;

import com.study.project.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final QueueService queueService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/waiting")
    public String waiting(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "waiting";
    }

    @GetMapping("/monitor")
    public String monitor() {
        return "monitor";
    }

    @GetMapping("/booking")
    public String booking(@RequestParam String token, Model model) {
        if (!queueService.isActive(token)) {
            return "redirect:/";
        }
        model.addAttribute("token", token);
        return "booking";
    }
}
