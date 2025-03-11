package com.nari._mw.util;

import com.nari._mw.pojo.dto.mqtt.transfer.ConfigTransferSlice;
import com.nari._mw.pojo.model.ConfigTransferData;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件切片工具类
 * 用于将文件切割成固定大小的切片并转换为ConfigTransferData对象
 */
@Slf4j
@UtilityClass
public class FileSlicerUtil {

    /**
     * 将指定文件按照给定的切片大小分割，并转化为ConfigTransferData对象
     *
     * @param filePath  文件路径
     * @param sliceSize 每个切片的大小（字节）
     * @return 包含文件切片信息的ConfigTransferData对象
     * @throws IOException 如果文件读取发生错误
     */
    public static ConfigTransferData sliceFile(String filePath, int sliceSize) throws IOException {
        Path path = Paths.get(filePath);
        validateFile(path);

        String taskNo = generateTaskNo();
        long fileSize = Files.size(path);
        List<ConfigTransferSlice> slices = readFileSlices(path, sliceSize, taskNo);

        log.debug("filePath: {} , fileSize: {} KB, sliceSize: {} KB, slices.size: {}",
                filePath, fileSize / 1024.0, sliceSize / 1024.0, slices.size());

        return new ConfigTransferData(
                fileSize,
                sliceSize,
                slices
        );
    }

    /**
     * 验证文件是否存在且是普通文件
     *
     * @param path 文件路径
     * @throws IOException 当文件不存在或不是普通文件时
     */
    private static void validateFile(Path path) throws IOException {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            log.error("文件不存在或不是有效的文件: {}", path);
            throw new IOException("文件不存在或不是有效的文件: " + path);
        }
    }

    /**
     * 生成唯一的任务编号
     *
     * @return 去除连字符的UUID字符串
     */
    private static String generateTaskNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 读取文件并按照指定大小分割为切片
     *
     * @param path      文件路径
     * @param sliceSize 切片大小
     * @param taskNo    任务编号
     * @return 切片列表
     * @throws IOException 当读取文件发生错误时
     */
    private static List<ConfigTransferSlice> readFileSlices(Path path, int sliceSize, String taskNo) throws IOException {
        List<ConfigTransferSlice> slices = new ArrayList<>();

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(sliceSize);
            int sliceNumber = 1;

            while (channel.read(buffer) > 0) {
                buffer.flip();

                byte[] data;
                if (buffer.limit() < sliceSize) {
                    // 如果读取的数据小于切片大小，创建合适大小的数组
                    data = new byte[buffer.limit()];
                    buffer.get(data);
                } else {
                    data = buffer.array().clone();
                }

                ConfigTransferSlice slice = ConfigTransferSlice.builder()
                        .taskNo(taskNo)
                        .number(sliceNumber++)
                        .data(data)
                        .build();

                slices.add(slice);

                // 清空缓冲区，准备下一次读取
                buffer.clear();
            }
        }

        return slices;
    }
}