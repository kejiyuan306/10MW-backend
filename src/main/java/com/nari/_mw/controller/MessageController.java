package com.nari._mw.controller;

import com.nari._mw.dto.MessageRequest;
import com.nari._mw.service.MQTTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MQTTService mqttService;

    @PostMapping
    public ResponseEntity<String> publishMessage(@Valid @RequestBody MessageRequest request) {
        mqttService.publishMessage(request.getMessage());
        return ResponseEntity.ok("Message queued for publishing");
    }
}
