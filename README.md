# 自动化测试平台

一个功能完整的自动化测试平台，采用前后端分离架构，支持测试用例管理、测试执行、报告生成等功能。

## 技术栈

### 后端
- **框架**: Flask 3.0
- **ORM**: SQLAlchemy 2.0
- **数据库**: SQLite (支持扩展到 MySQL/PostgreSQL)
- **认证**: Flask-Login
- **跨域**: Flask-CORS
- **调度**: APScheduler
- **API文档**: Swagger (计划中)

### 前端
- **框架**: Vue 3.4
- **构建工具**: Vite 5.0
- **路由**: Vue Router 4.2
- **HTTP客户端**: Axios 1.6
- **UI框架**: Bootstrap 5.3
- **图标**: Bootstrap Icons 1.11
- **图表**: Chart.js 4.4

## 项目结构

```
automation-test-platform/
├── backend/              # 后端代码
│   ├── app/             # Flask应用主目录
│   │   ├── models/      # 数据模型
│   │   ├── routes/      # 路由定义
│   │   ├── schemas/     # 数据验证模式
│   │   ├── services/    # 业务逻辑服务
│   │   ├── utils/       # 工具函数
│   │   └── __init__.py  # 应用初始化
│   ├── migrations/      # 数据库迁移文件
│   ├── static/         # 静态资源（已废弃，前端使用独立的静态资源）
│   ├── templates/       # HTML模板（已废弃，前端使用Vue组件）
│   ├── run_web.py       # 后端启动脚本
│   ├── requirements_web.txt # 后端依赖
│   └── scheduler.py     # 任务调度器
├── frontend/             # 前端代码
│   ├── src/             # 前端源码
│   │   ├── components/  # Vue组件
│   │   ├── views/       # 页面组件
│   │   ├── router/      # 路由配置
│   │   ├── utils/       # 工具函数
│   │   ├── main.js       # 应用入口
│   │   └── App.vue       # 根组件
│   ├── index.html       # HTML入口
│   ├── vite.config.js   # Vite配置
│   └── package.json     # 前端依赖
└── README.md            # 项目说明文档
```

## 后端安装和运行

### 1. 安装依赖

```bash
cd backend
pip install -r requirements_web.txt
```

### 2. 初始化数据库

```bash
# 初始化迁移环境（首次运行）
python -m flask db init

# 创建迁移脚本
python -m flask db migrate -m "Initial migration"

# 应用迁移
python -m flask db upgrade
```

### 3. 启动后端服务

```bash
python run_web.py
```

后端服务将在 http://localhost:5000 启动

## 前端安装和运行

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

前端服务将在 http://localhost:3000 启动

### 3. 构建生产版本

```bash
npm run build
```

构建后的文件将输出到 `dist` 目录

## API文档

API文档将在后续版本中通过 Swagger 提供，目前可通过查看路由文件了解可用接口：

- `backend/app/routes/main_routes.py` - 主路由（登录、登出、仪表盘）
- `backend/app/routes/test_case_routes.py` - 测试用例管理
- `backend/app/routes/test_execution_routes.py` - 测试执行
- `backend/app/routes/report_routes.py` - 报告生成

## 开发指南

### 后端开发

1. **添加新模型**: 在 `backend/app/models/` 目录下创建新的模型类
2. **添加新路由**: 在 `backend/app/routes/` 目录下创建新的路由文件，并在 `backend/app/__init__.py` 中注册蓝图
3. **添加新服务**: 在 `backend/app/services/` 目录下创建新的服务类，封装业务逻辑
4. **数据库迁移**: 每次修改模型后，在 `backend` 目录下运行迁移命令更新数据库

### 前端开发

1. **添加新页面**: 在 `src/views/` 目录下创建新的页面组件
2. **配置路由**: 在 `src/router/index.js` 中添加新的路由配置
3. **添加API调用**: 使用 axios 调用后端API，建议封装到专门的 API 服务中
4. **组件开发**: 通用组件放在 `src/components/` 目录下，页面组件放在 `src/views/` 目录下

## 部署指南

### 后端部署

1. 使用 Gunicorn 或 uWSGI 作为 WSGI 服务器
2. 配置 Nginx 作为反向代理
3. 设置环境变量管理配置
4. 配置数据库连接

### 前端部署

1. 构建生产版本
2. 将 `dist` 目录部署到 Nginx 或其他静态文件服务器
3. 配置 Nginx 反向代理 API 请求到后端

## 功能特性

- ✅ 测试用例管理
- ✅ 测试执行引擎
- ✅ 测试报告生成
- ✅ 测试套件管理
- ✅ 测试计划管理
- ✅ 环境管理
- ✅ 用户认证和授权
- ✅ 任务调度
- ✅ 仪表盘统计
- ✅ 前后端分离架构

## 开发计划

1. 添加 API 文档（Swagger）
2. 实现更完善的权限管理
3. 支持更多测试类型
4. 增强测试报告功能
5. 添加测试用例版本管理
6. 实现测试用例导入导出功能
7. 添加测试执行监控

## 联系方式

如有问题或建议，请联系项目维护人员。

## 许可证

MIT License
