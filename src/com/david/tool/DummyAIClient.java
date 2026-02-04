package com.david.tool;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * 简易的 AI 占位实现：
 * 1) 尝试使用系统已安装的 tesseract（需在 PATH 中或在 Config 中配置）做 OCR；
 * 2) 若没有 tesseract，则返回基于随机 UUID 的占位名称（用户可手动修改或替换实现）。
 */
public class DummyAIClient implements AIClient {
    @Override
    public String identifyLabel(BufferedImage regionImage) throws Exception {
        // 尝试使用 tesseract（如果可用）
        try {
            File tmp = File.createTempFile("region_", ".png");
            ImageIO.write(regionImage, "png", tmp);
            ProcessBuilder pb = new ProcessBuilder("tesseract", tmp.getAbsolutePath(), "stdout", "-l", "chi_sim");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            InputStream in = p.getInputStream();
            byte[] out = in.readAllBytes();
            p.waitFor();
            String text = new String(out, StandardCharsets.UTF_8).trim();
            Files.deleteIfExists(tmp.toPath());
            if (!text.isEmpty()) {
                // 取第一行作为名称
                String[] lines = text.split("\\r?\\n");
                return lines[0].trim();
            }
        } catch (Exception e) {
            // 忽略错误，退化到占位名
        }
        return "region_" + UUID.randomUUID().toString().substring(0, 6);
    }
}
