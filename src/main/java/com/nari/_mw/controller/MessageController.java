package com.nari._mw.controller;

import com.nari._mw.dto.MessageRequest;
import com.nari._mw.dto.MessageResponse;
import com.nari._mw.service.MQTTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.concurrent.CompletableFuture;

@CrossOrigin(originPatterns = "*", allowCredentials = "true")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MQTTService mqttService;

    @PostMapping
    public CompletableFuture<ResponseEntity<MessageResponse>> publishMessage(
            @Valid @RequestBody MessageRequest request) {

        return mqttService.publishMessage(request.getMessage())
                .thenApply(v -> ResponseEntity.ok(new MessageResponse("Message published successfully")))
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(new MessageResponse("Failed to publish message: " + ex.getMessage())));
    }
}