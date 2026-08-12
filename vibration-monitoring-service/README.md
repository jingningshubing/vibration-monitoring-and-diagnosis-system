# 振动监测后端服务

本模块是“加注供气关键点位动设备-管线无人值守监测系统”的 Spring Boot 后端，负责振动数据回放导入、特征计算、阈值预警、波形查询及 ONNX 辅助诊断。

项目整体说明、前端启动方式和功能范围请参阅仓库根目录的 [README](../README.md)。

## 技术栈

- Java 17、Spring Boot 3、Spring JDBC、Maven
- MySQL 8
- ONNX Runtime

## 配置

服务默认端口为 `8081`，接口前缀为 `/api/vibration`。数据库连接通过环境变量提供，请勿将真实密码写入并提交到配置文件。

```powershell
$env:VIBRATION_DB_URL="jdbc:mysql://localhost:3306/vibration_monitoring?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:VIBRATION_DB_USERNAME="root"
$env:VIBRATION_DB_PASSWORD="你的数据库密码"
```

启动前需要在 MySQL 中创建 `vibration_monitoring` 数据库并初始化项目所需表结构。

## 启动

在当前目录执行：

```powershell
mvn spring-boot:run
```

## 目录说明

```text
src/main/java/                 # REST API、定时任务、业务服务
src/main/resources/models/     # ONNX 轴承诊断模型
ml/                            # 训练、评估和 ONNX 导出脚本
scripts/                       # CSV 演示数据回放脚本
```

## 数据与诊断

- 定时任务以 CSV 回放模拟周期采集；原始三轴波形压缩为 JSON GZIP，批次索引、特征、告警和设备状态存入 MySQL。
- 阈值规则负责振动状态分级；ONNX 模型用于按需输出健康、轴承内圈故障或轴承外圈故障的辅助诊断结果。
- 原始数据集、运行时波形、Python 依赖和训练产物均未提交到仓库；训练依赖清单见 `ml/requirements.txt`。
