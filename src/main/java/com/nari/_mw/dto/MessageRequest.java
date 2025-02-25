package com.nari._mw.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MessageRequest {
    @NotBlank(message = "Message cannot be empty")
    private String message;
}
