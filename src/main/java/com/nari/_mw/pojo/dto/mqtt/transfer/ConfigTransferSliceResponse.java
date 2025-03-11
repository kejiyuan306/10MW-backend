package com.nari._mw.pojo.dto.mqtt.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置切片传输响应类
 * 用于接收设备对配置文件切片传输的响应状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigTransferSliceResponse {

    /**
     * 任务号，每次文件传输的唯一标识
     */
    private String taskNo;

    /**
     * 切片序号
     * 指示当前响应所对应的切片编号
     */
    private int number;

    /**
     * 响应状态
     * success: 传输成功，HMI接下来发下一个切片
     * failure: 传送失败，HMI接下来重新发送原来切片
     */
    private String status;
}