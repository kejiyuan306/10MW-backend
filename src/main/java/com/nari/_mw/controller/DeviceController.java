package com.nari._mw.controller;

import com.nari._mw.exception.MessageProcessingException;
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

    //Spring中使用CompletableFuture：该函数接受到一个CompletableFuture对象后，Spring 会在内部注册一个回调，等待 CompletableFuture 完成，然后再返回给客户端
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

        // 创建一个特殊的资源，在 try-with-resources 中自动释放信号量
        class SemaphoreResource implements AutoCloseable {
            @Override
            public void close() {
                publishConfigSemaphore.release();
            }
        }

        CompletableFuture<ResponseEntity<MessageResponse>> future = new CompletableFuture<>();

        try (SemaphoreResource resource = new SemaphoreResource()) {
            deviceService.publishConfig(request)
                    .orTimeout(2, TimeUnit.MINUTES)
                    .thenApply(v -> ResponseEntity.ok(
                            new MessageResponse("配置成功发布至设备: " + request.getDeviceId())))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            // 将异常传递给全局异常处理器
                            if (ex instanceof java.util.concurrent.TimeoutException) {
                                future.completeExceptionally(new MessageProcessingException("处理请求超时", ex));
                            } else {
                                future.completeExceptionally(ex);
                            }
                        } else {
                            future.complete(result);
                        }
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

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

        CompletableFuture<ResponseEntity<MessageResponse>> future = new CompletableFuture<>();

        try {
            deviceService.processFunctionBlocks(request)
                    .orTimeout(2, TimeUnit.MINUTES)
                    .thenApply(v -> ResponseEntity.ok(
                            new MessageResponse("功能块成功发布至设备: " + request.getDeviceId())))
                    .whenComplete((result, ex) -> {
                        processFunctionBlocksSemaphore.release();
                        if (ex != null) {
                            // 将异常传递给全局异常处理器
                            if (ex instanceof java.util.concurrent.TimeoutException) {
                                future.completeExceptionally(new MessageProcessingException("处理请求超时", ex));
                            } else {
                                future.completeExceptionally(ex);
                            }
                        } else {
                            future.complete(result);
                        }
                    });
        } catch (Exception e) {
            processFunctionBlocksSemaphore.release();
            future.completeExceptionally(e);
        }

        return future;
    }
}