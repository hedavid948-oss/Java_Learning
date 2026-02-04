package com.david.tool;

import java.awt.image.BufferedImage;

/**
 * AI 接口：提供从区域图片识别名称/标签的能力。
 * 实现可替换为调用云端视觉模型或本地 OCR（如 Tesseract）。
 */
public interface AIClient {
    /**
     * 返回识别到的文字标签（可能为空）。
     */
    String identifyLabel(BufferedImage regionImage) throws Exception;
}
