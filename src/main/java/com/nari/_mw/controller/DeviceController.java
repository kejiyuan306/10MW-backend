package com.nari._mw.controller;

import com.nari._mw.pojo.dto.request.DeviceFunctionBlockRequest;
import com.nari._mw.pojo.dto.request.PublishConfigRequest;
import com.nari._mw.pojo.dto.response.MessageResponse;
import com.nari._mw.service.DeviceService;
import com.nari._mw.service.TestDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@CrossOrigin(originPatterns = "*", allowCredentials = "true")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;
    private final TestDeviceService testDeviceService;
    private final Semaphore publishConfigSemaphore = new Semaphore(1);
    private final Semaphore processFunctionBlocksSemaphore = new Semaphore(1);

    @GetMapping("/test")
    public CompletableFuture<String> test() {
        return testDeviceService.test();
    }

    @GetMapping("/test-interact")
    public CompletableFuture<ResponseEntity<MessageResponse>> testInteract() {
        return testDeviceService.testInteract().orTimeout(10, TimeUnit.MINUTES)
                .thenApply(v -> ResponseEntity.ok(
                        new MessageResponse("testInteract: 交互成功")));
    }

    //Spring中使用CompletableFuture：该函数接受到一个CompletableFuture对象后，Spring 会在内部注册一个回调，等待 CompletableFuture 完成，然后再返回给客户端
    @PostMapping("/function-blocks")
    public CompletableFuture<ResponseEntity<MessageResponse>> processFunctionBlocks(
            @Valid @RequestBody DeviceFunctionBlockRequest request) {
        // 如果无法获取信号量，直接返回繁忙信息
        if (!processFunctionBlocksSemaphore.tryAcquire()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new MessageResponse("系统正在处理其他配置请求，请稍后再试"))
            );
        }

        // 使用try-catch确保无论如何都会释放信号量
        try {
            return deviceService.processFunctionBlocks(request)
                    .orTimeout(2, TimeUnit.MINUTES)
                    .thenApply(v -> ResponseEntity.ok(
                            new MessageResponse("功能块成功发布至设备: " + request.getDeviceId())))
                    .whenComplete((result, ex) -> processFunctionBlocksSemaphore.release());
        } catch (Exception e) {
            // 发生异常时释放信号量并重新抛出异常由GlobalExceptionHandler处理
            processFunctionBlocksSemaphore.release();
            throw e;
        }
    }

    @PostMapping("/publish-config")
    public CompletableFuture<ResponseEntity<MessageResponse>> publishConfig(
            @Valid @RequestBody PublishConfigRequest request) {
        // 如果无法获取信号量，直接返回繁忙信息
        if (!publishConfigSemaphore.tryAcquire()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(new MessageResponse("系统正在处理其他配置请求，请稍后再试"))
            );
        }

        try {
            return deviceService.publishConfig(request)
                    .orTimeout(2, TimeUnit.MINUTES)
                    .thenApply(v -> ResponseEntity.ok(
                            new MessageResponse("配置成功发布至设备: " + request.getDeviceId())))
                    .whenComplete((result, ex) -> publishConfigSemaphore.release());
        } catch (Exception e) {
            publishConfigSemaphore.release();
            throw e;
        }
    }
}