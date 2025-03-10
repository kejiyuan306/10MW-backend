package com.nari._mw.controller;

import com.nari._mw.dto.DeviceFunctionBlockRequest;
import com.nari._mw.dto.MessageResponse;
import com.nari._mw.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.concurrent.CompletableFuture;

@CrossOrigin(originPatterns = "*", allowCredentials = "true")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;

    @PostMapping("/function-blocks")
    public CompletableFuture<ResponseEntity<MessageResponse>> processFunctionBlocks(
            @Valid @RequestBody DeviceFunctionBlockRequest request) {
        return deviceService.processFunctionBlocks(request)
                .thenApply(v -> ResponseEntity.ok(
                        new MessageResponse("功能块成功发布至设备: " + request.getDeviceId())));
    }
}