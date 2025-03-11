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
    private double size;
    private double sliceSize;
    private List<ConfigTransferSlice> slices;
}
