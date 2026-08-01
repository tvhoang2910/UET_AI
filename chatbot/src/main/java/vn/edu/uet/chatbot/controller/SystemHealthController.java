package vn.edu.uet.chatbot.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uet.chatbot.dto.SystemHealthResponse;
import vn.edu.uet.chatbot.service.SystemHealthService;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> health() {
        return ResponseEntity.ok(systemHealthService.health());
    }
}
