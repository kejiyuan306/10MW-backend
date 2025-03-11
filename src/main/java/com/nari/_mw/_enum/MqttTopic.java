package com.nari._mw._enum;

import lombok.Getter;

/**
 * 表示用于设备配置传输的MQTT主题的枚举
 */
@Getter
public enum MqttTopic {

    // HMI向控制器发送初始配置传输请求
    CONFIG_TRANSFER_REQUEST("config"),

    // 控制器向HMI发送准备就绪确认
    CONFIG_TRANSFER_ACK("config/ack"),

    // HMI向控制器发送文件数据块
    FILE_DATA_CHUNK("file/data"),

    // 控制器向HMI发送数据块确认
    FILE_CHUNK_ACK("file/dataack"),

    // 控制器向HMI发送最终文件验证结果
    FILE_VERIFICATION_RESULT("file/ack");

    private final String topic;

    MqttTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 获取带设备ID的完整主题路径
     *
     * @param deviceId 设备ID
     * @return 完整的主题路径
     */
    public String getTopicForDevice(String deviceId) {
        return deviceId + "/" + topic;
    }
}