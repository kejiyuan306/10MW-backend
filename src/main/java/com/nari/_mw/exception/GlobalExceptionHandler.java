package com.nari._mw.exception;

import com.nari._mw.pojo.dto.response.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

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
}