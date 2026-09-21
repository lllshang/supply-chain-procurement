# 供应链采购协同中台（dzgylxt）

单仓 Monorepo，包含后台 Web 管理端、供应商 H5、后端服务与脚本。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2 + Java 17 + MyBatis-Plus + MySQL 8 + Redis 7 |
| 鉴权 | Spring Security 6 + JWT + `AuthProvider` 适配层（预留统一认证） |
| 文件存储 | MinIO（抽象 `FileStorage` 接口） |
| API 契约 | OpenAPI 3.1（Springdoc） |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router 4 |
| H5 | Vue 3 同栈（移动端，一期仅资质协同） |
| CI/CD | GitLab CI + Docker 多阶段构建 |

## 目录结构

```
dzgylxt/
├─ backend/     Spring Boot 服务（Gradle Kotlin DSL）
├─ frontend/    后台 Web 管理端（Vue3 + Vite）
├─ h5/          供应商 H5（Vue3 + Vite，移动端）
├─ docs/        需求/技术文档
├─ scripts/     SQL 初始化、数据迁移、工具脚本
└─ scm-proxy/   本地一体化代理（nginx 等，请勿破坏）
```

## 启动方式

### 本地一体化（推荐）
```bash
docker compose up -d        # 启动 mysql / redis / minio / backend / frontend / h5
```

### 后端（backend/）
```bash
cd backend
./gradlew bootRun           # 或 gradle bootRun
# 接口文档: http://localhost:8080/swagger-ui.html
# API 前缀: /api/v1/**
```

### 前端（frontend/）
```bash
cd frontend
npm install
npm run dev                 # 开发 http://localhost:5173
npm run build               # 产物 dist/
```

### 供应商 H5（h5/）
```bash
cd h5
npm install
npm run dev                 # 开发 http://localhost:5174
npm run build               # 产物 dist/
```

## 阶段说明

- **阶段一（T01–T05）**：框架骨架，可启动、可登录、RBAC 权限菜单、前后端契约打通。
- **阶段二（T06+）**：业务模块按采购主链路逐个落地。

> 账号：`admin / admin123`（种子数据，BCrypt 加密，首次登录请修改）。
