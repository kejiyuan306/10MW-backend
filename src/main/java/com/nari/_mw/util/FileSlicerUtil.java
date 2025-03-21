package com.nari._mw.util;

import com.nari._mw.exception.FileSliceException;
import com.nari._mw.pojo.dto.mqtt.transfer.ConfigTransferSlice;
import com.nari._mw.pojo.model.ConfigTransferData;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    public static ConfigTransferData sliceFile(String filePath, int sliceSize) {
        try {
            Path path = Paths.get(filePath);
            validateFile(path);

            String fileName = path.getFileName().toString();

            String taskNo = generateTaskNo();
            long fileSize = Files.size(path);
            String md5 = calculateMD5(filePath);

            List<ConfigTransferSlice> slices = readFileSlices(path, sliceSize, taskNo);

            log.debug("文件路径: {} , 文件大小: {} KB, 切片大小: {} KB, 切片数量: {}",
                    filePath, fileSize / 1024.0, sliceSize / 1024.0, slices.size());

            return new ConfigTransferData(
                    taskNo,
                    fileName,
                    fileSize,
                    sliceSize,
                    slices.size(),
                    slices,
                    md5
            );
        } catch (IOException e) {
            log.error("文件切片处理出错: {}", e.getMessage(), e);
            throw new FileSliceException("文件切片处理失败", e);
        }
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

    /**
     * 计算文件的MD5哈希值
     *
     * @param filePath 文件路径
     * @return 文件的MD5哈希值（32位十六进制字符串）
     * @throws IOException 如果文件读取发生错误
     */
    public static String calculateMD5(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        validateFile(path);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            // 使用缓冲区分块读取文件以处理大文件
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; // 8KB缓冲区
                int bytesRead;

                while ((bytesRead = is.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }

            // 将字节数组转换为十六进制字符串
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }

            log.debug("文件 {} 的MD5值: {}", filePath, sb.toString());
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算MD5时出错: {}", e.getMessage());
            throw new RuntimeException("计算MD5时出错", e);
        }
    }
}