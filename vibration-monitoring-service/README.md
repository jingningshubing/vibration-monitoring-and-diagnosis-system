# 振动监测后端服务

## 目录说明

- `src/`：Spring Boot 后端源码和数据库迁移脚本。
- `data/jzjc01/waveforms/`：本地波形数据。
- `simulator/vibration-simulator/`：MQTT/HTTP 波形模拟器。
- `pom.xml`：Maven 构建配置。

## 启动

1. 确保 MySQL 中已创建 `vibration_monitoring` 数据库，并执行 `src/main/resources/db/migration/` 中的建表脚本。
2. 按需在 `src/main/resources/application.yml` 中配置数据库连接。
3. 在本目录执行：

```powershell
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8081`，接口前缀为 `/api/vibration`。
