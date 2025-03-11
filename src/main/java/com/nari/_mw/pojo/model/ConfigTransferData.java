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
     * 文件总大小
     */
    private long size;
    /**
     * 切片大小
     */
    private int sliceSize;
    /**
     * 切片列表
     */
    private List<ConfigTransferSlice> slices;
}
