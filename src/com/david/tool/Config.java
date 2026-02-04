package com.david.tool;

/**
 * 全局配置点（目前仅示例），可以扩展为读取配置文件或环境变量。
 */
public class Config {
    // 可配置为 tesseract 可执行路径
    public static String TESSERACT_PATH = "tesseract";

    // 可配置为外部 AI 服务的 HTTP endpoint
    public static String AI_HTTP_ENDPOINT = "";

    // HTTP 服务配置（用于 n8n 接入）
    public static int SERVER_PORT = 8080;
    // 若不为空，请求需包含 Header: X-API-Key: <API_KEY>
    public static String API_KEY = "";
}
