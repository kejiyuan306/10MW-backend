package com.nari._mw.pojo.dto.mqtt.transfer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置传输请求数据类
 * 用于处理配置文件传输的请求信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigTransferMetadata {

    /**
     * 操作类型，固定为"transfer_config"，表示传送事件请求
     */
    private String action;

    /**
     * 任务号，每次文件传输的唯一标识
     * 直到传输最终结束，任务号才结束生命周期
     */
    private String taskNo;

    /**
     * 传送文件名
     */
    private String name;

    /**
     * 文件总大小(bytes)
     */
    private long size;

    /**
     * 文件分片数
     */
    private int number;

    /**
     * 单个分片文件大小(bytes)
     */
    private int onesize;

    /**
     * 文件MD5值
     */
    @JsonProperty("MD5")
    private String md5;
}