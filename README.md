# 加注供气关键点位动设备-管线无人值守监测系统

面向加注供气关键点位动设备与管线的振动监测子系统原型。围绕“振动采集 - 数据预处理 - 异常监测 - 智能诊断 - 可视化与追溯”的业务链路，系统对三轴振动数据进行特征计算和阈值预警，提供波形、速度波形与 FFT 频谱查询，并集成 ONNX 模型实现健康、轴承内圈故障和轴承外圈故障的辅助诊断。

> 本项目用于学习与作品展示。仓库不包含原始振动数据集、运行时波形文件和本地数据库密码。

## 项目范围

该仓库聚焦无人值守监测系统中的**动设备-管线振动监测与辅助诊断**能力，覆盖设备/测点概览、振动数据处理、异常告警、历史查询、诊断结果管理和统计分析。数据接入阶段以 CSV 回放模拟周期采集，便于本地演示与算法验证；现场无线传感器、网关接入及报告导出等工程化能力不在当前实现范围内。

## 已实现功能

- **设备与测点概览**：结合流程图展示设备、测点位置、在线状态和三轴速度有效值。
- **周期采集与预处理**：定时回放 CSV 三轴振动样本，计算加速度 RMS、速度 RMS、总振值等特征，并完成入库与索引。
- **异常振动监测**：依据预警、报警、危险三级阈值生成告警记录，支持按设备、时间和告警等级筛选与追溯。
- **波形与频谱分析**：查询三轴原始波形、速度波形及 FFT 频谱，支持历史批次查看。
- **智能诊断**：基于 ONNX Runtime 加载轴承分类模型，输出故障类别、置信度、稳定性和维护建议。
- **统计分析**：展示设备/测点状态分布、告警与诊断时间趋势、设备排行和历史记录。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Element Plus、ECharts、Axios、Pinia |
| 后端 | Java 17、Spring Boot 3、Spring JDBC、Maven |
| 数据与模型 | MySQL、JSON GZIP、ONNX Runtime、Python（模型训练） |

## 架构与数据流

```text
CSV 回放三轴振动数据
      │
      ├─ 特征计算（RMS、速度 RMS、总振值） ──> MySQL（批次、特征、告警、状态）
      │
      ├─ 原始波形压缩 ─────────────────────> JSON GZIP 文件
      │
      └─ 按需诊断 ─────────────────────────> ONNX Runtime ──> 诊断结论
                                                        │
Vue 3 + ECharts <──────────────────── Spring Boot REST API
```

原始三轴波形采用 JSON GZIP 保存，MySQL 保存索引、特征和业务数据，避免大体积时序波形直接入库带来的维护压力。后端以定时任务模拟周期采集，并将诊断能力与预警规则解耦：阈值负责实时状态分级，ONNX 诊断结果用于辅助研判与维护建议。

## 项目结构

```text
.
├── vibration-monitoring-web/       # Vue 3 前端
├── vibration-monitoring-service/   # Spring Boot 后端
│   ├── src/main/resources/models/   # ONNX 诊断模型
│   ├── ml/                          # 模型训练与导出脚本
│   └── scripts/                     # 演示数据脚本
└── README.md
```

## 本地运行

### 1. 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 16+
- pnpm 或 npm
- MySQL 8+

### 2. 配置数据库

创建 `vibration_monitoring` 数据库，并根据本地实际情况初始化所需表结构。数据库连接通过环境变量配置；不要把真实密码写进配置文件或提交到仓库。

```powershell
$env:VIBRATION_DB_URL="jdbc:mysql://localhost:3306/vibration_monitoring?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:VIBRATION_DB_USERNAME="root"
$env:VIBRATION_DB_PASSWORD="你的数据库密码"
```

### 3. 启动后端

```powershell
cd vibration-monitoring-service
mvn spring-boot:run
```

后端默认地址为 `http://localhost:8081`，接口前缀为 `/api/vibration`。

### 4. 启动前端

```powershell
cd vibration-monitoring-web
pnpm install
pnpm dev
```

## 数据说明

- 原始数据集、依赖目录、构建产物与运行时生成波形均通过 `.gitignore` 排除。
- 如需训练或替换模型，可参考 `vibration-monitoring-service/ml/` 内的训练和 ONNX 导出脚本。
- 数据集缺失时，系统可用于阅读源码和前端界面开发；完整数据导入需自行准备合法来源的振动样本及数据库表结构。
