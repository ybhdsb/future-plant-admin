# 未来植物原型系统（独立工程）

从原 `springboot-layui-admin` 拆出的独立项目，面向未来植物原型展示与控制。

## 保留能力

- 登录 / 权限管理
- 未来植物：总览、设备控制、作物表型、历史、导出
- 设备管理（控制器在线与登记）
- 模型库、数据集库

## 数据库

默认与原项目**共用**同一 MySQL 库（`rail_traffic`），表结构不冲突：

- 鉴权：`userauth`
- 植物：`plant_*`
- 库：`model_library` / `dataset_library`
- 设备：`devices`

密码写在 `src/main/resources/application-local.yml`（勿提交）。

## 启动

```bash
cd future-plant-admin
./mvnw spring-boot:run
```

浏览器打开：http://localhost:8708/login  

（端口 **8708**，避免与原项目 8707 冲突。）

## 与原项目关系

| | 原项目 | 本项目 |
|--|--------|--------|
| 路径 | `springboot-layui-admin-master` | `future-plant-admin` |
| 定位 | 生猪 / 联邦 / 机器人等综合平台 | 未来植物独立原型 |
| 端口 | 8707 | 8708 |
| 数据库 | 可共用 | 可共用 |

原项目中的未来植物菜单可后续自行下线；开发与验收请以本仓库为准。
