package com.david.tool;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 简易 HTTP 服务，暴露给 n8n 使用：
 * - GET  /health  -> 简单健康检查
 * - POST /process -> 上传图片或传 imageUrl/imageBase64 的 JSON，返回处理结果 JSON 并在服务器生成输出包
 */
public class HttpServerMain {
    public static void main(String[] args) throws Exception {
        int port = Config.SERVER_PORT;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/process", new ProcessHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        System.out.println("n8n HTTP service started on port " + port);
        server.start();
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String resp = "ok";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp.getBytes(StandardCharsets.UTF_8)); }
        }
    }

    static class ProcessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, jsonErr("Method Not Allowed"));
                return;
            }

            // 简单鉴权：若 Config.API_KEY 不为空，则要求头部 X-API-Key 匹配
            Headers reqh = exchange.getRequestHeaders();
            String apiKey = reqh.getFirst("X-API-Key");
            if (Config.API_KEY != null && !Config.API_KEY.isEmpty()) {
                if (apiKey == null || !apiKey.equals(Config.API_KEY)) {
                    sendJson(exchange, 401, jsonErr("Unauthorized"));
                    return;
                }
            }

            String contentType = reqh.getFirst("Content-Type");
            byte[] body = readAll(exchange.getRequestBody());
            BufferedImage img = null;
            boolean runAI = true;

            try {
                if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
                    String s = new String(body, StandardCharsets.UTF_8);
                    String imageUrl = extractJsonString(s, "imageUrl");
                    String imageBase64 = extractJsonString(s, "imageBase64");
                    String runAIStr = extractJsonString(s, "runAI");
                    if (runAIStr != null) {
                        runAI = runAIStr.equalsIgnoreCase("true") || runAIStr.equals("1");
                    }
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        img = fetchImageFromUrl(imageUrl);
                    } else if (imageBase64 != null && !imageBase64.isEmpty()) {
                        byte[] bytes = Base64.getDecoder().decode(imageBase64);
                        img = ImageIO.read(new ByteArrayInputStream(bytes));
                    } else {
                        sendJson(exchange, 400, jsonErr("No image provided"));
                        return;
                    }
                } else if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    img = ImageIO.read(new ByteArrayInputStream(body));
                } else if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("octet-stream")) {
                    img = ImageIO.read(new ByteArrayInputStream(body));
                } else {
                    sendJson(exchange, 400, jsonErr("Unsupported Content-Type. Use application/json with imageUrl/imageBase64 or upload raw image binary."));
                    return;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                sendJson(exchange, 500, jsonErr("Failed to read image: " + ex.getMessage()));
                return;
            }

            if (img == null) {
                sendJson(exchange, 400, jsonErr("Unable to decode image"));
                return;
            }

            try {
                List<Region> regions = ImageProcessor.segmentByColor(img, 32);
                if (runAI) {
                    AIClient ai = new DummyAIClient();
                    for (Region r : regions) {
                        try { r.name = ai.identifyLabel(r.image); } catch (Exception ignored) {}
                    }
                }
                // 导出
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File outDir = new File("output_maps/n8n_" + stamp);
                File imagesDir = new File(outDir, "images");
                File masksDir = new File(outDir, "masks");
                imagesDir.mkdirs(); masksDir.mkdirs();
                ImageIO.write(img, "png", new File(imagesDir, "original.png"));

                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append(String.format("  \"generatedAt\": \"%s\",\n", stamp));
                json.append(String.format("  \"outputDir\": \"%s\",\n", escape(outDir.getAbsolutePath())));
                json.append("  \"regions\": [\n");
                for (int i = 0; i < regions.size(); i++) {
                    Region r = regions.get(i);
                    String imgName = String.format("region_%02d.png", i);
                    String maskName = String.format("mask_%02d.png", i);
                    ImageIO.write(r.image, "png", new File(imagesDir, imgName));
                    ImageIO.write(r.mask, "png", new File(masksDir, maskName));
                    json.append("    {");
                    json.append(String.format("\"id\": %d, \"name\": \"%s\", \"centroid\": [%d, %d], \"image\": \"images/%s\", \"mask\": \"masks/%s\", \"pixels\": %d", i, r.name == null ? "" : escape(r.name), (int)r.centroidX, (int)r.centroidY, imgName, maskName, r.pixelCount));
                    json.append("}");
                    if (i < regions.size() - 1) json.append(",\n");
                    else json.append("\n");
                }
                json.append("  ]\n}");

                sendJson(exchange, 200, json.toString());
            } catch (Exception ex) {
                ex.printStackTrace();
                sendJson(exchange, 500, jsonErr("Processing failed: " + ex.getMessage()));
            }
        }

        private static byte[] readAll(InputStream in) throws IOException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) bout.write(buf, 0, r);
            return bout.toByteArray();
        }

        private static BufferedImage fetchImageFromUrl(String urlStr) throws IOException {
            URL url = new URL(urlStr);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "MapTool/1.0");
            try (InputStream in = c.getInputStream()) {
                return ImageIO.read(in);
            }
        }

        private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }

        private static String jsonErr(String message) {
            return "{\"error\": \"" + escape(message) + "\"}";
        }

        // 极其简陋的 JSON 提取（仅用于 MVP）
        private static String extractJsonString(String json, String key) {
            String k1 = '"' + key + '"' + ":";
            int idx = json.indexOf('"' + key + '"');
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            if (colon < 0) return null;
            int start = colon + 1;
            // 跳过空白
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
            if (start >= json.length()) return null;
            char ch = json.charAt(start);
            if (ch == '"') {
                int end = json.indexOf('"', start + 1);
                if (end > start) return json.substring(start + 1, end);
            } else {
                // 非字符串（如 true/false/number）
                int end = start;
                while (end < json.length() && !Character.isWhitespace(json.charAt(end)) && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
                return json.substring(start, end);
            }
            return null;
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
