package com.nari._mw.pojo.dto.mqtt.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置传输数据切片类
 * 用于处理配置文件传输过程中的数据切片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigTransferSlice {

    /**
     * 任务号，每次文件传输的唯一标识
     */
    private String taskNo;

    /**
     * 切片序号
     * 收到控制器回复当前切片任务传送完成(status=success)后，进行下一个切片发送
     */
    private int number;

    /**
     * 二进制数据
     * 当前切片的实际数据内容
     */
    private byte[] data;
}