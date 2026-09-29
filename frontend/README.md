# Future Plant Frontend（商业级 Vue 3）

植物主链路 SPA：登录、壳、总览、控制、表型、历史与数据。

## 开发

需 Node 18+（推荐 20）。后端默认 `http://localhost:8708`。

```bash
export PATH="$HOME/.local/node-v20.18.0-darwin-x64/bin:$PATH"  # 若本机已装本地 Node20
cd frontend
npm install
npm run dev
```

浏览器打开 Vite 提示的地址（默认 `http://localhost:5173/app/`）。

## 生产构建

```bash
cd frontend
npm run build
```

产物输出到 `src/main/resources/static/app/`，由 Spring 以 `/app/**` 提供。

## 入口

| 地址 | 说明 |
|------|------|
| `/` `/index` `/login` | 重定向到 Vue SPA |
| `/app/` `/app/login` | 新前端 |
| `/index/legacy` `/login/legacy` | 旧 Layui 页面 |
