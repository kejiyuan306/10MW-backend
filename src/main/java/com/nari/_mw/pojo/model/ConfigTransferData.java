package com.nari._mw.pojo.model;

import com.nari._mw.pojo.dto.mqtt.transfer.ConfigTransferSlice;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigTransferData {
    /**
     * 任务号，每次文件传输的唯一标识
     */
    private String taskNo;

    /**
     * 传输的文件名
     */
    private String fileName;

    /**
     * 文件总大小
     */
    private long size;

    /**
     * 切片大小
     */
    private int sliceSize;


    /**
     * 切片数量
     */
    private int number;

    /**
     * 切片列表
     */
    private List<ConfigTransferSlice> slices;

    /**
     * 文件MD5值
     */
    private String md5;
}
