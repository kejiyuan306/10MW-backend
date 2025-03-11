package com.nari._mw.pojo.dto.mqtt.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 配置传输响应数据类
 * 用于接收硬件设备对配置文件传输请求的响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigTransferAcknowledgeResponse {

    /**
     * 响应状态
     * 表示配置传输操作的处理结果
     */
    private String status;

    // 覆写equal方法
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigTransferAcknowledgeResponse that = (ConfigTransferAcknowledgeResponse) o;
        return Objects.equals(status, that.status);
    }
}