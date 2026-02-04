# n8n 集成说明

此文档说明如何用 n8n 调用项目内的 HTTP 服务（由 `HttpServerMain` 提供）。

前提：
- 已启动本地 Java HTTP 服务：
  - 编译：
    `javac -encoding UTF-8 -d out src\com\david\tool\*.java`
  - 运行：
    `java -cp out com.david.tool.HttpServerMain`
- 如果在 `Config.API_KEY` 中设置了 API Key，请在请求中添加 HTTP 头 `X-API-Key`。

示例 1：n8n 使用 HTTP Request 节点直接 POST JSON（imageUrl）
- 节点配置：
  - Method: POST
  - URL: http://<your-host>:8080/process
  - Content Type: JSON
  - JSON/RAW Parameters：
    {
      "imageUrl": "https://example.com/your-map.png",
      "runAI": true
    }
  - Header: X-API-Key (如果配置了)

示例 2：POST Base64（适合从上一个节点直接读取图片并编码）
- Body(JSON)：
  {
    "imageBase64": "<base64-encoded-image>",
    "runAI": true
  }

响应（成功示例）
{
  "generatedAt": "20260203_121212",
  "outputDir": "D:\\path\\to\\workspace\\output_maps\\n8n_20260203_121212",
  "regions": [
    { "id": 0, "name": "齐", "centroid": [120, 200], "image": "images/region_00.png", "mask": "masks/mask_00.png", "pixels": 12345 },
    ...
  ]
}

n8n 工作流建议（简易）：
1. Trigger（例如 HTTP Trigger / Cron / Manual）
2. HTTP Request（调用上面的 /process 接口）
3. SplitInBatches（可按 regions 逐个处理）
4. 后续节点：将结果写入数据库、上传到云盘、或触发下一步游戏资源生成流程

注意事项：
- 当前服务是 MVP：JSON 解析、OCR、分割都为基本实现。为生产环境建议加上更严格的 JSON 验证、错误处理、鉴权与 SSL。
- 若需要我为你实现一个 n8n 专用的 Authentication Token 节点或返回更复杂的 job_id / 状态接口（polling / webhook 回调），告诉我需求，我会继续实现。