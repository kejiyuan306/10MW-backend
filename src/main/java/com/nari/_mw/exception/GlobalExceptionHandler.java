package com.nari._mw.exception;

import com.nari._mw.pojo.dto.response.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(java.util.concurrent.CompletionException.class)
    public ResponseEntity<MessageResponse> handleCompletionException(java.util.concurrent.CompletionException ex) {
        log.error("异步操作执行失败", ex);

        // 提取原始异常
        Throwable cause = ex.getCause();

        if (cause instanceof MQTTValidationException) {
            return handleMQTTValidationException((MQTTValidationException) cause);
        } else if (cause instanceof MessageProcessingException) {
            return handleMessageProcessingException((MessageProcessingException) cause);
        } else if (cause instanceof DeviceInteractionException) {
            return handleDeviceInteractionException((DeviceInteractionException) cause);
        } else if (cause instanceof FileSliceException) {
            return handleFileSliceException((FileSliceException) cause);
        } else {
            // 处理其他未知异常
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("系统处理请求时发生错误: " + (cause != null ? cause.getMessage() : ex.getMessage())));
        }
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<MessageResponse> handleUnexpectedException(Throwable ex) {
        log.error("系统发生未预期的异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("系统发生未预期的异常: " + ex.getMessage()));
    }

    @ExceptionHandler(MQTTValidationException.class)
    public ResponseEntity<MessageResponse> handleMQTTValidationException(MQTTValidationException ex) {
        log.error("MQTT连接参数验证失败", ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(MessageProcessingException.class)
    public ResponseEntity<MessageResponse> handleMessageProcessingException(MessageProcessingException ex) {
        log.error("消息处理失败", ex);
        return ResponseEntity.badRequest()
                .body(new MessageResponse("处理失败: " + ex.getMessage()));
    }

    @ExceptionHandler(DeviceInteractionException.class)
    public ResponseEntity<MessageResponse> handleDeviceInteractionException(DeviceInteractionException ex) {
        log.error("设备交互失败: 设备ID={}", ex.getDeviceId(), ex);
        return ResponseEntity.status(500)
                .body(new MessageResponse("设备交互失败: " + ex.getMessage() + ", 设备ID: " + ex.getDeviceId()));
    }

    @ExceptionHandler(FileSliceException.class)
    public ResponseEntity<MessageResponse> handleFileSliceException(FileSliceException e) {
        log.error("文件切片处理异常", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("文件切片处理异常: " + e.getMessage()));
    }
}