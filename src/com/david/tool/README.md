# Map → GameMap Tool (MVP)

⚙️ 简介

这是 `com.david.tool` 下的一个最小可用原型：把现实地图图片通过**颜色分割**转成游戏可用的资源包（包含图片和 `regions.json`），并提供一个 AI 接口（可用 tesseract OCR 或替换为 HTTP/云端识别），后续可扩展加入“兵营位置提取”与“兵力分析”模块。

🚀 快速上手

1. 编译（示例）
   - 使用你已存在的 `javac` build 任务或手动：
     ```
     javac -encoding UTF-8 -d out src\com\david\tool\*.java
     ```
2. 运行：
   ```
   java -cp out com.david.tool.Main
   ```
3. 使用方法：
   - 将地图图片拖拽到窗口中。
   - 程序会做颜色分割，列出检测到的区域（可能需要人工确认/命名）。
   - 点击 “调用 AI 识别名称” 会尝试用 tesseract OCR（若安装）识别区域上的标签。
   - 点击 “导出地图包” 会在 `output_maps` 下生成 `map_<timestamp>` 文件夹，结构示例：
     ```
     map_20260101_123456/
       images/original.png
       images/region_00.png
       masks/mask_00.png
       regions.json
     ```

🔧 扩展点（建议）

- 更好的分割：用语义分割/实例分割模型替换当前的颜色量化实现（对古地图尤其有效）。
- AI 接入：实现 `AIClient` 的 HTTP 客户端，调用云端 OCR/vision 模型（例如：OCR、图像文字检测 + GPT 帮助解析所属国家名）。
- 兵营提取：默认使用区域质心作为兵营候选位置；可进一步用城市标注识别（OCR 找到城名/城池）或基于道路/地形检测放置更合理的“兵营”锚点。
- 兵力分析模块：接入历史数据或地图上兵力密度估计模型，输出每个区域的估计兵力和热力图图层（后续可逐步加入）。

📄 注意

- 当前实现为 MVP，不保证对所有地图都能准确分割；对颜色明显区分的手绘/着色地图效果最好。
- 若想使用 tesseract OCR，请安装 tesseract 并确保 `tesseract` 可执行程序在 PATH 下，或在 `Config.TESSERACT_PATH` 中指定路径。

---

如需我把 AIClient 替换成调用某个在线模型（提供 API Key 与 endpoint），或把输出直接格式化为你 RTS 项目能立即加载的图块/资产格式（并生成兵营位置数据结构），告诉我你想要的输出格式，我会继续实现。