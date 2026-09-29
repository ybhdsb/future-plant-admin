# 两人协作说明

## 第一次拉取

```bash
git clone https://github.com/ybhdsb/aiot-project.git
cd aiot-project
```

1. 复制本地配置：
   ```bash
   cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
   ```
   按自己电脑修改 MySQL 密码、`join-base-url` 等。

2. 若需要批量部署心跳，再复制设备清单：
   ```bash
   cp scripts/device-heartbeat/devices.csv.example scripts/device-heartbeat/devices.csv
   ```

3. 准备好本地 MySQL（库名 `rail_traffic`）和 MQTT（本机 1883）后启动项目。

## 日常开发

```bash
git pull
git checkout -b feature/你的功能名
# ... 改代码 ...
git add .
git commit -m "简明说明本次改动"
git push -u origin feature/你的功能名
```

然后在 GitHub 上开 Pull Request，合并到 `main`。

## 注意

- 不要提交 `application-local.yml`、`devices.csv`（含密码）
- 不要提交 `target/` 编译产物
- 数据库数据不进 Git，表结构变更请放 `sql/` 脚本
